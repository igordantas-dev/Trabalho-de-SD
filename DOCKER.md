# Execucao local com Docker

Esta etapa executa frontend Nginx, Gateway, tres microsservicos
e PostgreSQL em seis conteineres. Os Dockerfiles Java usam compilacao multi-stage,
Java 21 e usuario sem privilegios no runtime. O frontend usa Nginx sem root.
O contexto de build e a raiz
do repositorio, pois todos os modulos dependem dos contratos gRPC.

## Iniciar no PowerShell

Encerre com Ctrl+C os servicos Java iniciados manualmente.
O Compose reutiliza o volume gymflow-postgres-data criado nos testes locais.
Nunca execute dois PostgreSQL simultaneamente sobre esse mesmo volume.

Confira o volume existente e pare o conteiner antigo antes de iniciar o Compose:

```powershell
cd C:\workspace\Trabalho-de-SD
python .\scripts\Setup-LocalAuth.py
docker volume inspect gymflow-postgres-data
docker stop gymflow-postgres
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=60
```

Em uma instalacao nova, sem banco anterior, crie o volume antes:
`docker volume create gymflow-postgres-data`.
O configurador exige Python 3 e deve ser executado somente na primeira configuracao.
Ele solicita e-mail/senha da gestao e grava .env.auth, ignorado pelo Git e pelo build.
Nao envie esse arquivo a outras pessoas. Se ja existir, nao o recrie para iniciar
os conteineres. O env_file raw exige Docker Compose 2.30 ou superior.
As credenciais deste Compose sao somente para desenvolvimento local.
Em um volume existente, as variaveis POSTGRES_* nao alteram usuarios ou senhas.

Acesse o frontend em http://localhost:8081. A API e o Swagger continuam em
http://localhost:8080/swagger-ui.html. O Gateway deixou de servir as telas;
os arquivos estaticos estao em frontend/public. O frontend encaminha /api/
para o Gateway, evitando configuracao de URLs no JavaScript e CORS no navegador.
Na primeira inicializacao, aguarde os servicos
gRPC terminarem de iniciar antes de executar os testes da API.
As portas gRPC ficam acessiveis apenas na rede do Compose.
Para acompanhar a inicializacao: `docker compose logs -f`.
Ctrl+C encerra apenas o acompanhamento dos logs quando iniciado com -d.

## Testar o fluxo completo

O script cria dados ficticios novos a cada execucao e registra um pagamento
somente no banco, sem efetuar qualquer transferencia financeira:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Test-Gymflow.ps1
```

A opcao Bypass vale somente para esse processo. O teste percorre frontend,
Gateway e os tres microsservicos. Os dados ficam disponiveis no navegador.
Para outro endereco: acrescente -BaseUrl https://URL-DO-FRONTEND.

## Configuracoes preparadas para Cloud Run

- Todos os processos de aplicacao escutam a variavel PORT. Sem ela, o Gateway
  usa 8080 e os servicos gRPC usam 9091, 9092 e 9093, como antes.
- No frontend, configure GATEWAY_URL com a origem do Gateway (https://...),
  sem barra final ou caminho. Nginx valida o certificado TLS do destino.
- No Gateway, configure GRPC_TLS=true, ALUNO_PORT=443, TREINO_PORT=443 e
  FINANCEIRO_PORT=443. ALUNO_HOST, TREINO_HOST e FINANCEIRO_HOST devem conter
  apenas os respectivos hostnames do Cloud Run, sem https:// ou caminho.
- Os servidores gRPC continuam recebendo h2c dentro do conteiner; configure
  HTTP/2 no Cloud Run. A plataforma termina o TLS dos endpoints publicos.
- O modo local continua usando gRPC sem TLS na rede privada do Compose.
- Na nuvem, GRPC_IAM=true faz o Gateway enviar ID tokens da sua conta de servico.
  Os microsservicos exigem roles/run.invoker para essa identidade. Localmente,
  GRPC_IAM permanece false, usando a rede privada do Compose. Consulte AUTH.md.
- Referencias: https://docs.cloud.google.com/run/docs/triggering/grpc e
  https://docs.cloud.google.com/run/docs/configuring/http2.

## Parar e voltar para a execucao manual

```powershell
docker compose down
docker start gymflow-postgres
```

O volume externo preserva os dados. Nao remova o volume.
Nao execute o banco antigo enquanto o banco do Compose estiver ativo.

## Acessos e verificacao

Gestao: http://localhost:8081, com e-mail/senha definidos no configurador.
Aluno: http://localhost:8081/aluno.html, com e-mail do cadastro e senha definida
pela gestao. Para alunos existentes, use Definir senha na lista de alunos.

No painel, confira prescricao e carga informada. Marcar exercicios e salvar
execucao e permitido apenas no Portal do Aluno. Matricula INATIVO bloqueia
novas execucoes, mas preserva consultas ao historico e as mensalidades.

O teste Test-Gymflow.ps1 solicita credenciais de gestao. Consulte AUTH.md para
os casos de verificacao do login, altura em metros e publicacao na nuvem.
