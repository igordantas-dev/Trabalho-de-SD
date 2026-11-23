-- Only used in the isolated disposable PostgreSQL created by Test-Schema.ps1.
INSERT INTO public.alunos (id, nome, cpf, email, status)
VALUES ('11111111-1111-4111-8111-111111111111', 'Teste schema', '00000000000', 'schema@example.com', 'ATIVO');

INSERT INTO public.fichas_treino (id, aluno_id, titulo, objetivo, status, data_criacao)
VALUES ('22222222-2222-4222-8222-222222222222', '11111111-1111-4111-8111-111111111111',
        'Teste schema', 'HIPERTROFIA', 'PENDENTE', CURRENT_TIMESTAMP);

INSERT INTO public.itens_treino (id, ficha_treino_id, nome_exercicio, series, repeticoes, concluido)
VALUES ('33333333-3333-4333-8333-333333333333', '22222222-2222-4222-8222-222222222222',
        'Supino', 3, 12, false);

INSERT INTO public.mensalidades (id, aluno_id, valor, data_vencimento, status)
VALUES ('44444444-4444-4444-8444-444444444444', '11111111-1111-4111-8111-111111111111',
        99.90, CURRENT_DATE + 10, 'PENDENTE');
