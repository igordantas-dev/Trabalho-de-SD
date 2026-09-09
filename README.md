# Sistema Distribuído de Gestão de Academia — GymFlow

Trabalho prático desenvolvido para a disciplina de Sistemas Distribuídos. O projeto consiste em um sistema de microsserviços distribuídos para gestão de atendimento, geração de treinos e check-in de alunos, utilizando comunicação de alta performance via **gRPC** e hospedagem na **Google Cloud Platform (GCP)**.

---

## Integrantes do Grupo

- Andressa Gonçalves Barros
- Igor Carvalho Dantas
- Wallace Elpidio Pereira Cardoso

---

## Escopo do Projeto

O sistema **GymFlow** gerencia o fluxo de entrada e prescrições de treinos em uma rede de academias. A arquitetura é dividida em dois microsserviços backend que se comunicam por chamadas RPC síncronas usando **Protocol Buffers (.proto)**.

1. **atendimento-service (Microsserviço A - Cliente/Servidor):**
   - Atua como ponto de entrada principal do atendimento.
   - Recebe requisições de check-in e consulta de treinos na **porta 9090**.
   - Delega a validação cadastral e regras de negócio para o serviço de treino atuando como cliente gRPC.

2. **treino-service (Microsserviço B - Servidor):**
   - Executa na **porta 9091**.
   - Gerencia a base de matrículas em memória e valida se o aluno está ativo ou inadimplente.
   - Gera a lista customizada de exercícios baseada no perfil do aluno (objetivo e nível de experiência) e autoriza ou bloqueia o check-in na catraca.

---

## Arquitetura do Projeto

```text
Cliente (Local/App)
      │
      │ gRPC :9090
      ▼
ServidorAtendimentoGrpc (VM 1 - GCP)
      │
      │ ClienteTreinoGrpc (gRPC :9091)
      ▼
ServidorTreinoGrpc (VM 2 - GCP)
      │
      ▼
Validação de Matrícula e Geração de Treino
```

---

## Pré-requisitos

- Java JDK 21 configurado no ambiente.
- Git para clonar e versionar o código.
- Maven 3.8+ (opcional, pois o repositório inclui o Maven Wrapper em `./mvnw`).

---

## Compilação e Build do Projeto

Antes de rodar os serviços pela primeira vez, é necessário compilar o módulo de contratos (`contratos-grpc`). Isso gera automaticamente os stubs e classes Java a partir do arquivo `academia.proto`.

Na raiz do projeto (`~/academia`), execute:

```bash
# Dar permissão de execução ao wrapper (necessário apenas na primeira vez no Linux/macOS)
chmod +x mvnw

# Limpar, compilar contratos gRPC e empacotar todos os módulos
./mvnw clean install
```

---

## Como Executar Localmente (3 Terminais)

Para simular o ecossistema distribuído localmente, abra três abas ou janelas do terminal, todas posicionadas na raiz do projeto (`~/academia`).

### Terminal 1 - Iniciar o Servidor de Treinos 

O serviço de treinos deve ser iniciado primeiro para ficar aguardando as requisições de validação:

```bash
./mvnw -pl treino-service exec:java -Dexec.mainClass="academia.treino.ServidorTreinoGrpc"
```

Saída esperada: `Servidor de Treinos gRPC rodando na porta 9091`.

### Terminal 2 - Iniciar o Servidor de Atendimento

Este serviço atuará como fachada/gateway gRPC para as requisições dos clientes:

```bash
./mvnw -pl atendimento-service exec:java -Dexec.mainClass="academia.atendimento.ServidorAtendimentoGrpc"
```

Saída esperada: `Servidor de Atendimento gRPC rodando na porta 9090`.

### Terminal 3 - Executar o Cliente de Testes

Execute o cliente para simular as chamadas da catraca/app consultando treinos e realizando check-in:

```bash
./mvnw -pl atendimento-service exec:java -Dexec.mainClass="academia.atendimento.ClienteAtendimentoGrpc"
```

---

##  Implantação e Execução na GCP

### Configuração de Firewall na GCP

No painel da VPC na GCP, libere as seguintes portas de entrada:

- `allow-grpc-atendimento`: porta TCP `9090` aberta para `0.0.0.0/0`
- `allow-grpc-treino`: porta TCP `9091` aberta para a subrede/IP da VM de atendimento

### Execução em Instâncias Compute Engine

#### VM do Servidor de Treino (VM 2)

```bash
./mvnw -pl treino-service exec:java -Dexec.mainClass="academia.treino.ServidorTreinoGrpc"
```

#### VM do Servidor de Atendimento (VM 1)

Defina o IP interno da VM de Treino antes de subir o serviço:

```bash
export TREINO_HOST="<IP_INTERNO_VM_TREINO>"
./mvnw -pl atendimento-service exec:java -Dexec.mainClass="academia.atendimento.ServidorAtendimentoGrpc"
```

#### Máquina Local (Cliente)

Defina o IP público da VM de Atendimento e faça os testes:

```bash
export ATENDIMENTO_HOST="<IP_PUBLICO_VM_ATENDIMENTO>"
./mvnw -pl atendimento-service exec:java -Dexec.mainClass="academia.atendimento.ClienteAtendimentoGrpc"
```

---
