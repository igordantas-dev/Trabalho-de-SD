# Banco de dados: baseline SQL versionada

O esquema corresponde as entidades JPA atuais e ao PostgreSQL 16.
Nao ha dados de exemplo nas migracoes de producao.

| Arquivo | Finalidade |
|---|---|
| migrations/V001__schema_inicial.sql | Cria as quatro tabelas ausentes, sem substituir as existentes |
| migrations/V002__senha_aluno.sql | Acrescenta senha_hash nullable; cadastros antigos continuam preservados |
| Validate.sql | Confere colunas, tipos, tamanhos, nulabilidade, PKs, unicidade e FK |
| Validate.psql | Executa somente validacao/contagens em transacao read-only |
| Apply.psql | Aplica V001 e V002, valida e registra as duas versoes atomicamente |
| tests/ | Fixtures usadas somente no banco descartavel dos testes locais |

## Modelo

- alunos: cadastro, CPF e email unicos; senha_hash protegido com PBKDF2.
  Hash nulo identifica cadastro antigo sem senha definida; nao ha senha padrao.
- fichas_treino: ficha de treino; aluno_id e uma referencia logica em varchar.
- itens_treino: exercicios com FK para fichas_treino.
- mensalidades: cobrancas; aluno_id e uma referencia logica em varchar.
- schema_migrations: versao da baseline e data de aplicacao.

IDs UUID, status e demais valores iniciais sao gerados pela aplicacao Java.
Os scripts mantem double precision nos valores/cargas e timestamp sem fuso nas
datas/horarios, como nas entidades existentes. Nao mudam esses tipos de negocio.

## Teste local isolado

Mantenha Docker Desktop ativo e execute na raiz:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-Schema.ps1
```

O teste usa um PostgreSQL 16 descartavel, com nome aleatorio, sem portas publicadas
e sem montar o volume do banco da aplicacao. Cria o schema, insere fixtures,
reaplica a baseline, verifica preservacao e rejeicao de schema divergente.
O conteiner e seu volume temporario sao removidos ao final.

## Cloud SQL existente

Depois de enviar os arquivos ao GitHub e atualizar o Cloud Shell, aplique
V002 **antes** de publicar o aluno-service atualizado. O banco atual ainda
nao possui senha_hash; executar apenas validate nessa situacao deve falhar.

```bash
python3 scripts/CloudSql-Schema.py apply
python3 scripts/CloudSql-Schema.py validate
```

Confira BASELINE_DO_BANCO_CONCLUIDA, versoes 001 e 002, e depois
VALIDACAO_DO_BANCO_CONCLUIDA. A migracao apenas acrescenta a coluna e o registro
de versao; nao redefine senhas nem remove alunos, fichas ou mensalidades.
Os alunos anteriores recebem senha pelo botao Definir senha na gestao.

O script busca a senha do Secret Manager sem exibi-la e inicia seu proprio
cloud-sql-proxy em uma porta dinamica de 127.0.0.1. O cliente psql recebe a
senha por PGPASSWORD e usa -w, que proibe prompts de senha.
Ele requer gcloud, cloud-sql-proxy e psql, presentes no Cloud Shell usado no
projeto, e as permissoes de acesso a instancia/secret.
O proxy iniciado pelo script e encerrado ao final, inclusive em caso de erro.
Nao e necessario instalar pacotes Python adicionais.

## Garantias e limites

- As migracoes nao executam DROP TABLE, TRUNCATE, DELETE ou UPDATE de registros da aplicacao.
  A limpeza manual descrita abaixo e separada das migracoes.
- CREATE TABLE IF NOT EXISTS nao corrige tabelas divergentes; Validate.sql
  detecta as incompatibilidades cobertas e interrompe a transacao.
- Os registros 001 e 002 so sao criados apos a validacao passar.
- PKs, unicidade e a FK sao comparadas por colunas, sem exigir nomes de
  constraints gerados pelo Hibernate.
- Reaplicar nao duplica dados nem o registro da versao.
- O lock e os timeouts sao locais a transacao.
- Este e um fluxo SQL manual, nao uma instalacao de Flyway/Liquibase.
- Depois de publicadas, mantenha V001 e V002 imutaveis; alteracoes futuras devem ser
  novas migracoes V003, V004 etc., com suas respectivas validacoes e registro.
- Depois da baseline validada, configure DB_SCHEMA_MODE=validate nos tres
  microsservicos para validar o mapeamento sem Hibernate alterar o schema.
- Nao execute os arquivos de tests/ em bancos da aplicacao.

## Criar um banco do zero

Crie a base academia e o usuario por meio do provedor/administrador. Conectado
como o usuario da aplicacao, com permissao CREATE no schema public, execute:

```bash
psql -X -v ON_ERROR_STOP=1 -d academia -f db/Apply.psql
```

Use variaveis/credenciais externas ao repositorio para host, usuario e senha.

## Zerar somente os dados do GCP

Use apenas quando quiser apagar todos os alunos, fichas, exercicios e
mensalidades da nuvem. Essa manutencao nao altera o banco local, as tabelas,
as migracoes registradas, a instancia Cloud SQL nem os secrets da gestao.
As contas e senhas dos alunos sao apagadas junto com seus cadastros.

No Cloud Shell, com a develop atualizada:

```bash
python3 scripts/CloudSql-Schema.py reset-data
```

O script conecta exclusivamente a sd2026:us-central1:gymflow-db, banco academia,
usuario gymflow_app, usando um proxy temporario proprio. Exibe contagens antes
e depois da limpeza e DADOS_DO_GCP_ZERADOS ao concluir. A operacao ocorre em
transacao, sem CASCADE; uma dependencia inesperada impede a limpeza.
Nao faz parte de apply, validate, build ou deploy. O arquivo SQL de manutencao
nao deve ser incluido nas migracoes nem executado no banco local.

Depois, cadastre manualmente os alunos desejados para a apresentacao.
Test-Gymflow.ps1 volta a criar registros de teste se for executado novamente.
