-- V2 — Seed da taxonomia de referência (card E1.3).
--
-- Fonte: ADR-0006 (tipos, categorias e licenças como dado de referência carregado por
-- migração versionada, sem painel administrativo no MVP), DRS §11.1.5–7, §6.3, RN005,
-- RN006, RF018.
--
-- Migração ADITIVA e IMUTÁVEL (RNF028): depois de aplicada, nunca editar este arquivo.
-- Mudar a lista — corrigir um nome, acrescentar categoria, desativar item — é a próxima
-- migração (V3, V4, ...), com UPDATE explícito. Isto é o caminho que o §6.3 autoriza.
--
-- Duas decisões de escrita, registradas de propósito:
--
--   1. `slug` é a chave. O INSERT nomeia o item pelo slug; o `nome` é rótulo de exibição
--      e pode mudar depois sem quebrar nada, porque material referencia `category.id`
--      (FK) e o cliente guarda/envia o slug (ADR-0006).
--   2. ON CONFLICT (slug) DO NOTHING: a idempotência de verdade é por versão do Flyway
--      (a V2 aplica uma vez, registrada em flyway_schema_history). O DO NOTHING cobre o
--      caso de reaplicação após reversão — quando o histórico de uma versão for apagado
--      (procedimento de ./README.md) a coluna UNIQUE(slug) ainda existe, e sem ele o
--      reapply abortaria com "duplicate key". A seed semeia; ela não sobrescreve quem
--      já está lá.
--
-- `ativo = true` em todos os itens: o contrato v0.2 filtra por estado ativo (default
-- true) e cada item devolve o campo, então item semeado nasce ativo.
--
-- Gênero nas descrições: "o material" (o objeto compartilhado), neutro quanto ao autor.

-- =====================================================================
-- 6 tipos de material (DRS §11.1.6, ADR-0006). Nome em português; slug é a chave.
-- =====================================================================

INSERT INTO material_type (nome, slug, ativo) VALUES
    ('Nota de aula',  'nota-de-aula',    true),
    ('Slides',        'slides',          true),
    ('Artigo',        'artigo',          true),
    ('Apresentação',  'apresentacao',    true),
    ('Guia de estudo','guia-de-estudo',  true),
    ('Outro',         'outro',           true)
ON CONFLICT (slug) DO NOTHING;

-- =====================================================================
-- 13 categorias (DRS §11.1.5, ADR-0006) — grandes áreas do conhecimento.
-- `parent_id` fica NULL em todas: o MVP usa um nível só (§11.1.5).
-- A descrição curta existe para descoberta e SEO (DRS §12), não é enfeite.
-- =====================================================================

INSERT INTO category (nome, slug, descricao, ativo) VALUES
    ('Computação e Tecnologia', 'computacao-e-tecnologia',
     'Algoritmos, programação, engenharia de software, redes, banco de dados, inteligência artificial e sistemas computacionais.', true),
    ('Matemática e Estatística', 'matematica-e-estatistica',
     'Álgebra, cálculo, geometria, análise, probabilidade, estatística e métodos matemáticos aplicados.', true),
    ('Física e Astronomia', 'fisica-e-astronomia',
     'Mecânica, termodinâmica, eletromagnetismo, física moderna, astronomia e astrofísica.', true),
    ('Química', 'quimica',
     'Química geral, orgânica, inorgânica, analítica, físico-química e bioquímica.', true),
    ('Ciências Biológicas', 'ciencias-biologicas',
     'Biologia celular e molecular, genética, ecologia, evolução, botânica, zoologia e microbiologia.', true),
    ('Saúde', 'saude',
     'Medicina, enfermagem, odontologia, farmácia, fisioterapia, saúde coletiva e demais áreas da saúde.', true),
    ('Engenharias', 'engenharias',
     'Engenharias civil, elétrica, mecânica, química, de produção, de materiais e áreas afins.', true),
    ('Ciências Agrárias', 'ciencias-agrarias',
     'Agronomia, zootecnia, ciência do solo, fitotecnia, veterinária e produção agropecuária.', true),
    ('Ciências Humanas', 'ciencias-humanas',
     'Filosofia, história, geografia, sociologia, psicologia, antropologia e educação.', true),
    ('Ciências Sociais Aplicadas', 'ciencias-sociais-aplicadas',
     'Administração, economia, direito, contabilidade, arquitetura e urbanismo, comunicação e serviço social.', true),
    ('Linguística e Letras', 'linguistica-e-letras',
     'Línguas, literatura, linguística, gramática, redação e estudos da tradução.', true),
    ('Artes', 'artes',
     'Artes visuais, música, teatro, dança, cinema e design.', true),
    ('Multidisciplinar', 'multidisciplinar',
     'Conteúdo que atravessa mais de uma área do conhecimento e não pertence a uma só categoria.', true)
ON CONFLICT (slug) DO NOTHING;

-- =====================================================================
-- 7 licenças (DRS §11.1.7, ADR-0006, RN006).
--
-- `identificador` recebe o identificador público da licença (SPDX) e `url_referencia` o
-- texto oficial; ambos são NULL quando não existem — caso de "Todos os direitos
-- reservados", que não tem texto público de referência.
--
-- `descricao_curta` é a regra do RN006 (e do §13.2): explica em linguagem simples o que
-- a licença PERMITE. Em especial, "Todos os direitos reservados" não é opção decorativa
-- nem sinônimo de domínio público — o texto diz que o material continua protegido, que é
-- exatamente o equívoco que o DRS manda evitar.
-- =====================================================================

INSERT INTO license (nome, slug, identificador, url_referencia, descricao_curta, ativo) VALUES
    ('CC BY 4.0', 'cc-by-4.0', 'CC-BY-4.0',
     'https://creativecommons.org/licenses/by/4.0/deed.pt-br',
     'Pode copiar, distribuir e adaptar o material, inclusive para fins comerciais, desde que cite o autor e indique esta licença.', true),

    ('CC BY-SA 4.0', 'cc-by-sa-4.0', 'CC-BY-SA-4.0',
     'https://creativecommons.org/licenses/by-sa/4.0/deed.pt-br',
     'Pode copiar, distribuir e adaptar o material, inclusive para fins comerciais, desde que cite o autor e compartilhe o que criar a partir dele sob esta mesma licença.', true),

    ('CC BY-NC 4.0', 'cc-by-nc-4.0', 'CC-BY-NC-4.0',
     'https://creativecommons.org/licenses/by-nc/4.0/deed.pt-br',
     'Pode copiar, distribuir e adaptar o material, desde que cite o autor e não faça uso comercial. Não é preciso compartilhar o que criar sob a mesma licença.', true),

    ('CC BY-NC-SA 4.0', 'cc-by-nc-sa-4.0', 'CC-BY-NC-SA-4.0',
     'https://creativecommons.org/licenses/by-nc-sa/4.0/deed.pt-br',
     'Pode copiar, distribuir e adaptar o material, desde que cite o autor, não faça uso comercial e compartilhe o que criar a partir dele sob esta mesma licença.', true),

    ('CC BY-ND 4.0', 'cc-by-nd-4.0', 'CC-BY-ND-4.0',
     'https://creativecommons.org/licenses/by-nd/4.0/deed.pt-br',
     'Pode copiar e distribuir o material, inclusive para fins comerciais, desde que cite o autor e não altere nem crie obras derivadas.', true),

    ('CC0 1.0 (domínio público)', 'cc0-1.0', 'CC0-1.0',
     'https://creativecommons.org/publicdomain/zero/1.0/deed.pt-br',
     'O autor renunciou aos direitos autorais sobre o material: pode usar, adaptar, redistribuir e usar comercialmente como quiser, sem pedir permissão e sem obrigação de citar.', true),

    ('Todos os direitos reservados', 'todos-os-direitos-reservados', NULL, NULL,
     'O material continua protegido por direitos autorais — publicar NÃO coloca a obra em domínio público. É permitido ler, baixar e citar; reutilizar, adaptar ou redistribuir exige autorização expressa do autor.', true)
ON CONFLICT (slug) DO NOTHING;
