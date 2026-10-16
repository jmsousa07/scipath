# E2.7 — Página inicial

- **Perfil:** frontend · **Depende de:** E2.1, E1.8 · **Status:** pronto para execução

## Objetivo

Entregar `/` (RF001): explicar o produto em segundos e dar o caminho para explorar ou publicar.

## Escopo

1. Proposta de valor, busca em destaque (leva a `/explorar?q=`), lista de categorias e materiais
   recentes.
2. "Recentes" segue **a regra documentada no contrato** (ordem padrão do E2.1) — nenhum destaque,
   contagem ou popularidade inventada (§12.4).
3. Os **7 estados** de página (contrato do E1.7) aplicados de verdade.
4. **Acervo vazio não pode parecer página quebrada**: sem materiais, a home mostra o convite a
   publicar e a explicação do produto — este é o estado real do produto até a Etapa 4 existir.
5. Conteúdo no HTML entregue (SSR/ISR) e metadados estáveis (RNF023–RNF025).

## Fora de escopo

Listagem com filtros (E2.8), telas autenticadas.

## Critérios de aceite

1. `next build` + lint + typecheck verdes.
2. HTML entregue contém proposta de valor, categorias e os materiais recentes (verificável sem JS).
3. Estado vazio exercitado e registrado na evidência (com o banco sem materiais publicados).
4. Nenhum valor de estilo solto — tokens do E1.6.

## Evidência esperada

Log de build/lint, e a listagem do HTML entregue (`curl`) mostrando conteúdo e metadados.

## Rastreabilidade

RF001, RNF003, RNF004, RNF020, RNF023–RNF025, CA001, CA011, DRS §12.4, §15.
