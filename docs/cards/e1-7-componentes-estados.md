# E1.7 — Componentes base e os estados obrigatórios

- **Perfil:** frontend · **Depende de:** E1.6 · **Status:** pronto para execução

## Objetivo

Entregar os componentes reutilizáveis que todas as telas das Etapas 2 a 5 vão usar — especialmente
os **sete estados de página** e os **seis estados de formulário** do DRS §15.3, que hoje não
existem e por isso seriam reinventados em cada tela.

## Escopo

1. `<PageState>` com os 7 estados: carregando, sucesso, vazio, erro de rede/servidor, não
   encontrado, não autorizado, sessão expirada.
2. `<FormState>` com os 6 estados: campo inválido, envio em andamento (sem envio duplo), falha
   recuperável (dados preservados), sucesso, tentativa duplicada, arquivo acima do limite/formato
   inválido.
3. Primitivos conforme `DESIGN_SYSTEM.md` §3: botão (variantes e desabilitado), campo de texto
   (com rótulo e erro associados), card, badge/chip, tabela com cabeçalho e paginação.
4. Rota isolada de demonstração `/_estados` (com `noindex`) **ou** testes de componente cobrindo
   cada estado — escolher uma e registrar a escolha no card. A demonstração não usa dado fictício
   apresentado como real: os rótulos dizem explicitamente que é demonstração de estado (§20.1).

## Fora de escopo

Telas do produto, formulários reais, integração com API.

## Critérios de aceite

1. Cada um dos 13 estados é alcançável e verificável (rota ou teste) — com a evidência colada.
2. Mensagem de erro acionável em cada caso: o que aconteceu e o que fazer (RNF004), nunca "erro".
3. Erro associado ao controle, rótulo associado ao campo, foco visível, operável só por teclado
   (RNF006, RNF007).
4. Nenhum hex/px de cor solto; nenhum componente novo duplicando primitive existente (RNF003).
5. `next build` + lint + typecheck verdes.

## Evidência esperada

Log de build/lint; lista dos 13 estados com onde cada um foi verificado; testes (ou print da rota
de demonstração).

## Rastreabilidade

RNF003, RNF004, RNF006, RNF007, RF022, PAGE_BLUEPRINTS.md, DRS §15.3.

## Registro de execução

- **Demonstração escolhida (item 4 do escopo):** a **rota `/_estados`**, e não testes de componente.
  Motivo: o que precisa ser verificado é o estado RENDERIZADO — erro associado ao controle, rótulo
  associado ao campo, foco visível, mensagem acionável —, e isso se confere na tela, com teclado
  (card de QA). Um runner de teste de componente seria dependência nova, que não é deste card.
  A verificação automática é `npm run check:states`: confere, no HTML de produção da rota, o
  marcador `data-state` de cada um dos 13 estados. A rota é `noindex`, não está em nenhuma navegação
  e todos os seus rótulos dizem que é demonstração (§20.1).
- **Camada do contrato visual ampliada pelo gerador:** cards, badges, chips e o indicador de
  atividade entraram na lista do `extract-ds-layer.mjs` (101 regras, contra 76 do E1.6). Nenhum CSS
  do DS2 foi transcrito à mão.
- **Primitivos que o DS2 não tem, criados na camada de projeto** (`frontend/src/styles/layout.css`):
  `.sc-table` (variante de produto — ver DESIGN_SYSTEM.md §7), `.sc-pagination` e `.sc-notice`
  (mensagem de estado, compartilhada por `<PageState>` e `<FormState>`, para não existirem treze
  blocos diferentes — RNF003).
- **Componentes entregues** em `frontend/src/components/ui/`: `Button` (com `SubmitButton`,
  o botão que não permite envio duplo), `TextField`/`TextAreaField`/`FileField`, `Card`, `Badge`,
  `Chip`, `DataTable`, `Pagination`, `PageState`, `FormState` e `mensagens.ts` (catálogo único das
  mensagens acionáveis de cada estado — RNF004).
- **Evidência:** `docs/qa/e1-7-componentes-2026-10-16.log`.
