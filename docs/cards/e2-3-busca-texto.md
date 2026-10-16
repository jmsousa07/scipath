# E2.3 — Busca por texto (com decisão registrada)

- **Perfil:** backend · **Depende de:** E2.2 · **Status:** pronto para execução

## Objetivo

Implementar o parâmetro `q` da listagem — busca lexical simples (pressuposto do DRS §20.2), com
comportamento documentado e sem promessa de relevância que o produto não tem.

## Escopo

1. **Registrar ADR-0008** com a estratégia escolhida e os trade-offs, porque isto é decisão de
   arquitetura e o DRS §21.5 exige motivos registrados. Recomendação: full-text do PostgreSQL
   (`tsvector` + índice GIN, configuração `portuguese`) sobre título e descrição, com `ILIKE` como
   alternativa se o ADR justificar melhor para o volume do MVP.
2. Busca combinável com os filtros de categoria e tipo já existentes (E2.2).
3. Termo que não encontra nada → **200 com lista vazia** e mensagem clara no frontend; nunca 404.
4. Busca resistente a acento e caixa: "matemática" e "matematica" devem encontrar o mesmo
   conjunto (critério verificável — RN013, busca consistente).

## Fora de escopo

Ordenação por relevância, sinônimos, busca semântica, IA (explicitamente fora do MVP, §20.2).

## Critérios de aceite

1. ADR-0008 escrito com alternativas descartadas e consequências.
2. Teste de integração: termo com e sem acento devolvendo o mesmo conjunto; termo vazio tratado.
3. `EXPLAIN` mostrando o índice de busca sendo usado (RNF020).
4. Nenhum resultado sem depender de configuração externa da máquina.

## Evidência esperada

ADR-0008, testes de integração colados, `EXPLAIN`.

## Rastreabilidade

RF003, RN013, RNF020, DRS §12.1, §20.2, §21.5.
