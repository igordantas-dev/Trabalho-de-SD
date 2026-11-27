-- Read-only checks. Also runs inside the baseline transaction.
DO $validation$
DECLARE
    mismatch text;
    relation_name text;
    unique_column text;
BEGIN
    WITH expected(table_name, column_name, udt_name, max_length, is_nullable) AS (
        VALUES
        ('alunos', 'id', 'uuid', NULL, 'NO'),
        ('alunos', 'nome', 'varchar', 255, 'NO'),
        ('alunos', 'cpf', 'varchar', 30, 'NO'),
        ('alunos', 'email', 'varchar', 255, 'NO'),
        ('alunos', 'telefone', 'varchar', 255, 'YES'),
        ('alunos', 'peso', 'float8', NULL, 'YES'),
        ('alunos', 'altura', 'float8', NULL, 'YES'),
        ('alunos', 'status', 'varchar', 20, 'NO'),
        ('alunos', 'senha_hash', 'varchar', 512, 'YES'),
        ('fichas_treino', 'id', 'uuid', NULL, 'NO'),
        ('fichas_treino', 'aluno_id', 'varchar', 255, 'NO'),
        ('fichas_treino', 'titulo', 'varchar', 255, 'NO'),
        ('fichas_treino', 'objetivo', 'varchar', 100, 'NO'),
        ('fichas_treino', 'status', 'varchar', 20, 'NO'),
        ('fichas_treino', 'data_criacao', 'timestamp', NULL, 'NO'),
        ('fichas_treino', 'data_conclusao', 'timestamp', NULL, 'YES'),
        ('itens_treino', 'id', 'uuid', NULL, 'NO'),
        ('itens_treino', 'ficha_treino_id', 'uuid', NULL, 'NO'),
        ('itens_treino', 'nome_exercicio', 'varchar', 255, 'NO'),
        ('itens_treino', 'grupo_muscular', 'varchar', 255, 'YES'),
        ('itens_treino', 'series', 'int4', NULL, 'NO'),
        ('itens_treino', 'repeticoes', 'int4', NULL, 'NO'),
        ('itens_treino', 'carga_sugerida', 'float8', NULL, 'YES'),
        ('itens_treino', 'carga_usada', 'float8', NULL, 'YES'),
        ('itens_treino', 'concluido', 'bool', NULL, 'NO'),
        ('mensalidades', 'id', 'uuid', NULL, 'NO'),
        ('mensalidades', 'aluno_id', 'varchar', 255, 'NO'),
        ('mensalidades', 'valor', 'float8', NULL, 'NO'),
        ('mensalidades', 'data_vencimento', 'date', NULL, 'NO'),
        ('mensalidades', 'data_pagamento', 'timestamp', NULL, 'YES'),
        ('mensalidades', 'status', 'varchar', 20, 'NO'),
        ('mensalidades', 'forma_pagamento', 'varchar', 20, 'YES'),
        ('mensalidades', 'descricao', 'varchar', 255, 'YES')
    )
    SELECT string_agg(format('%I.%I: expected %s, length=%s, nullable=%s; found %s, length=%s, nullable=%s',
        e.table_name, e.column_name, e.udt_name, e.max_length, e.is_nullable,
        c.udt_name, c.character_maximum_length, c.is_nullable), E'\n')
    INTO mismatch
    FROM expected e
    LEFT JOIN information_schema.columns c
      ON c.table_schema = 'public'
     AND c.table_name = e.table_name
     AND c.column_name = e.column_name
    WHERE c.column_name IS NULL
       OR c.udt_name <> e.udt_name
       OR c.character_maximum_length IS DISTINCT FROM e.max_length
       OR c.is_nullable <> e.is_nullable
       OR (e.udt_name = 'timestamp' AND c.datetime_precision <> 6);

    IF mismatch IS NOT NULL THEN
        RAISE EXCEPTION 'Schema incompatible with JPA entities:%', E'\n' || mismatch;
    END IF;

    FOREACH relation_name IN ARRAY ARRAY['alunos', 'fichas_treino', 'itens_treino', 'mensalidades']
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage k
              ON k.constraint_schema = tc.constraint_schema
             AND k.constraint_name = tc.constraint_name
             AND k.table_name = tc.table_name
            WHERE tc.table_schema = 'public'
              AND tc.table_name = relation_name
              AND tc.constraint_type = 'PRIMARY KEY'
            GROUP BY tc.constraint_name
            HAVING array_agg(k.column_name::text ORDER BY k.ordinal_position) = ARRAY['id']::text[]
        ) THEN
            RAISE EXCEPTION 'Missing single-column primary key: %.id', relation_name;
        END IF;
    END LOOP;

    FOREACH unique_column IN ARRAY ARRAY['cpf', 'email']
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.table_constraints tc
            JOIN information_schema.key_column_usage k
              ON k.constraint_schema = tc.constraint_schema
             AND k.constraint_name = tc.constraint_name
             AND k.table_name = tc.table_name
            WHERE tc.table_schema = 'public' AND tc.table_name = 'alunos'
              AND tc.constraint_type = 'UNIQUE'
            GROUP BY tc.constraint_name
            HAVING array_agg(k.column_name::text ORDER BY k.ordinal_position)
                = ARRAY[unique_column]::text[]
        ) THEN
            RAISE EXCEPTION 'Missing uniqueness constraint: alunos.%', unique_column;
        END IF;
    END LOOP;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_attribute source ON source.attrelid = c.conrelid AND source.attnum = c.conkey[1]
        JOIN pg_attribute target ON target.attrelid = c.confrelid AND target.attnum = c.confkey[1]
        WHERE c.contype = 'f'
          AND c.conrelid = 'public.itens_treino'::regclass
          AND c.confrelid = 'public.fichas_treino'::regclass
          AND cardinality(c.conkey) = 1
          AND source.attname = 'ficha_treino_id'
          AND target.attname = 'id'
    ) THEN
        RAISE EXCEPTION 'Missing FK: itens_treino.ficha_treino_id -> fichas_treino.id';
    END IF;
END;
$validation$;

SELECT 'alunos' AS tabela, count(*) AS registros FROM public.alunos
UNION ALL SELECT 'fichas_treino', count(*) FROM public.fichas_treino
UNION ALL SELECT 'itens_treino', count(*) FROM public.itens_treino
UNION ALL SELECT 'mensalidades', count(*) FROM public.mensalidades
ORDER BY tabela;
