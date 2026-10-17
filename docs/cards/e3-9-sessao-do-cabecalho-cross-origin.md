# E3.9 — Sessão do cabeçalho não chega à API no navegador (estado fica `network-error`)

**Perfil:** frontend · **Depende de:** E3.8 (achado da verificação independente da Etapa 3) · **Status:** pronto para execução

## Contexto

Achado **medido no navegador** na verificação E3.8 (card `t_76729dda`) — evidência na seção "ADENDO 1 — VERIFICAÇÃO NO NAVEGADOR" de `docs/qa/e3-identidade-2026-10-17.log` e nas capturas `docs/qa/e3-8-navegador-*.png`.

O que acontece: servindo o build do frontend (a saída de `npm run verify`) e abrindo `/cadastro`, `/entrar` ou `/painel` num navegador real, o **cabeçalho não consegue conferir a sessão**. A tela mostra "Não foi possível verificar a sua sessão / O servidor não respondeu à consulta de sessão" e os elementos de estado ficam `data-state="network-error"`.

Isso não aparece nas verificações que leem o HTML servido (seção 8 da bateria: o documento entregue está correto), porque o cabeçalho resolve a sessão no **cliente**, depois da hidratação.

Por que importa: o aviso de "verificação pendente" e o estado de sessão vivem nesse componente de cliente. Enquanto a chamada falhar, uma pessoa com sessão aberta vê o cabeçalho como se **não** estivesse autenticada, e o aviso de e-mail pendente não aparece. O item 8 do escopo da Etapa 3 fica com ressalva por causa disto.

**Hipótese com base medida (confirmar antes de corrigir):** o cliente chama `http://localhost:8080/api` (`BASE_PADRAO` de `frontend/src/lib/api/client.ts`, usada quando `NEXT_PUBLIC_API_BASE_URL` não foi inlinado no build) — origem diferente da página — e (i) `backend/src/main` não tem **nenhuma** configuração de CORS (varredura por `Cors|Access-Control|allowedOrigin`: 0 ocorrências) e (ii) `frontend/next.config.*` não declara proxy/rewrite de `/api`. Pedido entre origens sem `Access-Control-Allow-Origin` é bloqueado pelo navegador. Não é artefato da porta usada na verificação: qualquer porta da web é cross-origin em relação à API.

## Escopo

1. Decidir e implementar **um** caminho canônico: proxy/rewrite same-origin no Next, CORS explícito no backend restrito às origens de desenvolvimento, ou inlinar a base do cliente no build. A decisão fica escrita (ADR ou `docs/01-arquitetura.md`) — não no código sem registro.
2. Se a decisão mexer na fronteira de API, começar por `contracts/openapi.yaml` (contrato primeiro).
3. Comprovar **no navegador** que o cabeçalho mostra o estado correto para os três casos: visitante, conta `PENDENTE_VERIFICACAO` e conta `ATIVO`.
4. Manter o que já funciona: o HTML entregue continua correto (RNF023) e o cookie de sessão segue `httpOnly`+`Secure`+`SameSite=Lax` (ADR-0003). Se a saída for CORS, o navegador passa a **enviar credenciais entre origens** — isso exige `credentials: include` e origem explícita, nunca `*`.

## Fora de escopo

Refazer o cabeçalho ou a página de painel. Mexer nas decisões do ADR-0003 (bcrypt, cookie, CSRF).

## Critérios de aceite

1. Captura de tela do cabeçalho **hidratado** nos três casos (visitante, pendente, ativo), com o estado correto.
2. A linha de rede da chamada de sessão (status e origem) colada na evidência, mostrando que a chamada chega à API.
3. Nenhum `data-state="network-error"` em `/`, `/cadastro`, `/entrar`, `/painel` no build servido.
4. `npm run build` + lint + typecheck verdes; se houver mudança no backend, `make test` verde.
5. Evidência versionada em `docs/qa/e3-9-<data>.log`.

## Rastreabilidade

ADR-0003 (b),(e); DRS §15.2, §17.3, §21.4; achado do adendo 1 de `docs/qa/e3-identidade-2026-10-17.log`.
