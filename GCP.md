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
O deadline existente de 5 s cobre o conjunto das tentativas de cada RPC.
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
Esse teste e local; o roteiro de falha no Cloud Run sera preparado separadamente.

## Ainda necessario antes da entrega

- Validar a conexao real com Cloud SQL.
- Adicionar scripts de schema/migracoes versionados e executar no banco.
- Implantar os cinco servicos Cloud Run com as novas imagens.
- Configurar variaveis/segredos e conferir no console.
- Testar persistencia e resiliência pelas URLs HTTPS publicas.
- Confirmar com o grupo a VM antiga e comprovar que esta desligada/excluida.
- Relatorio PDF de ate 5 paginas com arquitetura e URLs reais.

Referencias:
- https://docs.cloud.google.com/sql/docs/postgres/connect-run
- https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory
- https://grpc.io/docs/guides/retry/
