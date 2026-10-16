# Sistema Distribuído de Gestão de Academia — GymDist Pro

Trabalho prático desenvolvido para a disciplina de **Sistemas Distribuídos** (Trabalho 2 — Exposição Web, API Gateway e Microsserviços Distribuídos).

O sistema evolui a arquitetura distribuída anterior para uma plataforma completa com interface gráfica (**Frontend Web SPA**), um **API Gateway centralizador (Spring Boot)** com segurança **JWT**, documentação interativa **Swagger/OpenAPI**, módulo de **geração de treinos assistida por IA**, e comunicação interna de alta performance via **gRPC/Protocol Buffers** integrada a três microsserviços com **persistência real em banco de dados relacional (PostgreSQL via JPA/Hibernate)**.

---

## 👥 Integrantes do Grupo

- **Andressa Gonçalves Barros**
- **Igor Carvalho Dantas**
- **Wallace Elpidio Pereira Cardoso**

---

## 🏛️ Arquitetura do Sistema

A arquitetura adota o padrão **"REST na Borda, gRPC por Dentro"**, garantindo facilidade de integração com clientes web e mobile, e máxima eficiência na comunicação interna entre microsserviços:

```text
 ┌────────────────────────────────────────────────────────────────────────┐
 │                              CLIENTES                                  │
 │         Navegador Web (Frontend SPA)  /  Postman  /  Swagger UI        │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ HTTP/REST (JSON) + Bearer JWT
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                     API GATEWAY (Spring Boot :8080)                    │
 │                                                                        │
 │  • Filtro de Autenticação JWT (HS256) ──▶ Retorna 401 se inválido      │
 │  • Bean Validation (@Valid)            ──▶ Retorna 400 se JSON inválido│
 │  • Orquestração & Deadline (5s)         ──▶ Retorna 504 se tempo esgotar│
 │  • Tradução JSON ⇄ Protobuf                                            │
 │  • Assistente IA de Treinos (Gemini API + Motor Heurístico)            │
 │  • Documentação OpenAPI / Swagger UI (/swagger-ui.html)                │
 │  • Frontend Web SPA Integrado (/)                                      │
 └─────────────┬─────────────────────┬─────────────────────┬──────────────┘
               │ gRPC :9091          │ gRPC :9092          │ gRPC :9093
               │ HTTP/2              │ HTTP/2              │ HTTP/2
               ▼                     ▼                     ▼
 ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
 │     aluno-service    │  │    treino-service    │  │  financeiro-service  │
 │     (gRPC :9091)     │  │     (gRPC :9092)     │  │     (gRPC :9093)     │
 └──────────┬───────────┘  └──────────┬───────────┘  └──────────┬───────────┘
            │ JPA/Hibernate           │ JPA/Hibernate           │ JPA/Hibernate
            ▼                         ▼                         ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                    BANCO DE DADOS (PostgreSQL :5432)                   │
 │       Tabelas: alunos  ·  fichas_treino  ·  itens_treino  ·  mensalidades  │
 └────────────────────────────────────────────────────────────────────────┘
```

### Divisão de Responsabilidades

| Módulo | Tipo | Tecnologia | Porta | Responsabilidade Principal |
|---|---|---|---|---|
| **`api-gateway`** | Borda REST | Spring Boot 3, Spring Security, Swagger | `8080` | Ponto único de entrada, validação de tokens JWT, Bean Validation, tradução JSON ⇄ Protobuf, orquestrador de IA e host do Frontend SPA. |
| **`aluno-service`** | Microsserviço | gRPC, JPA / Hibernate | `9091` | Gestão cadastral de alunos, verificação de duplicidade de CPF e controle de status de matrícula (`ATIVO`, `INATIVO`, `INADIMPLENTE`). |
| **`treino-service`** | Microsserviço | gRPC, JPA / Hibernate | `9092` | Criação de fichas de treino, catálogo de exercícios e **registro de execução com persistência das cargas reais utilizadas**. |
| **`financeiro-service`**| Microsserviço | gRPC, JPA / Hibernate | `9093` | Geração de mensalidades com vencimento, máquina de estados (`PENDENTE` ➔ `PAGO`/`ATRASADO`) e quitação segura anti-duplo pagamento. |
| **`contratos-grpc`** | Contratos | Protocol Buffers (Proto3) | — | Definição tipada dos serviços e mensagens RPC (`aluno.proto`, `treino.proto`, `financeiro.proto`). |

---

## 🔒 Segurança & Autenticação JWT (Padrão Aula 7)

O sistema implementa autenticação **Stateless** via Spring Security e OAuth2 Resource Server:
- **Algoritmo:** HMAC-SHA256 (`HS256`).
- **Segredo Didático:** `segredo-didatico-com-tamanho-suficiente-para-hs256` (configurável via variável `JWT_SECRET`).
- **Duração do Token:** 30 minutos após emissão.
- **Cabeçalho Obrigatório:** `Authorization: Bearer <TOKEN>` em todas as rotas operacionais de `/api/**`.
- **Filtro de Segurança:** Requisições sem token ou com token inválido/expirado são rejeitadas com **`401 Unauthorized`** e cabeçalho `WWW-Authenticate: Bearer`.

### Como obter um Token JWT:
1. **Via Endpoint REST:**
   ```bash
   curl -X POST http://localhost:8080/api/autenticar \
     -H "Content-Type: application/json" \
     -d '{"username":"aluno","senha":"123"}'
   ```
2. **Via Endpoint Rápido de Laboratório:**
   ```bash
   curl http://localhost:8080/api/gerar-token
   ```
3. **Via Linha de Comando (idêntico à Aula 7):**
   ```bash
   ./mvnw exec:java -pl api-gateway -Dexec.mainClass="academia.gateway.seguranca.GeradorTokenJwt"
   ```

---

## 📖 Documentação Interativa Swagger / OpenAPI 3

O API Gateway disponibiliza a documentação viva de todas as rotas através do Swagger UI:
- **URL de Acesso:** `http://localhost:8080/swagger-ui.html`
- **JSON OpenAPI:** `http://localhost:8080/v3/api-docs`
- **Autenticação no Swagger:** Clique no botão verde **"Authorize"** no topo da página e insira seu token no formato:
  ```text
  Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
  ```

---

## 🌐 Endpoints REST (Rotas Iniciadas por Verbos)

Todas as rotas da API foram projetadas iniciando por verbos para facilitar o entendimento:

### 1. Autenticação
- `POST /api/autenticar` — Realiza login e emite token JWT (`200 OK`).
- `GET /api/gerar-token` — Emite token rápido de demonstração (`200 OK`).

### 2. Gestão de Alunos
- `POST /api/cadastrar-aluno` — Cadastra novo aluno (`201 Created` / `400 Bad Request` / `409 Conflict`).
- `GET /api/listar-alunos` — Lista todos os alunos cadastrados (`200 OK`).
- `GET /api/buscar-aluno/{id}` — Busca detalhes de um aluno por UUID (`200 OK` / `404 Not Found`).
- `PATCH /api/atualizar-status-aluno/{id}` — Atualiza status de matrícula (`200 OK`).

### 3. Fichas de Treino & Inteligência Artificial
- `POST /api/criar-ficha-treino` — Criação manual de ficha com exercícios (`201 Created`).
- `POST /api/gerar-treino-ia` — **Geração inteligente de treino assistida por IA** (`200 OK`).
- `GET /api/buscar-ficha-treino/{id}` — Busca detalhes de uma ficha (`200 OK` / `404 Not Found`).
- `GET /api/listar-fichas-aluno/{alunoId}` — Lista histórico de treinos do aluno (`200 OK`).
- `POST /api/registrar-execucao-treino/{id}` — **Conclui treino e salva as cargas reais utilizadas** (`200 OK`).

### 4. Gestão Financeira
- `POST /api/gerar-mensalidade` — Gera cobrança com vencimento (`201 Created`).
- `GET /api/buscar-mensalidade/{id}` — Busca mensalidade por UUID (`200 OK` / `404 Not Found`).
- `GET /api/listar-mensalidades-aluno/{alunoId}` — Lista histórico de cobranças do aluno (`200 OK`).
- `POST /api/registrar-pagamento-mensalidade/{id}` — Quita cobrança (`PIX`, `CARTAO`, `DINHEIRO`, `BOLETO`) (`200 OK` / `422 Unprocessable`).

---

## 🤖 Geração de Treinos com Inteligência Artificial

O endpoint `POST /api/gerar-treino-ia` orquestra uma integração de alto nível entre os microsserviços:
1. O Gateway consulta a biometria do aluno (peso, altura, idade, histórico de cargas) no `aluno-service` via gRPC.
2. A inteligência sintetiza a rotina de exercícios com base no objetivo (`HIPERTROFIA`, `EMAGRECIMENTO`, `CONDICIONAMENTO`, `FORCA`), nível do praticante (`INICIANTE`, `INTERMEDIARIO`, `AVANCADO`) e agrupamento muscular desejado.
3. Se a chave `GEMINI_API_KEY` estiver configurada, o modelo de linguagem do Google Gemini é acionado; se estiver offline no laboratório, o **motor heurístico esportivo local** sintetiza o treino automaticamente, garantindo que a apresentação nunca falhe.
4. A ficha gerada é persistida diretamente no PostgreSQL através do `treino-service` via gRPC.

---

## 💻 Frontend Web SPA

Uma interface web moderna e reativa foi embutida diretamente no API Gateway (`api-gateway/src/main/resources/static/`), acessível imediatamente em:
```text
http://localhost:8080/
```

### Funcionalidades da Interface:
- **Dashboard:** Visão geral da rede, status dos serviços e contadores em tempo real.
- **Gestão de Alunos:** Formulário de cadastro com validação e tabela com alternância de status.
- **Fichas de Treino:** Criação manual e tela de **execução**, onde o aluno preenche a carga real utilizada (kg) em cada exercício para progressão futura.
- **Painel IA ✨:** Assistente para gerar treinos personalizados em um clique.
- **Financeiro:** Emissão de boletos/mensalidades e modal para quitação via PIX ou Cartão.
- **Central de Demonstração (Checklist de Avaliação):** Botões dedicados para testar ao vivo diante do professor os cenários avaliados:
  - `201 OK` (Requisição autenticada com sucesso).
  - `401 Unauthorized` (Barrado pelo filtro de segurança do Gateway).
  - `400 Bad Request` (Rejeição por Bean Validation de campos obrigatórios).
  - `404 Not Found` (Consulta de UUID inexistente traduzida de gRPC para HTTP).

---

## ⚙️ Pré-requisitos de Instalação

- **Java JDK 21** instalado e configurado no `PATH`.
- **PostgreSQL 14+** em execução (localmente ou no Google Cloud SQL).
- **Git** e terminal bash.
- Conexão de rede (para download inicial de dependências do Maven).

---

## 🚀 Como Executar o Projeto

### 1. Criar o Banco de Dados no PostgreSQL
Conecte-se ao PostgreSQL e crie o banco da aplicação:
```sql
CREATE DATABASE academia;
```

### 2. Compilar Todos os Módulos
Na raiz do repositório (`/home/igord/academia`):
```bash
./mvnw clean compile
```

### 3. Inicializar os Serviços (Terminais Separados ou VMs)

#### Terminal 1 — Microsserviço de Alunos (Porta gRPC 9091)
```bash
export DB_URL="jdbc:postgresql://localhost:5432/academia"
export DB_USER="postgres"
export DB_PASSWORD="sua_senha_do_postgres"

./mvnw exec:java -pl aluno-service
```
*(Saída esperada: `[ServidorAlunoGrpc] Servidor iniciado na porta 9091`)*

#### Terminal 2 — Microsserviço de Treinos (Porta gRPC 9092)
```bash
export DB_URL="jdbc:postgresql://localhost:5432/academia"
export DB_USER="postgres"
export DB_PASSWORD="sua_senha_do_postgres"

./mvnw exec:java -pl treino-service
```
*(Saída esperada: `[ServidorTreinoGrpc] Servidor iniciado na porta 9092`)*

#### Terminal 3 — Microsserviço Financeiro (Porta gRPC 9093)
```bash
export DB_URL="jdbc:postgresql://localhost:5432/academia"
export DB_USER="postgres"
export DB_PASSWORD="sua_senha_do_postgres"

./mvnw exec:java -pl financeiro-service
```
*(Saída esperada: `[ServidorFinanceiroGrpc] Servidor iniciado na porta 9093`)*

#### Terminal 4 — API Gateway REST + Frontend + Swagger (Porta HTTP 8080)
```bash
# Caso os serviços gRPC estejam em outras VMs da GCP, configure os IPs:
# export ALUNO_HOST="IP_INTERNO_ALUNO"
# export TREINO_HOST="IP_INTERNO_TREINO"
# export FINANCEIRO_HOST="IP_INTERNO_FINANCEIRO"

./mvnw spring-boot:run -pl api-gateway
```
*(Saída esperada: `Tomcat started on port 8080 (http)`)*

---

## 🧪 Como Testar no Postman

Uma coleção completa e atualizada está pronta para importação no arquivo:
👉 [`academia-postman-collection.json`](file:///home/igord/academia/academia-postman-collection.json)

1. Abra o Postman e clique em **Import**.
2. Selecione o arquivo `academia-postman-collection.json`.
3. Execute primeiro a requisição `0. Autenticação > Realizar Login` para receber o token JWT.
4. O Postman já possui a variável `{{baseUrl}} = http://localhost:8080` e todas as chamadas configuradas com `Bearer {{jwtToken}}`.

---

## ☁️ Implantação no Google Cloud Platform (GCP)

Para a apresentação oficial com VMs distribuídas na GCP:

1. **Firewall VPC na GCP:**
   - Liberar entrada TCP na porta `8080` (API Gateway) para `0.0.0.0/0`.
   - As portas gRPC `9091`, `9092` e `9093` devem ficar acessíveis **somente na rede interna (VPC)** entre as VMs.
2. **Cloud SQL (PostgreSQL):**
   - Autorizar os IPs das VMs que executam os serviços de backend (`aluno-service`, `treino-service`, `financeiro-service`).
   - O `api-gateway` **não acessa o banco diretamente**, garantindo o isolamento da arquitetura.
3. **Inicialização no Cloud:**
   - Basta passar os IPs internos nas variáveis `ALUNO_HOST`, `TREINO_HOST` e `FINANCEIRO_HOST` ao rodar o `api-gateway`.
