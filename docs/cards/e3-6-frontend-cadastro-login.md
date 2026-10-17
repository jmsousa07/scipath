# E3.6 — Frontend: cadastro, login e logout

- **Perfil:** frontend · **Depende de:** E3.1 · **Status:** pronto para execução

## Contexto

As páginas autenticadas da Etapa 3 (§15.2) começam pelo fluxo de sessão. RF008/RF009 e CA004:
criar conta, autenticar e encerrar sessão **sem expor credenciais**. DRS §7.2: depois do cadastro o
usuário entra na sessão e é orientado a completar o perfil, **sem bloquear** a navegação pública.

## Escopo

1. Página de **cadastro** e de **login**, e a ação de **logout** com o estado de sessão consistente
   em todo o site (o cabeçalho reflete sessão; nenhuma tela depende de esconder botão para
   proteger dado — RNF010 e o critério de RF009).
2. Todos os **estados obrigatórios** de página e de formulário do §15.3 aplicados de verdade:
   carregamento, sucesso, erro de rede/servidor, campo inválido, envio em andamento, falha
   recuperável, tentativa duplicada. Campo com mensagem associada (acessibilidade, CA009).
3. Erros do contrato tratados por código: 401 credencial inválida, 409 e-mail já cadastrado, 422
   campo inválido, 429 reenvio em cooldown.
4. Sessão via cookie do ADR-0003: as chamadas passam pelo **proxy `/api` do Next** (mesma origem) —
   nada de `Authorization` no `localStorage` nem token em JavaScript.
5. Após o cadastro, aviso **persistente e honesto** de que a verificação de e-mail falta para
   publicar (ADR-0003 (h)), com caminho para reenviar — sem muro silencioso.
6. Nada de dado fictício em tela; sem dado real de conta alheia; nenhuma credencial em mensagem de
   erro ou em log do navegador.

## Fora de escopo

Editar perfil e área do usuário (E3.7), páginas de publicação (Etapa 4), rodar `next dev` ou
navegador headless como prova (proibido em card de implementação — prova é `next build` + lint +
typecheck).

## Critérios de aceite

1. `next build` + `npm run lint` + typecheck verdes; log colado.
2. HTML entregue contém o formulário e os campos principais (verificável sem JS).
3. Cada estado do §15.3 exercitado e registrado na evidência (uma linha por estado, com o que foi
   feito para provocá-lo) — inclusive o 409 de e-mail duplicado e o 429 do cooldown.
4. Nenhum valor de estilo solto: tokens do E1.6; sem cor/tamanho hardcoded.
5. Navegação por teclado no formulário com foco visível e mensagem de erro associada ao campo.

## Evidência esperada

Log de build/lint/typecheck, a lista de estados com o que os provoca, e o HTML entregue de cada
página (`curl`). Renomeie o arquivo de evidência com o id do card (regra P1).

## Rastreabilidade

RF008, RF009, RNF001, RNF002, RNF010, RNF013, CA004, CA009; ADR-0003 (e), (h);
DRS §7.2, §8.2.1, §8.2.2, §15.2, §15.3.
