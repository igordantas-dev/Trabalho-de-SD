DO $test$
BEGIN
    IF (SELECT count(*) FROM public.alunos) <> 1
       OR (SELECT count(*) FROM public.fichas_treino) <> 1
       OR (SELECT count(*) FROM public.itens_treino) <> 1
       OR (SELECT count(*) FROM public.mensalidades) <> 1
       OR (SELECT count(*) FROM public.schema_migrations WHERE version = '001') <> 1
       OR (SELECT count(*) FROM public.schema_migrations WHERE version = '002') <> 1 THEN
        RAISE EXCEPTION 'Baseline reapplied: rows or migration history changed unexpectedly';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.alunos WHERE email = 'schema@example.com' AND senha_hash IS NULL)
       OR NOT EXISTS (SELECT 1 FROM public.mensalidades WHERE valor = 99.90 AND status = 'PENDENTE') THEN
        RAISE EXCEPTION 'Application fixture values were not preserved';
    END IF;
END;
$test$;
