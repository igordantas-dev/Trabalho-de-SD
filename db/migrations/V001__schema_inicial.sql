-- Baseline PostgreSQL 16, compatible with the JPA entities.
-- Run through db/Apply.psql so validation and version registration are atomic.
-- Existing application tables and rows are never replaced.
CREATE TABLE IF NOT EXISTS public.alunos (
    id uuid PRIMARY KEY,
    nome varchar(255) NOT NULL,
    cpf varchar(30) NOT NULL,
    email varchar(255) NOT NULL,
    telefone varchar(255),
    peso double precision,
    altura double precision,
    status varchar(20) NOT NULL,
    CONSTRAINT uk2t1qqj9okv59i9gcj7hjirhju UNIQUE (cpf),
    CONSTRAINT ukmbvpvjdhy6gsmbw6odlro3ub UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS public.fichas_treino (
    id uuid PRIMARY KEY,
    aluno_id varchar(255) NOT NULL,
    titulo varchar(255) NOT NULL,
    objetivo varchar(100) NOT NULL,
    status varchar(20) NOT NULL,
    data_criacao timestamp(6) without time zone NOT NULL,
    data_conclusao timestamp(6) without time zone
);

CREATE TABLE IF NOT EXISTS public.itens_treino (
    id uuid PRIMARY KEY,
    ficha_treino_id uuid NOT NULL,
    nome_exercicio varchar(255) NOT NULL,
    grupo_muscular varchar(255),
    series integer NOT NULL,
    repeticoes integer NOT NULL,
    carga_sugerida double precision,
    carga_usada double precision,
    concluido boolean NOT NULL,
    CONSTRAINT fk_itens_treino_ficha FOREIGN KEY (ficha_treino_id)
        REFERENCES public.fichas_treino (id)
);

CREATE TABLE IF NOT EXISTS public.mensalidades (
    id uuid PRIMARY KEY,
    aluno_id varchar(255) NOT NULL,
    valor double precision NOT NULL,
    data_vencimento date NOT NULL,
    data_pagamento timestamp(6) without time zone,
    status varchar(20) NOT NULL,
    forma_pagamento varchar(20),
    descricao varchar(255)
);

-- IDs/status/default values are assigned by Java, matching the existing entities.
-- aluno_id remains a logical string reference; no cross-service foreign key.
