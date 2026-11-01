#!/usr/bin/env node
/**
 * card E3.6, critério 3 (parte executável): cada classe de erro do contrato vira um
 * estado do §15.3 — exercitado com os PAYLOADS REAIS do contrato, não com a intenção.
 *
 * Por que um script Node e não o navegador: a prova do card de implementação é
 * `next build` + lint + typecheck (AGENTS.md — navegador é ferramenta do card de QA). O
 * mapeamento, esse, é lógica pura justamente para poder ser exercitado aqui: o script
 * importa `src/lib/sessao/erros.ts` DIRETO e alimenta cada caso com o `ProblemDetail` que
 * o contrato publica (`components/schemas/ProblemDetail` e as respostas de `/contas` e
 * `/sessoes`). Quem remove os tipos é `scripts/ts-loader.mjs`, registrado por
 * `scripts/ts-registrar.mjs` — o type stripping NATIVO não serve: o Node 22 do pacote
 * Ubuntu é compilado sem suporte a TypeScript (`process.features.typescript === false`),
 * então importar `.ts` cru dá `ERR_UNKNOWN_FILE_EXTENSION` e, com
 * `--experimental-strip-types`, `ERR_NO_TYPESCRIPT` (card E1.6-f1).
 *
 * O que este script NÃO prova: que o servidor responde aquilo (isso é do backend e do
 * QA) e que a tela PINTA o estado (isso é do navegador). Prova que, dado o que o
 * contrato diz, a decisão do cliente é a certa.
 *
 * Uso: node --import ./scripts/ts-registrar.mjs scripts/check-erros-sessao.mjs
 *      (é o que `npm run check:erros-sessao` faz)
 */
import { DESFECHO_DUPLICADO, desfechoDeCamposInvalidos, mapearFalhaDeEnvio } from '../src/lib/sessao/erros.ts';
import { caminhoInterno } from '../src/lib/sessao/destino.ts';
import { validarCadastro, validarCredenciais } from '../src/lib/sessao/validacao.ts';

let falhas = 0;
let total = 0;

function verificar(titulo, condicao, detalhe) {
  total += 1;
  if (condicao) {
    console.log(`PASS  ${titulo}`);
    return;
  }
  falhas += 1;
  console.log(`FALHA ${titulo}${detalhe === undefined ? '' : `  — ${detalhe}`}`);
}

/** `ProblemDetail` do contrato (RFC 7807) — `type` é o vocabulário fechado. */
const problema = (type, status, extra = {}) => ({
  type: `urn:scipath:erro:${type}`,
  title: type,
  status,
  ...extra,
});

console.log('== check:erros-sessao — falha do contrato → estado do formulário (DRS §15.3) ==');
console.log('fonte das mensagens: contracts/openapi.yaml v0.4.0 (ProblemDetail, /contas, /sessoes)\n');

console.log('1. falha sem resposta (rede/tempo) — POST /contas');
{
  const d = mapearFalhaDeEnvio({ motivo: 'sem-resposta' }, 'criar-conta');
  verificar('estado = recoverable-failure', d.estado === 'recoverable-failure', d.estado);
  verificar('mensagem diz que nada foi perdido', /continua preenchido/.test(d.mensagem), d.mensagem);
}

console.log('\n2. 2xx sem corpo JSON do contrato');
{
  const d = mapearFalhaDeEnvio({ motivo: 'corpo-invalido' }, 'criar-conta');
  verificar('estado = recoverable-failure', d.estado === 'recoverable-failure', d.estado);
  verificar('classe = corpo-invalido', d.classe === 'corpo-invalido', d.classe);
}

console.log('\n3. 401 nao_autenticado no LOGIN (corpo idêntico para e-mail inexistente e senha errada — RNF013)');
{
  const problem = problema('nao_autenticado', 401, {
    title: 'Credenciais inválidas',
    detail: 'E-mail ou senha incorretos.',
  });
  const d = mapearFalhaDeEnvio({ motivo: 'http', status: 401, problem }, 'abrir-sessao');
  verificar('estado = invalid-field', d.estado === 'invalid-field', d.estado);
  verificar('NÃO aponta campo (o contrato não diz qual falhou)', Object.keys(d.campos).length === 0, JSON.stringify(d.campos));
  verificar('não repete o detail do servidor como verdade sobre o campo', !/E-mail ou senha incorretos/.test(d.mensagem), d.mensagem);
}

console.log('\n4. 401 nao_autenticado em escrita com identidade (sessão acabou)');
{
  const d = mapearFalhaDeEnvio(
    { motivo: 'http', status: 401, problem: problema('nao_autenticado', 401) },
    'reenviar-verificacao',
  );
  verificar('estado = recoverable-failure', d.estado === 'recoverable-failure', d.estado);
  verificar('sessaoEncerrada = true', d.sessaoEncerrada === true, String(d.sessaoEncerrada));
}

console.log('\n5. 403 acesso_negado (conta suspensa / CSRF — POST /sessoes)');
{
  const problem = problema('acesso_negado', 403, {
    title: 'Conta suspensa',
    detail: 'Esta conta está suspensa e não pode entrar.',
  });
  const d = mapearFalhaDeEnvio({ motivo: 'http', status: 403, problem }, 'abrir-sessao');
  verificar('estado = recoverable-failure', d.estado === 'recoverable-failure', d.estado);
  verificar('acessoNegado = true', d.acessoNegado === true, String(d.acessoNegado));
  verificar('explica o que o servidor disse e o que fazer', /suspensa/.test(d.mensagem) && /recarregue/.test(d.mensagem), d.mensagem);
}

console.log('\n6. 409 conflito_de_unicidade — e-mail já cadastrado (POST /contas)');
{
  const problem = problema('conflito_de_unicidade', 409, {
    title: 'E-mail já cadastrado',
    detail: 'Já existe conta com este e-mail.',
    errors: [{ campo: 'email', mensagem: 'Este e-mail já pertence a uma conta.' }],
  });
  const d = mapearFalhaDeEnvio({ motivo: 'http', status: 409, problem }, 'criar-conta');
  verificar('estado = invalid-field', d.estado === 'invalid-field', d.estado);
  verificar('mensagem associada ao campo email', typeof d.campos.email === 'string', JSON.stringify(d.campos));
  verificar('campoFoco = email (âncora "Ir para o campo")', d.campoFoco === 'email', d.campoFoco);
  verificar('classe = conflito_de_unicidade', d.classe === 'conflito_de_unicidade', d.classe);
}

console.log('\n7. 422 validacao_campo — campo inválido (POST /contas)');
{
  const problem = problema('validacao_campo', 422, {
    title: 'Campo inválido',
    detail: 'Um ou mais campos do corpo são inválidos.',
    errors: [
      { campo: 'senha', mensagem: 'A senha precisa de 8 caracteres no mínimo.' },
      { campo: 'nomePublico', mensagem: 'Informe o nome público.' },
      { campo: 'slugDoServidor', mensagem: 'campo que o formulário não tem' },
    ],
  });
  const d = mapearFalhaDeEnvio({ motivo: 'http', status: 422, problem }, 'criar-conta');
  verificar('estado = invalid-field', d.estado === 'invalid-field', d.estado);
  verificar('dois campos associados', Object.keys(d.campos).length === 2, JSON.stringify(d.campos));
  verificar('campo desconhecido NÃO some (vai para o resumo)', d.foraDosCampos.length === 1, JSON.stringify(d.foraDosCampos));
  verificar('resumo cita o campo desconhecido', /slugDoServidor/.test(d.mensagem), d.mensagem);
}

console.log('\n8. 429 reenvio_em_cooldown (POST /verificacao-email/reenvios, Retry-After)');
{
  const problem = problema('reenvio_em_cooldown', 429, {
    title: 'Aguarde para reenviar',
    detail: 'O último reenvio foi feito há menos de 60 segundos.',
  });
  const d = mapearFalhaDeEnvio({ motivo: 'http', status: 429, problem, retryAfterS: 42 }, 'reenviar-verificacao');
  verificar('estado = recoverable-failure', d.estado === 'recoverable-failure', d.estado);
  verificar('aguardarS vem do Retry-After (42)', d.aguardarS === 42, String(d.aguardarS));
  verificar('mensagem diz o tempo', /42 s/.test(d.mensagem), d.mensagem);
}

console.log('\n9. 500 interno e 502 do proxy (API inalcançável)');
{
  const interno = mapearFalhaDeEnvio(
    { motivo: 'http', status: 500, problem: problema('interno', 500) },
    'abrir-sessao',
  );
  const proxy = mapearFalhaDeEnvio({ motivo: 'http', status: 502, problem: null }, 'abrir-sessao');
  verificar('500 → recoverable-failure', interno.estado === 'recoverable-failure', interno.estado);
  verificar('502 → recoverable-failure com mensagem de servidor', /servidor falhou/.test(proxy.mensagem), proxy.mensagem);
}

console.log('\n10. 400 validacao (pedido ilegível)');
{
  const d = mapearFalhaDeEnvio({ motivo: 'http', status: 400, problem: problema('validacao', 400) }, 'criar-conta');
  verificar('estado = recoverable-failure', d.estado === 'recoverable-failure', d.estado);
}

console.log('\n11. validação do próprio formulário (antes da requisição)');
{
  const vazio = validarCredenciais({ email: '', senha: '' });
  verificar('login: e-mail e senha vazios → dois erros', Boolean(vazio.email) && Boolean(vazio.senha), JSON.stringify(vazio));

  const formato = validarCredenciais({ email: 'maria.silva', senha: 'nota-de-aula-2026' });
  verificar('login: e-mail sem @ → erro no campo', /@/.test(formato.email ?? ''), JSON.stringify(formato));

  const curta = validarCadastro({ email: 'a@b.br', senha: '1234567', senhaConfirmacao: '1234567', nomePublico: 'Maria' });
  verificar('cadastro: senha de 7 caracteres → erro (minLength 8)', /8 caracteres/.test(curta.senha ?? ''), JSON.stringify(curta));

  const longa = validarCadastro({ email: 'a@b.br', senha: 'x'.repeat(73), senhaConfirmacao: 'x'.repeat(73), nomePublico: 'Maria' });
  verificar('cadastro: senha de 73 caracteres → erro (teto do bcrypt)', /72/.test(longa.senha ?? ''), JSON.stringify(longa));

  const diferentes = validarCadastro({ email: 'a@b.br', senha: 'nota-de-aula-2026', senhaConfirmacao: 'outra-senha', nomePublico: 'Maria' });
  verificar('cadastro: confirmação diferente → erro no campo de confirmação', Boolean(diferentes.senhaConfirmacao), JSON.stringify(diferentes));

  const ok = validarCadastro({ email: 'maria.silva@exemplo.br', senha: 'nota-de-aula-2026', senhaConfirmacao: 'nota-de-aula-2026', nomePublico: 'Maria Silva' });
  verificar('cadastro válido → nenhum erro', Object.keys(ok).length === 0, JSON.stringify(ok));

  const desfecho = desfechoDeCamposInvalidos(diferentes);
  verificar('desfecho local = invalid-field com âncora no primeiro campo', desfecho.estado === 'invalid-field' && desfecho.campoFoco === 'senhaConfirmacao', JSON.stringify(desfecho.campoFoco));

  verificar('desfecho duplicado = duplicate-attempt', DESFECHO_DUPLICADO.estado === 'duplicate-attempt', DESFECHO_DUPLICADO.estado);
}

console.log('\n12. destino preservado (?proximo=) — só caminho interno');
{
  verificar('/painel/perfil é aceito', caminhoInterno('/painel/perfil') === '/painel/perfil');
  verificar('//outro-host é recusado (open redirect)', caminhoInterno('//outro-host/x') === undefined);
  verificar('https://outro.site é recusado', caminhoInterno('https://outro.site/x') === undefined);
  verificar('vazio/ausente é recusado', caminhoInterno(undefined) === undefined && caminhoInterno('') === undefined);
  verificar('lista (parâmetro repetido) usa o primeiro valor interno', caminhoInterno(['/a', '//b']) === '/a');
}

console.log(
  `\nresumo: ${total} verificações · ${falhas === 0 ? 'todas passaram' : `${falhas} falharam`}`,
);
process.exit(falhas === 0 ? 0 : 1);
