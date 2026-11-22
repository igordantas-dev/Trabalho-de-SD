# Execucao local com Docker

Esta etapa executa o Gateway (incluindo o frontend atual), tres microsservicos
e PostgreSQL em conteineres. Os Dockerfiles usam compilacao multi-stage,
Java 21 e usuario sem privilegios no runtime. O contexto de build e a raiz
do repositorio, pois todos os modulos dependem dos contratos gRPC.

## Iniciar no PowerShell

Encerre com Ctrl+C os servicos Java iniciados manualmente.
O Compose reutiliza o volume gymflow-postgres-data criado nos testes locais.
Nunca execute dois PostgreSQL simultaneamente sobre esse mesmo volume.

Confira o volume existente e pare o conteiner antigo antes de iniciar o Compose:

```powershell
cd C:\workspace\Trabalho-de-SD
docker volume inspect gymflow-postgres-data
docker stop gymflow-postgres
docker compose config --quiet
docker compose up -d --build
docker compose ps
docker compose logs --tail=60
```

Em uma instalacao nova, sem banco anterior, crie o volume antes:
`docker volume create gymflow-postgres-data`.
As credenciais deste Compose sao somente para desenvolvimento local.
Em um volume existente, as variaveis POSTGRES_* nao alteram usuarios ou senhas.

Acesse http://localhost:8080. Na primeira inicializacao, aguarde os servicos
gRPC terminarem de iniciar antes de executar os testes da API.
As portas gRPC ficam acessiveis apenas na rede do Compose.
Para acompanhar a inicializacao: `docker compose logs -f`.
Ctrl+C encerra apenas o acompanhamento dos logs quando iniciado com -d.

## Parar e voltar para a execucao manual

```powershell
docker compose down
docker start gymflow-postgres
```

O volume externo preserva os dados. Nao remova o volume.
Nao execute o banco antigo enquanto o banco do Compose estiver ativo.

## Proximas etapas da entrega

Esta configuracao valida a containerizacao local, ainda nao a implantacao
final. Faltam separar o frontend, adequar portas e TLS dos clientes gRPC
ao Cloud Run, configurar Cloud SQL, implementar retries e preparar a
publicacao no Artifact Registry. Nenhuma configuracao GCP e criada aqui.
