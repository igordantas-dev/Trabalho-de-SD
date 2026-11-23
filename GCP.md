# Preparacao da entrega final no GCP

Projeto: sd2026. Regiao: us-central1.
Cloud SQL: sd2026:us-central1:gymflow-db. Banco: academia.
Usuario da aplicacao: gymflow_app. Senha: secret gymflow-db-password.
Nunca copie a senha para o repositorio.

## Conexao dos microsservicos

Os tres microsservicos incluem o Cloud SQL Java Connector.
Sem INSTANCE_CONNECTION_NAME, continuam usando DB_URL para PostgreSQL local.
Com INSTANCE_CONNECTION_NAME, constroem a URL JDBC para o conector e usam:

| Variavel | Valor no Cloud Run |
|---|---|
| INSTANCE_CONNECTION_NAME | sd2026:us-central1:gymflow-db |
| DB_NAME | academia |
| DB_USER | gymflow_app |
| DB_PASSWORD | Referencia ao secret gymflow-db-password, versao 1 |
| DB_POOL_SIZE | 3 |
| DB_SCHEMA_MODE | update, enquanto nao forem aplicadas migracoes |
| PORT | Fornecida pelo Cloud Run |

O conector usa IP publico da instancia, credenciais da conta de servico do
Cloud Run e refresh lazy. Nao requer liberar 0.0.0.0/0 no banco nem gravar
chaves JSON no conteiner. A conta de servico dos microsservicos precisa de
roles/cloudsql.client e acesso ao secret especifico.
O conector protege/autentica o transporte; DB_USER/DB_PASSWORD autenticam o
usuario dentro do PostgreSQL.
Antes da publicacao final, conferir criacao do schema e permissao CREATE
do usuario no schema public. A conexao real ainda precisa ser validada no GCP.

## Resiliencia

GrpcRetryPolicy aplica retry somente a:
- BuscarAluno e ListarAlunos;
- BuscarFichaTreino e ListarFichasPorAluno;
- BuscarMensalidade e ListarMensalidadesPorAluno.

Sao no maximo 3 tentativas, incluindo a primeira, apenas em UNAVAILABLE.
O intervalo parte de 200 ms, dobra e tem teto de 1 s, com jitter do gRPC.
O deadline cobre o conjunto das tentativas de cada RPC. O padrao local e 5 s.
No Gateway do Cloud Run, configure GRPC_DEADLINE_SECONDS=20 para acomodar
a inicializacao a frio dos microsservicos, sem manter instancias ociosas.
O timeout HTTP do frontend e do Cloud Run permanece em 60 s.
DEADLINE_EXCEEDED e erros de negocio nao acionam novas tentativas.
Nao ha politica de retry para criacao, pagamento ou atualizacao.
O gRPC pode repetir transparentemente transportes que nao chegaram a executar
a operacao no servidor, conforme as garantias da biblioteca.

O Gateway retorna HTTP 503 para UNAVAILABLE e 504 para DEADLINE_EXCEEDED.
GrpcAttemptLogging registra grpc_call, method, attempt, transparent_retry e
status em stdout, coletavel no Cloud Logging. Nenhum corpo de requisicao,
token JWT ou senha e registrado por esse interceptor.

### Teste automatizado da politica, sem Docker/GCP

O teste inicia um servidor gRPC real em porta temporaria e verifica recuperacao
na terceira tentativa, limite de tentativas, ausencia de retry em NOT_FOUND e
escritas, e limite de tempo em chamada lenta. Na raiz do projeto:

PowerShell:

```powershell
.\mvnw.cmd -pl api-gateway test-compile exec:java "-Dexec.mainClass=academia.gateway.grpc.GrpcRetryIntegrationCheck" "-Dexec.classpathScope=test"
```

Cloud Shell (depois do build/install dos contratos):

```bash
./mvnw -pl api-gateway test-compile exec:java -Dexec.mainClass=academia.gateway.grpc.GrpcRetryIntegrationCheck -Dexec.classpathScope=test
```

### Verificar deadline configuravel

O teste abaixo inicia um servidor gRPC com resposta de 6 s e usa o controller
real com configuracao Spring: o padrao de 5 s expira, mas a variavel de ambiente
GRPC_DEADLINE_SECONDS=20 permite concluir a consulta.

```powershell
.\mvnw.cmd -pl api-gateway test-compile exec:java "-Dexec.mainClass=academia.gateway.grpc.ColdStartDeadlineCheck" "-Dexec.classpathScope=test"
```

### Demonstracao local de falha e recuperacao

Recrie os conteineres, aguarde inicializarem e execute:

```powershell
docker compose up -d --build
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-Gymflow.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-Resiliencia.ps1
```

O segundo teste para apenas financeiro-service, confere o erro tratado,
consulta alunos durante a falha, mostra os logs e restaura o financeiro.
O bloco finally tenta restaurar o servico mesmo se o teste falhar.
Esse teste controla apenas Docker local. Para o Cloud Run, use o roteiro abaixo.

### Demonstracao no Cloud Run e Cloud Logging

Execute no Cloud Shell, no clone atualizado:

```bash
python3 scripts/Test-Resiliencia-Gcp.py
```

O script usa somente consultas HTTPS e os servicos ja implantados. Ele:

1. Confirma que alunos e financeiro estao acessiveis pelo frontend.
2. Salva o endereco original do financeiro em ~/gymflow-financeiro-restore.json.
3. Configura temporariamente FINANCEIRO_HOST=127.0.0.1 e FINANCEIRO_PORT=1
   no Gateway. Isso simula perda de conectividade com uma dependencia,
   e nao o desligamento do processo do microsservico.
4. Exige HTTP 503 tratado no financeiro e HTTP 200 na consulta de alunos.
5. Consulta Cloud Logging na revisao de falha e exige tentativas 1, 2 e 3
   associadas ao mesmo grpc_call; imprime as linhas e o filtro do Logs Explorer.
6. Restaura o endereco original em um bloco finally e confirma HTTP 200.

As alteracoes de variaveis criam revisoes do Gateway; a imagem, os segredos,
os demais endpoints e os dados do banco sao preservados. Cada update pode
levar alguns minutos. Nao faca outra implantacao do Gateway durante o teste.

Se a sessao for interrompida e a restauracao ficar pendente, reconecte ao
Cloud Shell, entre na pasta do projeto e execute:

```bash
python3 scripts/Test-Resiliencia-Gcp.py --restore
```

O arquivo de emergencia contem apenas host, porta e identificacao do servico,
sem tokens ou senhas. Ele so e removido apos a restauracao concluir.
Os testes offline verificam o fluxo, restauracao em caso de falha, preservacao
do arquivo quando a restauracao falha e agrupamento correto dos logs:

```bash
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Para mostrar a evidencia no console:
https://console.cloud.google.com/logs/query?project=sd2026
Cole o filtro impresso pelo script e mostre as tres tentativas.

## Ainda necessario antes da entrega

Para publicar somente o Gateway com o prazo ajustado, envie os commits para
develop, atualize o clone do Cloud Shell e execute:

```bash
bash scripts/Update-Gateway.sh
```

O script publica uma imagem com a tag do commit e atualiza somente a imagem e
GRPC_DEADLINE_SECONDS=20 no servico existente, preservando hosts e segredos.
Nao habilita instancias minimas permanentemente.

- Conexao com Cloud SQL e fluxo HTTPS validados pelo teste de cadastro,
  consulta, treino, mensalidade e pagamento em 23/11/2026.
- Scripts de schema/baseline em db/: consulte db/README.md para testar,
  validar o banco existente e registrar a versao 001.
- Cinco servicos implantados: alunos, treinos, financeiro, Gateway e frontend.
- Configurar variaveis/segredos e conferir no console.
- Demonstracao de resiliência no Cloud Run concluida; guardar os prints
  do Cloud Logging com as tres tentativas.
- Grupo confirmou exclusao das VMs antigas; guardar os prints de cada projeto.
- Relatorio PDF de ate 5 paginas com arquitetura e URLs reais.

Referencias:
- https://docs.cloud.google.com/sql/docs/postgres/connect-run
- https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory
- https://grpc.io/docs/guides/retry/
