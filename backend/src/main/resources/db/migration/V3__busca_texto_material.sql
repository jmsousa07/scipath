-- V3 — Busca lexical da listagem pública (ADR-0008, RF003, RN013, RNF020).
--
-- Fonte: DRS §12.1 ("a busca inicial é lexical, simples e previsível... deve consultar
-- título e descrição"), §20.2 ("o MVP prioriza busca lexical simples e filtros explícitos")
-- e o parâmetro `q` do contrato v0.3 (components/parameters/FiltroBusca).
--
-- Migração ADITIVA e IMUTÁVEL (RNF028): cria UM índice. Não cria extensão, não altera
-- tabela, coluna nem dado. Reversão em ./README.md.
--
-- Por que o índice não usa a configuração `portuguese` de fábrica sozinha — medido nesta
-- máquina (PostgreSQL 16.15) e é a razão de existir do translate abaixo:
--
--     to_tsvector('portuguese', 'matemática') -> 'matemát'
--     to_tsvector('portuguese', 'matematica') -> 'matemat'
--     to_tsvector('portuguese', 'ordenação')  -> 'orden'
--     to_tsvector('portuguese', 'ordenacao')  -> 'ordenaca'
--
-- Sem dobrar o acento, buscar "matematica" NÃO acharia o material cujo título traz
-- "matemática" — o oposto do que o contrato promete ("matemática" e "matematica" casam o
-- mesmo conjunto") e do que a RN013 exige (busca consistente). O acento também contamina o
-- radical: 'ordenação' e 'ordenacao' saem com stems diferentes. Dobrar o acento ANTES do
-- to_tsvector faz os dois lados virarem o mesmo texto — e portanto o mesmo radical:
--
--     to_tsvector('portuguese', translate('matemática', ...)) -> 'matemat'
--     to_tsvector('portuguese', translate('matematica', ...)) -> 'matemat'
--     to_tsvector('portuguese', translate('ordenação',  ...)) -> 'ordenaca'
--     to_tsvector('portuguese', translate('ordenacao',  ...)) -> 'ordenaca'
--
-- A dobra é um `translate()` explícito, e não a extensão `unaccent`, por três motivos
-- (ADR-0008 §Alternativas descartadas):
--   1. `CREATE EXTENSION` exige CREATE no banco. A role de desenvolvimento só tem
--      CONNECT/TEMPORARY no banco de teste (infra/db-test-setup.sh) — `make db:migrate:test`
--      passaria a falhar, quebrando o invariante do card E1.12 (as duas roles migram o banco
--      de teste) e a reprodutibilidade do caminho documentado no README.
--   2. Adicionaria o pacote postgresql-contrib como dependência de IMPLANTAÇÃO — decisão
--      operacional que não é deste card (ADR-0007 trata ambientes).
--   3. O mapa de `unaccent` é arquivo do sistema (share/tsearch_data/unaccent.rules), fora do
--      repositório: o resultado da busca mudaria com o pacote do sistema, sem nenhuma mudança
--      no código. O translate é imutável, vive no repositório e é verificável por teste.
--
-- O mapa dobra as duas caixas (maiúscula e minúscula) e NÃO chama lower(): `lower()` depende
-- da collation/ctype do banco (num banco LC_CTYPE=C ele não dobra "Á"), e o que sobra depois
-- do translate é ASCII, que o parser do tsvector rebaixa de forma determinística. Os dois
-- mapas têm 54 caracteres — `translate()` exige comprimentos iguais e falha alto se
-- divergirem. A MESMA dobra está em MateriaisRepositorio (lado da consulta): índice e
-- consulta precisam ser a mesma expressão, senão o planejador não reconhece o índice.

-- Índice da busca: GIN sobre o vetor de título + descrição — o escopo que o §12.1 e o
-- contrato fixam. Nome público do autor e categoria ficam de fora: o §12.1 os admite "se for
-- compatível com a implementação escolhida", e o contrato não os promete.
--
-- É índice de EXPRESSÃO, não coluna nova: o vetor deriva de duas colunas que já existem, e
-- uma coluna gerada faria `material` carregar um dado que só a busca usa (e mais um passo de
-- escrita em toda publicação).
--
-- Não é índice parcial (`WHERE estado_publicacao = 'PUBLICADO'`): o ganho de tamanho não paga
-- o risco de o predicado deixar de ser reconhecido quando a consulta mudar. Se o volume
-- exigir, entra como migração aditiva — decisão de outro card, não "melhoria" deste.
CREATE INDEX material_busca_idx ON material USING GIN (
    to_tsvector('portuguese',
        translate(titulo || ' ' || descricao,
                  'ÀÁÂÃÄÅàáâãäåÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûüÇçÑñÝŸýÿ',
                  'AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCcNnYYyy'))
);

COMMENT ON INDEX material_busca_idx IS 'Índice GIN da busca por texto da listagem pública (RF003, RNF020). Cobre título e descrição com acento dobrado (ADR-0008); não cobre nome do autor nem categoria.';
