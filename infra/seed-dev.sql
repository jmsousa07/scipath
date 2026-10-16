-- Seed de DESENVOLVIMENTO do SciPath (card E2.6).
--
-- Para que serve: dar o que listar, filtrar, buscar e paginar nas verificações da Etapa 2
-- (E2.2–E2.5, E2.11), sem que dado de exemplo possa passar por conteúdo real — risco
-- nomeado no DRS §20.1. NÃO é conteúdo do produto: nenhuma linha aqui é publicação de
-- alguém.
--
-- Onde roda: SÓ no banco local de DESENVOLVIMENTO (`scipath`, 127.0.0.1). Nunca no
-- `scipath_test` (a suíte de integração precisa de estado limpo) e nunca em produção.
-- A guarda do §0 abaixo recusa o banco de teste e qualquer servidor que não seja o
-- loopback; o alvo `make db:seed:dev` recusa antes disso, pelo nome do banco e do host.
-- Entrar por `psql -f` cru não escapa: a guarda está dentro do script, não só no Makefile.
--
-- Marcador de dado de exemplo: o slug (`exemplo-...`) e o e-mail (`@exemplo.invalid`,
-- domínio reservado pelo RFC 2606 — não existe e não recebe correio). Nome público e
-- título também começam com "Exemplo", para que print de tela não seja confundível com
-- material de verdade. Não há coluna nova nem flag no schema: o prefixo é o marcador.
--
-- Idempotente: UUIDs vêm do `gen_random_uuid()` do banco e as referências entre linhas
-- são por chave natural (e-mail, slug, chave_interna, com `ON CONFLICT DO NOTHING`).
-- Rodar duas, três vezes seguidas não duplica nem falha. Como a V2, esta seed semeia:
-- ela não sobrescreve linha que já existe (mudar dado de exemplo é editar este arquivo
-- e rodar `make db:reset`).
--
-- Reversível por `make db:reset` (recria o banco do zero e reaplica as migrações): o
-- dado de exemplo desaparece junto com o banco, sem resíduo no schema.
--
-- O que esta seed NÃO faz:
--   * não grava binário nenhum no storage — os `file_asset` são só metadado, então
--     download de arquivo de exemplo não é o que ela habilita (ver infra/README.md);
--   * não cria conta que consiga autenticar: a senha_hash é um bcrypt de formato válido
--     com pré-imagem desconhecida e descartada, para que nenhuma senha (nem a "óbvia")
--     entre como estes autores (ADR-0003).

-- =====================================================================
-- 0. Guardas de destino
-- =====================================================================

DO $$
DECLARE
    v_banco    text := current_database();
    v_servidor inet := inet_server_addr();   -- NULL quando a conexão é por socket unix local
BEGIN
    IF v_banco ~ '_test$' THEN
        RAISE EXCEPTION 'seed-dev recusado: "%" é banco de TESTE — a suíte precisa de estado limpo (card E2.6, critério 2)', v_banco;
    END IF;

    IF v_servidor IS NOT NULL AND host(v_servidor) NOT IN ('127.0.0.1', '::1') THEN
        RAISE EXCEPTION 'seed-dev recusado: servidor % não é local — dado de exemplo não vai para ambiente remoto (ADR-0007)', host(v_servidor);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '2' AND success) THEN
        RAISE EXCEPTION 'seed-dev recusado: taxonomia (V2) não aplicada em "%" — rode make db:migrate antes', v_banco;
    END IF;

    RAISE NOTICE 'seed-dev: banco local de desenvolvimento "%" (taxonomia V2 presente) — seguindo', v_banco;
END $$;

-- =====================================================================
-- 1. Contas de exemplo (ADR-0003). ATIVO + e-mail verificado: só assim um perfil
--    poderia publicar; o estado do dado de exemplo tem de ser o do caso normal.
-- =====================================================================

INSERT INTO account (email, senha_hash, estado, email_verificado_em) VALUES
    ('exemplo-ana.ribeiro@exemplo.invalid',
     '$2a$12$exemploDeDevSalt000000naohaLigacaoComNenhumaSenha0000', 'ATIVO', now()),
    ('exemplo-bruno.tavares@exemplo.invalid',
     '$2a$12$exemploDeDevSalt000000naohaLigacaoComNenhumaSenha0000', 'ATIVO', now()),
    ('exemplo-carolina.mendes@exemplo.invalid',
     '$2a$12$exemploDeDevSalt000000naohaLigacaoComNenhumaSenha0000', 'ATIVO', now())
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 2. Perfis de exemplo. `afiliacao_declarada` diz em texto que não há vínculo real:
--    o campo é declaração do autor e nunca pode ser lido como vínculo verificado
--    (RN011, DRS §13.1); aqui não se nomeia instituição nenhuma de verdade.
-- =====================================================================

INSERT INTO profile (account_id, nome_publico, slug, biografia, afiliacao_declarada, areas_interesse)
VALUES
    ((SELECT id FROM account WHERE email = 'exemplo-ana.ribeiro@exemplo.invalid'),
     'Exemplo: Ana Ribeiro', 'exemplo-ana-ribeiro',
     'Perfil de exemplo (dado de desenvolvimento) — não é uma pessoa real. Existe para dar autoria aos materiais de exemplo da Etapa 2.',
     'Instituição de exemplo (dado de desenvolvimento, sem vínculo real)',
     ARRAY['algoritmos', 'estruturas de dados', 'ensino de programação']),

    ((SELECT id FROM account WHERE email = 'exemplo-bruno.tavares@exemplo.invalid'),
     'Exemplo: Bruno Tavares', 'exemplo-bruno-tavares',
     'Perfil de exemplo (dado de desenvolvimento) — não é uma pessoa real. Autor de exemplo nas áreas de física, engenharia e química.',
     'Instituição de exemplo (dado de desenvolvimento, sem vínculo real)',
     ARRAY['mecânica clássica', 'energia solar', 'química geral']),

    ((SELECT id FROM account WHERE email = 'exemplo-carolina.mendes@exemplo.invalid'),
     'Exemplo: Carolina Mendes', 'exemplo-carolina-mendes',
     'Perfil de exemplo (dado de desenvolvimento) — não é uma pessoa real. Autora de exemplo na área de ciências biológicas.',
     'Instituição de exemplo (dado de desenvolvimento, sem vínculo real)',
     ARRAY['genética', 'biologia celular', 'ecologia'])
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 3. Arquivos (metadado, ADR-0005 §2). `chave_interna` tem forma de UUID com prefixo
--    zerado de propósito: é identificável como exemplo e não se confunde com chave
--    gerada pelo sistema. `checksum` é repetição de 'e' — inconfundível, e o binário
--    correspondente não existe no storage (esta seed é só de metadado).
-- =====================================================================

INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado, tamanho_bytes,
                        checksum, estado_processamento, enviado_em)
VALUES
    ('00000000-0000-4000-8000-000000000001', 'exemplo-nota-aula-algoritmos.pdf',      'application/pdf',  348160, repeat('e', 64), 'VALIDADO', timestamptz '2026-10-14 09:10:00-03'),
    ('00000000-0000-4000-8000-000000000002', 'exemplo-slides-estruturas-dados.pdf',   'application/pdf', 1048576, repeat('e', 64), 'VALIDADO', timestamptz '2026-10-08 14:35:00-03'),
    ('00000000-0000-4000-8000-000000000003', 'exemplo-guia-calculo-i.pdf',            'application/pdf',  215040, repeat('e', 64), 'VALIDADO', timestamptz '2026-09-30 20:05:00-03'),
    ('00000000-0000-4000-8000-000000000004', 'exemplo-artigo-mecanica-newtoniana.pdf','application/pdf',  512000, repeat('e', 64), 'PURGADO', timestamptz '2026-09-18 08:00:00-03'),
    ('00000000-0000-4000-8000-000000000005', 'exemplo-apresentacao-energia-solar.pdf','application/pdf', 2097152, repeat('e', 64), 'VALIDADO', timestamptz '2026-10-11 10:20:00-03'),
    ('00000000-0000-4000-8000-000000000006', 'exemplo-roteiro-titulacao.pdf',         'application/pdf',  163840, repeat('e', 64), 'VALIDADO', timestamptz '2026-09-25 16:45:00-03'),
    ('00000000-0000-4000-8000-000000000007', 'exemplo-genetica-mendeliana.pdf',       'application/pdf',  462848, repeat('e', 64), 'VALIDADO', timestamptz '2026-10-02 11:30:00-03'),
    ('00000000-0000-4000-8000-000000000008', 'exemplo-microscopia-celular.pdf',       'application/pdf',  786432, repeat('e', 64), 'VALIDADO', timestamptz '2026-10-15 19:00:00-03')
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 4. Materiais de exemplo — 8, distribuídos nos 3 autores.
--
--    Cobrem os 6 tipos de material, 6 categorias e as 7 licenças (uma de cada),
--    com datas de publicação espalhadas de propósito: a listagem pública ordena por
--    `publicado_em DESC` (DRS §12.2) e a paginação precisa de ordem estável.
--
--    Dois estados além de PUBLICADO, exigidos pelo card:
--      * 'nota-aula-genetica-mendeliana' → RETIRADO (fora do público, reversível): é o
--        material para verificar o 404 no detalhe (E2.4);
--      * 'artigo-mecanica-newtoniana'    → EXCLUIDO (exclusão lógica): o `file_asset`
--        desse material está 'PURGADO', que é a combinação que o ADR-0005 §3 prescreve
--        (metadado retido, binário removido).
--
--    Sobram 6 PUBLICADO — 3 do Ana, 2 do Bruno e 1 da Carolina — para que perfil
--    público (E2.5) e filtros por autor tenham o que mostrar em cada autor.
--
--    As FKs da taxonomia vêm por subselect no `slug` (chave estável, ADR-0006). Se a V2
--    não estiver aplicada, o subselect devolve NULL e o NOT NULL do material aborta a
--    execução — falha barulhenta, nunca seed pela metade.
-- =====================================================================

INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                      license_id, file_asset_id, estado_publicacao, publicado_em)
VALUES
    ('exemplo-nota-aula-algoritmos-de-ordenacao',
     'Exemplo: Algoritmos de ordenação — nota de aula',
     'Material de exemplo (dado de desenvolvimento): nota de aula introdutória sobre ordenação por comparação, com análise de complexidade.',
     (SELECT id FROM profile WHERE slug = 'exemplo-ana-ribeiro'),
     (SELECT id FROM category WHERE slug = 'computacao-e-tecnologia'),
     (SELECT id FROM material_type WHERE slug = 'nota-de-aula'),
     (SELECT id FROM license WHERE slug = 'cc-by-4.0'),
     (SELECT id FROM file_asset WHERE chave_interna = '00000000-0000-4000-8000-000000000001'),
     'PUBLICADO', timestamptz '2026-10-14 09:10:00-03'),

    ('exemplo-slides-estruturas-de-dados',
     'Exemplo: Estruturas de dados — slides de revisão',
     'Material de exemplo (dado de desenvolvimento): slides de revisão de listas, pilhas, filas e árvores.',
     (SELECT id FROM profile WHERE slug = 'exemplo-ana-ribeiro'),
     (SELECT id FROM category WHERE slug = 'computacao-e-tecnologia'),
     (SELECT id FROM material_type WHERE slug = 'slides'),
     (SELECT id FROM license WHERE slug = 'cc-by-sa-4.0'),
     (SELECT id FROM file_asset WHERE chave_interna = '00000000-0000-4000-8000-000000000002'),
     'PUBLICADO', timestamptz '2026-10-08 14:35:00-03'),

    ('exemplo-guia-de-estudo-calculo-i',
     'Exemplo: Cálculo I — guia de estudo para a primeira prova',
     'Material de exemplo (dado de desenvolvimento): roteiro de estudo de limites, derivadas e aplicações.',
     (SELECT id FROM profile WHERE slug = 'exemplo-ana-ribeiro'),
     (SELECT id FROM category WHERE slug = 'matematica-e-estatistica'),
     (SELECT id FROM material_type WHERE slug = 'guia-de-estudo'),
     (SELECT id FROM license WHERE slug = 'cc-by-nc-4.0'),
     (SELECT id FROM file_asset WHERE chave_interna = '00000000-0000-4000-8000-000000000003'),
     'PUBLICADO', timestamptz '2026-09-30 20:05:00-03'),

    ('exemplo-artigo-mecanica-newtoniana',
     'Exemplo: Mecânica newtoniana — artigo de revisão',
     'Material de exemplo (dado de desenvolvimento): artigo de revisão sobre as leis de Newton e suas consequências. Este material está EXCLUIDO (exclusão lógica, ADR-0005 §3) — serve para verificar o 404.',
     (SELECT id FROM profile WHERE slug = 'exemplo-bruno-tavares'),
     (SELECT id FROM category WHERE slug = 'fisica-e-astronomia'),
     (SELECT id FROM material_type WHERE slug = 'artigo'),
     (SELECT id FROM license WHERE slug = 'cc-by-nd-4.0'),
     (SELECT id FROM file_asset WHERE chave_interna = '00000000-0000-4000-8000-000000000004'),
     'EXCLUIDO', timestamptz '2026-09-18 08:00:00-03'),

    ('exemplo-apresentacao-energia-solar-fotovoltaica',
     'Exemplo: Energia solar fotovoltaica — apresentação',
     'Material de exemplo (dado de desenvolvimento): apresentação sobre geração fotovoltaica e dimensionamento básico.',
     (SELECT id FROM profile WHERE slug = 'exemplo-bruno-tavares'),
     (SELECT id FROM category WHERE slug = 'engenharias'),
     (SELECT id FROM material_type WHERE slug = 'apresentacao'),
     (SELECT id FROM license WHERE slug = 'cc-by-4.0'),
     (SELECT id FROM file_asset WHERE chave_interna = '00000000-0000-4000-8000-000000000005'),
     'PUBLICADO', timestamptz '2026-10-11 10:20:00-03'),

    ('exemplo-roteiro-de-laboratorio-titulacao-acido-base',
     'Exemplo: Titulação ácido-base — roteiro de laboratório de química',
     'Material de exemplo (dado de desenvolvimento): roteiro de prática de titulação ácido-base, tipo "Outro" do vocabulário.',
     (SELECT id FROM profile WHERE slug = 'exemplo-bruno-tavares'),
     (SELECT id FROM category WHERE slug = 'quimica'),
     (SELECT id FROM material_type WHERE slug = 'outro'),
     (SELECT id FROM license WHERE slug = 'todos-os-direitos-reservados'),
     (SELECT id FROM file_asset WHERE chave_interna = '00000000-0000-4000-8000-000000000006'),
     'PUBLICADO', timestamptz '2026-09-25 16:45:00-03'),

    ('exemplo-nota-aula-genetica-mendeliana',
     'Exemplo: Genética mendeliana — nota de aula',
     'Material de exemplo (dado de desenvolvimento): nota de aula sobre as leis de Mendel e heredogramas. Este material está RETIRADO (fora do público, reversível) — serve para verificar o 404 no detalhe.',
     (SELECT id FROM profile WHERE slug = 'exemplo-carolina-mendes'),
     (SELECT id FROM category WHERE slug = 'ciencias-biologicas'),
     (SELECT id FROM material_type WHERE slug = 'nota-de-aula'),
     (SELECT id FROM license WHERE slug = 'cc-by-nc-sa-4.0'),
     (SELECT id FROM file_asset WHERE chave_interna = '00000000-0000-4000-8000-000000000007'),
     'RETIRADO', timestamptz '2026-10-02 11:30:00-03'),

    ('exemplo-slides-microscopia-celular',
     'Exemplo: Microscopia celular — slides',
     'Material de exemplo (dado de desenvolvimento): slides sobre técnicas de microscopia e observação de células.',
     (SELECT id FROM profile WHERE slug = 'exemplo-carolina-mendes'),
     (SELECT id FROM category WHERE slug = 'ciencias-biologicas'),
     (SELECT id FROM material_type WHERE slug = 'slides'),
     (SELECT id FROM license WHERE slug = 'cc0-1.0'),
     (SELECT id FROM file_asset WHERE chave_interna = '00000000-0000-4000-8000-000000000008'),
     'PUBLICADO', timestamptz '2026-10-15 19:00:00-03')
ON CONFLICT DO NOTHING;

-- =====================================================================
-- 5. Conferência — a saída desta consulta é a evidência do card (contagem por tabela,
--    sempre restrita ao marcador de exemplo, e a quebra de material por estado).
--    A segunda consulta é o que o critério 1 do card olha: rodar duas vezes e a
--    contagem não mudar.
-- =====================================================================

\echo '-- seed-dev: contagem por tabela (só linhas de exemplo)'
SELECT tabela, linhas FROM (
    SELECT 1 AS ordem, 'account (exemplo)'    AS tabela, count(*) AS linhas FROM account    WHERE email LIKE '%@exemplo.invalid'
    UNION ALL
    SELECT 2, 'profile (exemplo)',            count(*) FROM profile  WHERE slug LIKE 'exemplo-%'
    UNION ALL
    SELECT 3, 'file_asset (exemplo)',         count(*) FROM file_asset WHERE chave_interna LIKE '00000000-0000-4000-8000-%'
    UNION ALL
    SELECT 4, 'material (exemplo)',           count(*) FROM material WHERE slug LIKE 'exemplo-%'
) t ORDER BY ordem;

\echo '-- seed-dev: material de exemplo por estado de publicação'
SELECT estado_publicacao, count(*) AS materiais
FROM material WHERE slug LIKE 'exemplo-%'
GROUP BY estado_publicacao ORDER BY estado_publicacao;

\echo '-- seed-dev: material de exemplo PUBLICADO por autor'
SELECT p.slug AS autor, count(*) AS publicados
FROM material m JOIN profile p ON p.id = m.profile_id
WHERE m.slug LIKE 'exemplo-%' AND m.estado_publicacao = 'PUBLICADO'
GROUP BY p.slug ORDER BY p.slug;
