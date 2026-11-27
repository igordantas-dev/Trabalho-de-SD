# Acesso com e-mail e senha

## Como funciona

| Portal | Credencial | Permissoes |
|---|---|---|
| Gestao | E-mail/senha configurados pelo responsavel pela implantacao | Cadastrar alunos, definir senhas, prescrever fichas, consultar cargas informadas e registrar pagamentos |
| Aluno | E-mail do cadastro e senha inicial definida pela gestao | Consultar somente seus dados, fichas e mensalidades; registrar a propria execucao quando a matricula permitir |

O login confere a senha antes de emitir um JWT com perfil e, no caso do aluno,
ID do cadastro. As permissoes e a propriedade dos registros sao verificadas no
Gateway, inclusive se alguem tentar chamar a API sem usar a interface.
O endpoint publico gerarToken foi removido e tokens da demonstracao anterior
sao rejeitados. As sessoes expiram apos 30 minutos.

As senhas usam PBKDF2-HMAC-SHA256, 600.000 iteracoes e salt aleatorio individual.
Nao existe senha padrao. O hash do administrador fica no Secret Manager na nuvem;
os hashes dos alunos ficam no Cloud SQL. O frontend nao recebe esses hashes.

A gestao define uma senha inicial no cadastro e pode redefini-la com **Definir
senha** na lista. Comunique a credencial ao aluno por um canal privado.
Cadastros antigos continuam sem acesso ate essa definicao; seus historicos
permanecem preservados. Nesta entrega nao ha autocadastro, recuperacao por e-mail
nem troca obrigatoria no primeiro acesso. Redefinir a senha altera proximos
logins; JWTs ja emitidos permanecem validos ate expirarem.

## Preparar e testar localmente

No PowerShell, com Python 3 e Docker Desktop iniciados:

```powershell
cd C:\workspace\Trabalho-de-SD
python .\scripts\Setup-LocalAuth.py
docker compose --parallel 1 build aluno-service api-gateway frontend
docker compose up -d
```

Execute o configurador uma unica vez. Informe o e-mail e a senha da gestao nos
prompts; a senha nao aparece na tela. Ele cria `.env.auth`, ignorado pelo Git e
pelo contexto do build. Nao compartilhe esse arquivo. Se ele ja existe, mantenha-o
e pule o configurador. As credenciais locais e as da nuvem sao independentes.

O Compose local usa Hibernate update para acrescentar senha_hash. O volume do
PostgreSQL existente e preservado. Na nuvem, a migracao SQL e aplicada antes do
deploy, pois os microsservicos usam validate.

1. Abra http://localhost:8081 e entre com a credencial da gestao.
2. Confira que senha incorreta impede o acesso e apresenta mensagem clara.
3. Cadastre um aluno com e-mail e senha inicial, ou use Definir senha em um antigo.
4. Abra http://localhost:8081/aluno.html e entre com essa conta do aluno.
5. Consulte suas fichas e mensalidades. Salve cargas/execucao em uma ficha propria.
6. Volte a gestao: a ficha apresenta prescricao e carga informada, sem controles
   de execucao ou alteracao do status do treino.
7. Inative a matricula e confira que novas execucoes sao impedidas, mantendo o
   historico consultavel. Reative o aluno se ele for usado na apresentacao.
8. No cadastro, informe altura 170: deve ser rejeitada. Informe 1,70 m para 170 cm.

Limites de preenchimento: senha 8 a 128 caracteres; altura opcional entre 0,50
e 2,50 metros; peso opcional entre 1 e 500 kg; cargas entre 0 e 1.000 kg.
A validacao acontece na interface e na API. Nao convertemos 170 automaticamente
para 1,70, para evitar alterar silenciosamente o dado informado.

Teste o fluxo integrado e a migracao isolada:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-Gymflow.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-Schema.ps1
```

O primeiro solicita a credencial da gestao e cria novos registros ficticios;
o segundo usa banco descartavel, sem alterar o volume da aplicacao.

## Publicar a atualizacao de login no GCP

Faca o teste local primeiro. Envie os arquivos atualizados da develop ao GitHub.
No Cloud Shell, utilize a sequencia abaixo. Ela pressupoe os cinco servicos,
Cloud SQL, secrets de banco/JWT e contas de servico ja existentes no sd2026.

### 1. Atualizar o codigo e preparar a credencial

```bash
cd ~/Trabalho-de-SD
git switch develop
git pull --ff-only origin develop
python3 scripts/Deploy-LoginGcp.py prepare
```

Informe e-mail e senha da gestao nos prompts. O script cria uma versao do secret
`gymflow-admin-password-hash` e concede acesso a conta de servico do Gateway.
A senha e o hash nao sao exibidos. O arquivo ~/gymflow-auth-deploy.json guarda
somente e-mail, numero da versao e identidade do Gateway para a proxima etapa.
Nao envie senhas pelo chat nem as escreva no comando ou no repositorio.

### 2. Aplicar a migracao no Cloud SQL

```bash
python3 scripts/CloudSql-Schema.py apply
python3 scripts/CloudSql-Schema.py validate
```

Exija BASELINE_DO_BANCO_CONCLUIDA, versoes 001/002 e
VALIDACAO_DO_BANCO_CONCLUIDA. V002 acrescenta senha_hash nullable; nao define
senhas automaticamente nem remove registros existentes. Se esta etapa falhar,
corrija antes de publicar as novas imagens.

### 3. Construir as tres imagens

```bash
TAG="$(git rev-parse --short HEAD)"
gcloud builds submit . --project=sd2026 --region=us-central1 \
  --config=cloudbuild/frontend-gateway.json --ignore-file=.dockerignore \
  --substitutions="_TAG=$TAG"
```

Aguarde STATUS: SUCCESS. Este arquivo constroi aluno-service, api-gateway e
frontend. Nao depende do Docker do Cloud Shell para enviar as imagens.

### 4. Implantar e validar

```bash
python3 scripts/Deploy-LoginGcp.py deploy --tag "$TAG"
```

O script atualiza aluno-service, Gateway e frontend, mantendo as configuracoes
existentes e acrescentando ADMIN_LOGIN, ADMIN_PASSWORD_HASH e GRPC_IAM=true.
Solicita a senha da gestao para verificar login e consulta de alunos pela URL
publica. Entao remove invocacao anonima dos tres microsservicos, mantendo
roles/run.invoker para a identidade do Gateway, e verifica novamente alunos.
Por fim configura minimo 0 e maximo 2 nos cinco servicos.

JWT autentica o usuario perante o Gateway; o ID token IAM autentica a conta do
Gateway perante os microsservicos. Sao credenciais diferentes. Isso impede que
uma chamada gRPC direta contorne as permissoes dos usuarios. Os URLs HTTPS dos
microsservicos continuam existindo, mas exigem IAM. Frontend e Gateway seguem
publicos na plataforma, com login exigido na API de negocio.

A verificacao do script cobre login administrativo e consulta de alunos. Depois,
teste o fluxo completo pela URL publicada, incluindo treinos e financeiro:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-Gymflow.ps1 -BaseUrl "https://gymflow-frontend-qywzrohxza-uc.a.run.app"
```

Atualize o navegador com Ctrl+F5, entre na gestao e defina senhas dos alunos
existentes. Verifique tambem o login do aluno e o teste de resiliencia no GCP.
A publicacao pode levar minutos e nao e atomica entre os tres servicos: durante
a troca podem ocorrer falhas transitorias. Se o script parar, confira a etapa e
os logs; nao exclua o banco nem os secrets para tentar corrigir.

## Escala e apresentacao

No Cloud Shell:

```bash
python3 scripts/Scaling-Gcp.py status
python3 scripts/Scaling-Gcp.py economia
# No dia da apresentacao, com antecedencia:
python3 scripts/Scaling-Gcp.py apresentacao
# Depois da apresentacao, durante a correcao:
python3 scripts/Scaling-Gcp.py economia
```

Status apenas consulta. Economia configura minimo 0/maximo 2; apresentacao
configura minimo 2/maximo 2 por servico. Os limites das revisoes tambem sao
ajustados para que um maximo antigo de 1 nao impeça manter duas instancias.
O modo apresentacao mantem instancias ociosas cobradas; nao o deixe ligado ate
a entrega. Minimo zero permite cold start e nao para a cobranca do Cloud SQL.

## Erros apresentados ao usuario

Senha incorreta, sessao expirada, falta de permissao e campos invalidos recebem
mensagens especificas. Indisponibilidade, demora e falha inesperada recebem
orientacao amigavel para tentar novamente. Detalhes tecnicos de excecoes nao
sao enviados na resposta de erro interno. Nenhuma implementacao garante cobrir
todo bug futuro; confirme os cenarios acima e consulte Cloud Logging se algo
falhar. Nao exibimos "estamos trabalhando nisso", pois isso sugeriria um
acompanhamento automatico que o projeto nao possui.
