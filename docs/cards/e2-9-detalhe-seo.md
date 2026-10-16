# E2.9 — Detalhe do material + SEO

- **Perfil:** frontend · **Depende de:** E2.4, E2.7 · **Status:** pronto para execução

## Objetivo

Entregar `/materiais/[slug]` (RF005) de forma indexável e honesta sobre o que o material é.

## Escopo

1. Conteúdo: título, descrição, tipo, categoria, **licença explicada em linguagem simples**
   (o que permite / o que não permite — RN006), autor com link para o perfil (RF007), data.
2. Ação de acesso ao arquivo **visível apenas quando houver arquivo público**; sem a Etapa 4, o
   estado esperado é o material sem arquivo disponível, com mensagem clara.
3. **Nenhuma** marca de revisão, certificação, curadoria ou verificação (RN010, RN011, CA012).
4. SEO (RNF023–RNF025): `title`, `description` e `canonical` estáveis a partir do slug; conteúdo
   presente no HTML entregue, não só após JS.
5. Material retirado/inexistente → o estado `não encontrado` correto (não um erro genérico).

## Fora de escopo

Download de arquivo (Etapa 4), comentários, avaliações.

## Critérios de aceite

1. `curl` da página entrega o conteúdo do material no HTML (evidência colada) — SEO depende disso.
2. `canonical` correto e estável; sem marcações de dado estruturado que não sejam verdadeiras.
3. Estado `não encontrado` exercitado com um slug inexistente e outro retirado.
4. `next build` + lint + typecheck verdes.

## Evidência esperada

Log de build/lint; saída do `curl` com conteúdo e metadados; os dois casos de 404.

## Rastreabilidade

RF005, RF007, RN006, RN010, RN011, RNF013, RNF023, RNF024, RNF025, CA002, CA008, CA011, CA012.
