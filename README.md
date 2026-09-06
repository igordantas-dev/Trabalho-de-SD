# Sistema Distribuído de Gestão de Academia — GymFlow

Trabalho prático desenvolvido para a disciplina de Sistemas Distribuídos. O projeto consiste em um sistema de microsserviços distribuídos para gestão de atendimento, geração de treinos e check-in de alunos, utilizando comunicação de alta performance via **gRPC** e hospedagem na **Google Cloud Platform (GCP)**.

---

## 👥 Integrantes do Grupo

* Andressa Gonçalves Barros
* Igor Carvalho Dantas
* Wallace Elpidio Pereira Cardoso

---

## 🚀 Escopo do Projeto

O sistema **GymFlow** gerencia o fluxo de entrada e prescrições de treinos em uma rede de academias. A arquitetura é dividida em dois microsserviços backend que se comunicam através de chamadas RPC síncronas usando **Protocol Buffers (.proto)**:

1. **`atendimento-service` (Microsserviço A - Cliente/Servidor):**
   * Atua como ponto de entrada principal do atendimento.
   * Recebe requisições de check-in e consulta de treinos na **porta 9090**.
   * Delaga a validação cadastral e regras de negócio para o serviço de treino atuando como cliente gRPC.

2. **`treino-service` (Microsserviço B - Servidor):**
   * Executa na **porta 9091**.
   * Gerencia a base de matrículas em memória e valida se o aluno está ativo ou inadimplente.
   * Gera a lista customizada de exercícios baseada no perfil do aluno (objetivo e nível de experiência) e autoriza/bloqueia o check-in na catraca.

---

## 🏗️ Arquitetura do Projeto

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