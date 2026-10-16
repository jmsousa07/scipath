# F0.4 — Frontend: tela de lista e leitura de material consumindo a API

## Contexto

Primeira tela real do produto: é o caminho que o usuário zero (o próprio dono) vai
percorrer para dizer "isso funciona". Consome a API do F0.3 pelos tipos gerados do contrato.

## Entrada

- `contracts/openapi.yaml` — `/materiais` (gere os tipos, não escreva à mão).
- `frontend/` criado no F0.2 (tokens, fontes, Tailwind).
- `assets/design_system_2.html` — use a linguagem do card de material e da lista.
- A API do F0.3 precisa estar no ar (`make api:dev`) para o teste manual.

## Saída

- `lib/api/client.ts` — cliente tipado único (`getMateriais`, `getMaterial`), erro tratado.
- `app/materiais/page.tsx` — lista com título, tipo, resumo, tags, autor e data.
- `app/materiais/[id]/page.tsx` — leitura do material (o endpoint de detalhe entra aqui:
  se não existir no contrato, **abra card de contrato** em vez de improvisar).
- `loading.tsx` e `error.tsx` por rota.

## Critérios de aceite

1. A lista mostra os materiais vindos da API (não dado fixo no código).
2. Os quatro estados tratados: carregando (esqueleto), vazio ("nenhum material ainda" com
   caminho para publicar), erro (mensagem útil + tentar de novo) e sucesso.
3. Nenhum `fetch` fora de `lib/api/client.ts`; tipos vindos do contrato (nenhum `any`).
4. Acessibilidade: navegação completa por teclado, foco visível, contraste AA nos textos
   sobre fundo branco, `article`/`nav` semânticos.
5. Funciona em 375 px e 1440 px sem rolagem horizontal.
6. `make lint` verde.

## Fora de escopo

Criar/editar material pela interface (entra na fatia 2), busca, filtros avançados,
paginação infinita, autenticação, tema escuro.

## Evidência exigida

Prints em `docs/qa/f0-4-lista-1440.png`, `docs/qa/f0-4-lista-375.png`,
`docs/qa/f0-4-vazio-1440.png`, `docs/qa/f0-4-erro-1440.png` (o de erro com a API derrubada)
+ `docs/qa/f0-4-frontend.log` com `make lint` e o resultado do teste de teclado.
