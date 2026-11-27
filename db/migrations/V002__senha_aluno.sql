-- Alunos existentes preservados, sem senha padrao. A gestao deve definir o acesso.
ALTER TABLE public.alunos ADD COLUMN IF NOT EXISTS senha_hash varchar(512);
