#!/usr/bin/env node
/**
 * card E3.7, critério 3 (parte executável): as falhas de `PUT /perfil/atual` — e a validação
 * local dos campos do RF011 — viram o estado do §15.3 certo, exercitadas com os PAYLOADS REAIS
 * do contrato e do backend.
 *
 * Por que um script Node e não o navegador: a prova do card de implementação é `next build` +
 * lint + typecheck (AGENTS.md — navegador é ferramenta do card de QA). O mapeamento, esse, é
 * lógica pura de propósito: este script importa `src/lib/sessao/erros.ts` e
 * `src/lib/sessao/validacao.ts` DIRETO (o Node 26 remove os tipos) e alimenta cada caso com o
 * `ProblemDetail` que o contrato publica — inclusive o 409 que o backend do E3.5 devolve de
 * verdade (`type`, `detail` e `errors[0].campo = slug`, conforme
 * `EdicaoPerfilIntegrationTest.slugDuplicado409ESlugInvalido422`).
 *
 * O que este script NÃO prova: que o servidor responde aquilo (isso é do backend e do card de
 * QA) e que a tela PINTA o estado (isso é do navegador). Prova que, dado o que o contrato e o
 * backend dizem, a decisão do cliente é a certa — e que o caminho antigo do 409 (mensagem de
 * cadastro) não vaza para a edição de perfil.
 *
 * Uso: node scripts/check-perfil-erros.mjs
 */
import { mapearFalhaDeEnvio, desfechoDeCamposInvalidos } from '../src/lib/sessao/erros.ts';
import {
  listaLinhaPorLinha,
  mensagemDeSlug,
  textoDeLista,
  validarEdicaoPerfil,
} from '../src/lib/sessao/validacao.ts';

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

const http = (status, problem) => ({ motivo: 'http', status, problem });

/** Corpo de erro REAL do backend para slug de outra conta (E3.5). */
const CONFLITO_DE_SLUG = problema('conflito_de_unicidade', 409, {
  detail: 'Este identificador público já está em uso.',
  instance: '/api/perfil/atual',
  errors: [{ campo: 'slug', mensagem: 'Este identificador público já está em uso.' }],
});

/** Corpo de erro REAL do backend para slug fora do formato (E3.5). */
const SLUG_FORA_DO_PADRAO = problema('validacao_campo', 422, {
  detail: 'Um ou mais campos são inválidos.',
  errors: [{ campo: 'slug', mensagem: 'deve ser um slug' }],
});

console.log('== check:perfil-erros — falha do contrato → estado do §15.3 na edição do perfil ==');
console.log('fonte: contracts/openapi.yaml v0.4.0 (ProblemDetail, PUT /perfil/atual) +');
console.log('       payloads do backend do card E3.5 (EdicaoPerfilIntegrationTest)\n');

console.log('1. 409 — slug já usado por outro perfil (errors[campo=slug] do backend)');
{
  const desfecho = mapearFalhaDeEnvio(http(409, CONFLITO_DE_SLUG), 'editar-perfil');
  verificar('estado = campo inválido', desfecho.estado === 'invalid-field', desfecho.estado);
  verificar('classe = conflito_de_unicidade', desfecho.classe === 'conflito_de_unicidade', desfecho.classe);
  verificar(
    'a mensagem do servidor cai no CAMPO slug (errors[] aponta o culpado)',
    desfecho.campos.slug === 'Este identificador público já está em uso.',
    JSON.stringify(desfecho.campos),
  );
  verificar('o foco do resumo vai para o campo slug', desfecho.campoFoco === 'slug', desfecho.campoFoco);
  verificar(
    'a mensagem do resumo fala de ALTERAÇÃO, não de cadastro',
    /altera/i.test(desfecho.mensagem) && !/conta não foi criada/i.test(desfecho.mensagem),
    desfecho.mensagem,
  );
}

console.log('\n2. 409 sem errors[] — o campo padrão da operação é `slug`, nunca `email`');
{
  const semCampos = problema('conflito_de_unicidade', 409, { detail: 'Identificador em uso.' });
  const desfecho = mapearFalhaDeEnvio(http(409, semCampos), 'editar-perfil');
  verificar('o campo apontado é slug', desfecho.campoFoco === 'slug', String(desfecho.campoFoco));
  verificar('nenhum campo `email` é inventado no formulário', desfecho.campos.email === undefined);
}

console.log('\n3. 409 no CADASTRO continua com o texto de conta (regressão do E3.6)');
{
  const desfecho = mapearFalhaDeEnvio(
    http(409, problema('conflito_de_unicidade', 409, { detail: 'E-mail já cadastrado.' })),
    'criar-conta',
  );
  verificar('o campo padrão segue email', desfecho.campoFoco === 'email', String(desfecho.campoFoco));
  verificar(
    'a mensagem continua sendo a de conta não criada',
    /conta não foi criada/i.test(desfecho.mensagem),
    desfecho.mensagem,
  );
}

console.log('\n4. 422 — campo inválido pelo servidor (formato do slug)');
{
  const desfecho = mapearFalhaDeEnvio(http(422, SLUG_FORA_DO_PADRAO), 'editar-perfil');
  verificar('estado = campo inválido', desfecho.estado === 'invalid-field', desfecho.estado);
  verificar(
    'a mensagem do campo é a do servidor',
    desfecho.campos.slug === 'deve ser um slug',
    JSON.stringify(desfecho.campos),
  );
  verificar('âncora no primeiro campo culpado', desfecho.campoFoco === 'slug', desfecho.campoFoco);
}

console.log('\n5. 422 — campo fora do formulário não desaparece (vai para o resumo)');
{
  const desfecho = mapearFalhaDeEnvio(
    http(422, problema('validacao_campo', 422, { errors: [{ campo: 'contaId', mensagem: 'não aceito' }] })),
    'editar-perfil',
  );
  verificar('a mensagem do campo desconhecido entra no resumo', /contaId/.test(desfecho.mensagem), desfecho.mensagem);
  verificar('e é listada em foraDosCampos', desfecho.foraDosCampos.length === 1, JSON.stringify(desfecho.foraDosCampos));
}

console.log('\n6. 401 — a sessão acabou entre carregar a página e salvar (§15.3 sessão expirada)');
{
  const desfecho = mapearFalhaDeEnvio(
    http(401, problema('nao_autenticado', 401, { detail: 'Sessão inválida.' })),
    'editar-perfil',
  );
  verificar('estado = falha recuperável', desfecho.estado === 'recoverable-failure', desfecho.estado);
  verificar('marcado como sessão encerrada (a tela oferece entrar de novo)', desfecho.sessaoEncerrada === true);
  verificar('a mensagem diz que o destino é preservado', /preservad/i.test(desfecho.mensagem), desfecho.mensagem);
}

console.log('\n7. 403 — escrita recusada (CSRF ausente ou conta suspensa) = não autorizado');
{
  const desfecho = mapearFalhaDeEnvio(
    http(403, problema('acesso_negado', 403, { detail: 'A conta não tem permissão para esta operação.' })),
    'editar-perfil',
  );
  verificar('estado = falha recuperável', desfecho.estado === 'recoverable-failure', desfecho.estado);
  verificar('marcado como acesso negado', desfecho.acessoNegado === true);
  verificar(
    'o detail do contrato é exibido (RNF004)',
    desfecho.mensagem.includes('não tem permissão'),
    desfecho.mensagem,
  );
}

console.log('\n8. outros status: 400, 415, 500 e queda de rede — sempre falha recuperável');
{
  const casos = [
    ['400 validacao', http(400, problema('validacao', 400))],
    ['415 tipo_nao_suportado', http(415, problema('tipo_nao_suportado', 415))],
    ['500 interno', http(500, problema('interno', 500))],
    ['502 do proxy (API fora)', http(502, { type: 'urn:scipath:erro:interno', title: 'x', status: 502 })],
    ['sem resposta', { motivo: 'sem-resposta' }],
    ['corpo inválido', { motivo: 'corpo-invalido' }],
  ];
  for (const [nome, falha] of casos) {
    const desfecho = mapearFalhaDeEnvio(falha, 'editar-perfil');
    verificar(`${nome} → falha recuperável`, desfecho.estado === 'recoverable-failure', desfecho.estado);
  }
}

console.log('\n9. validação LOCAL — os limites que o contrato publica em EdicaoPerfil');
{
  const base = {
    nomePublico: 'Maria Silva',
    slug: 'maria-silva',
    biografia: '',
    afiliacaoDeclarada: '',
    areasInteresse: [],
    linksExternos: [],
  };
  verificar('opcionais vazios não geram erro (RF011: podem ficar vazios)', Object.keys(validarEdicaoPerfil(base)).length === 0);

  verificar('nome vazio é recusado', validarEdicaoPerfil({ ...base, nomePublico: '   ' }).nomePublico !== undefined);
  verificar(
    'nome acima de 120 é recusado',
    validarEdicaoPerfil({ ...base, nomePublico: 'a'.repeat(121) }).nomePublico !== undefined,
  );
  verificar('nome de 120 passa', validarEdicaoPerfil({ ...base, nomePublico: 'a'.repeat(120) }).nomePublico === undefined);

  verificar('slug vazio é recusado', mensagemDeSlug('') !== undefined);
  verificar('slug de 2 caracteres é recusado (minLength 3)', mensagemDeSlug('ab') !== undefined);
  verificar('slug de 3 caracteres passa', mensagemDeSlug('abc') === undefined);
  verificar('slug com maiúscula é recusado (pattern)', mensagemDeSlug('Maria-Silva') !== undefined);
  verificar('slug com hífen duplo é recusado (pattern)', mensagemDeSlug('maria--silva') !== undefined);
  verificar('slug com underscore é recusado (pattern)', mensagemDeSlug('maria_silva') !== undefined);
  verificar('slug acima de 120 é recusado', mensagemDeSlug('a'.repeat(121)) !== undefined);
  verificar('slug válido passa', mensagemDeSlug('maria-silva-2') === undefined);
  verificar('slug é validado dentro do formulário', validarEdicaoPerfil({ ...base, slug: 'Maria Silva' }).slug !== undefined);

  verificar('biografia de 600 passa', validarEdicaoPerfil({ ...base, biografia: 'b'.repeat(600) }).biografia === undefined);
  verificar('biografia de 601 é recusada', validarEdicaoPerfil({ ...base, biografia: 'b'.repeat(601) }).biografia !== undefined);
  verificar(
    'afiliação de 300 passa e a de 301 é recusada',
    validarEdicaoPerfil({ ...base, afiliacaoDeclarada: 'a'.repeat(300) }).afiliacaoDeclarada === undefined &&
      validarEdicaoPerfil({ ...base, afiliacaoDeclarada: 'a'.repeat(301) }).afiliacaoDeclarada !== undefined,
  );
  verificar(
    '20 áreas passam e 21 são recusadas (maxItems 20)',
    validarEdicaoPerfil({ ...base, areasInteresse: Array.from({ length: 20 }, (_, i) => `area-${i}`) }).areasInteresse ===
      undefined &&
      validarEdicaoPerfil({ ...base, areasInteresse: Array.from({ length: 21 }, (_, i) => `area-${i}`) }).areasInteresse !==
        undefined,
  );
  verificar(
    'área de 60 passa e a de 61 é recusada (maxLength 60)',
    validarEdicaoPerfil({ ...base, areasInteresse: ['a'.repeat(60)] }).areasInteresse === undefined &&
      validarEdicaoPerfil({ ...base, areasInteresse: ['a'.repeat(61)] }).areasInteresse !== undefined,
  );
  const link = (n) => `https://e.x/${'a'.repeat(n - 'https://e.x/'.length)}`;
  verificar(
    `link de 300 (${link(300).length}) passa e o de 301 (${link(301).length}) é recusado (maxLength 300)`,
    validarEdicaoPerfil({ ...base, linksExternos: [link(300)] }).linksExternos === undefined &&
      validarEdicaoPerfil({ ...base, linksExternos: [link(301)] }).linksExternos !== undefined,
  );
  verificar(
    'erros de vários campos voltam juntos (nome + slug)',
    Object.keys(validarEdicaoPerfil({ ...base, nomePublico: '', slug: 'A' })).length === 2,
  );
}

console.log('\n10. listas em texto — uma por linha, sem item em branco virando área de interesse');
{
  verificar(
    'linha vazia e espaços nas pontas não viram item',
    JSON.stringify(listaLinhaPorLinha('algoritmos\n\n  estruturas de dados  \n')) ===
      JSON.stringify(['algoritmos', 'estruturas de dados']),
    JSON.stringify(listaLinhaPorLinha('algoritmos\n\n  estruturas de dados  \n')),
  );
  verificar('texto vazio vira lista vazia', listaLinhaPorLinha('').length === 0);
  verificar('ida e volta preserva a ordem', textoDeLista(['a', 'b']) === 'a\nb');
}

console.log('\n11. desfecho local = campo inválido com âncora no primeiro campo');
{
  const desfecho = desfechoDeCamposInvalidos({ slug: 'x', nomePublico: 'y' });
  verificar('estado = campo inválido', desfecho.estado === 'invalid-field', desfecho.estado);
  verificar('âncora no primeiro campo com problema', desfecho.campoFoco === 'slug', String(desfecho.campoFoco));
}

console.log(`\nresumo: ${total} verificações · ${falhas === 0 ? 'todas passaram' : `${falhas} falharam`}`);
process.exit(falhas === 0 ? 0 : 1);
