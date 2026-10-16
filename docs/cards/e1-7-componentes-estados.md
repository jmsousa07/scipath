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
