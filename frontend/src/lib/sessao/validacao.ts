/**
 * Validação de CLIENTE dos formulários de acesso — os mesmos limites que o contrato
 * publica (`CadastroConta`, `Credenciais`), para o erro chegar antes do envio quando é
 * possível saber sozinho: e-mail sem `@`, senha de 7 caracteres, confirmação diferente.
 *
 * O que isto NÃO é: a autoridade. A validação que vale é a do servidor (422
 * `validacao_campo`), e o formulário trata a resposta dele do mesmo jeito. Se um dia o
 * contrato mudar um limite, este arquivo fica desatualizado em silêncio — por isso ele
 * cita o schema de origem e por isso há `scripts/check-erros-sessao.mjs` exercitando os
 * dois lados (cliente recusando, servidor recusando).
 *
 * Módulo puro (sem `import` de runtime): o script de verificação o carrega direto no Node.
 */

export type ErrosDeCampo = Record<string, string>;

/** `CadastroConta.email`/`Credenciais.email`: format email, maxLength 254. */
const TAMANHO_MAXIMO_EMAIL = 254;
/** `CadastroConta.nomePublico`: minLength 1, maxLength 120. */
const TAMANHO_MAXIMO_NOME = 120;
/** `CadastroConta.senha`: minLength 8 e maxLength 72 (o teto vem do bcrypt — ADR-0003 (a)). */
const SENHA_MINIMA = 8;
const SENHA_MAXIMA = 72;

/**
 * Formato de e-mail: a checagem é deliberadamente simples — o que ela pega é o dedo
 * escorregando (`maria.silva`, `maria@`), que é o caso comum. Validação de e-mail por
 * expressão regular é assunto conhecido por não dar para fazer bem; o servidor tem a
 * palavra final e a mensagem dele também aparece no campo.
 */
export function emailPareceValido(email: string): boolean {
  const semEspacos = email.trim();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(semEspacos) && semEspacos.length <= TAMANHO_MAXIMO_EMAIL;
}

function validarEmail(email: string): string | undefined {
  if (email.trim() === '') return 'Informe o e-mail da conta — é com ele que você entra.';
  if (email.trim().length > TAMANHO_MAXIMO_EMAIL)
    return `O e-mail passa de ${TAMANHO_MAXIMO_EMAIL} caracteres. Use um endereço mais curto.`;
  if (!emailPareceValido(email))
    return 'Confira o endereço: falta o @ ou o domínio (exemplo: nome@instituicao.br).';
  return undefined;
}

function validarSenha(senha: string, tarefa: 'entrar' | 'criar'): string | undefined {
  if (senha === '') return 'Informe a sua senha.';
  if (tarefa === 'criar') {
    if (senha.length < SENHA_MINIMA)
      return `A senha precisa de ${SENHA_MINIMA} caracteres no mínimo.`;
    if (senha.length > SENHA_MAXIMA)
      return `A senha não pode passar de ${SENHA_MAXIMA} caracteres.`;
  }
  return undefined;
}

/** Regras de `Credenciais` — login. */
export function validarCredenciais(valores: { email: string; senha: string }): ErrosDeCampo {
  const erros: ErrosDeCampo = {};
  const email = validarEmail(valores.email);
  const senha = validarSenha(valores.senha, 'entrar');
  if (email) erros.email = email;
  if (senha) erros.senha = senha;
  return erros;
}

/** Regras de `CadastroConta` — cadastro (a confirmação de senha é regra de formulário). */
export function validarCadastro(valores: {
  email: string;
  senha: string;
  senhaConfirmacao: string;
  nomePublico: string;
}): ErrosDeCampo {
  const erros: ErrosDeCampo = {};

  const email = validarEmail(valores.email);
  if (email) erros.email = email;

  // A senha do cadastro é regra NOVA, não a do login: 8 a 72 caracteres (CadastroConta).
  const senha = validarSenha(valores.senha, 'criar');
  if (senha) erros.senha = senha;

  const nome = valores.nomePublico.trim();
  if (nome === '') {
    erros.nomePublico = 'Informe como você quer aparecer como autor dos seus materiais.';
  } else if (nome.length > TAMANHO_MAXIMO_NOME) {
    erros.nomePublico = `O nome público não pode passar de ${TAMANHO_MAXIMO_NOME} caracteres.`;
  }

  if (valores.senhaConfirmacao === '') {
    erros.senhaConfirmacao = 'Repita a senha para confirmar que ela está correta.';
  } else if (valores.senha !== valores.senhaConfirmacao) {
    erros.senhaConfirmacao = 'As duas senhas precisam ser iguais.';
  }

  return erros;
}

/* ---------------------------------------------------------------------------
   Edição do próprio perfil (RF011) — card E3.7.

   Os limites abaixo são os que o contrato PUBLICA no schema `EdicaoPerfil`
   (`contracts/openapi.yaml` v0.4.0): `nomePublico` 1–120, `slug`
   `^[a-z0-9]+(-[a-z0-9]+)*$` com 3–120, `biografia` até 600,
   `afiliacaoDeclarada` até 300, `areasInteresse` até 20 itens de 60 e
   `linksExternos` até 20 itens de 300. O servidor valida de novo — esta função
   antecipa o erro, não substitui a autoridade dele.

   O que NÃO está aqui, de propósito: a lista de slugs RESERVADOS. O backend a
   tem (E3.5 — `SlugsReservados`) e responde 422 com o campo `slug` apontado,
   mas o contrato não a publica; repeti-la no cliente seria inventar uma regra
   a partir do código do servidor e mantê-la em dia por conta própria. O caso
   chega pelo 422 e cai no mesmo campo.
   --------------------------------------------------------------------------- */

/** `EdicaoPerfil.slug` — mesmo `pattern` do contrato (chave estável da URL `/u/{slug}`). */
export const SLUG_PADRAO = /^[a-z0-9]+(-[a-z0-9]+)*$/;

const SLUG_MINIMO = 3;
const SLUG_MAXIMO = 120;
/** `EdicaoPerfil.nomePublico`: minLength 1, maxLength 120. */
const NOME_MAXIMO_PERFIL = 120;
/** `EdicaoPerfil.biografia`: maxLength 600. */
const BIOGRAFIA_MAXIMA = 600;
/** `EdicaoPerfil.afiliacaoDeclarada`: maxLength 300. */
const AFILIACAO_MAXIMA = 300;
/** `EdicaoPerfil.areasInteresse`: maxItems 20, item maxLength 60. */
const AREAS_MAXIMAS = 20;
const AREA_MAXIMA = 60;
/** `EdicaoPerfil.linksExternos`: maxItems 20, item maxLength 300. */
const LINKS_MAXIMOS = 20;
const LINK_MAXIMO = 300;

/**
 * Lista em texto (uma entrada por linha) → array do contrato.
 *
 * Por que uma linha por item, e não um editor de etiquetas: o formulário tem de funcionar
 * SEM JavaScript (o HTML entregue carrega os campos) e um `<textarea>` é o controle que faz
 * isso com um controle só, sem inventar widget. Espaço em branco nas pontas e linha vazia
 * não viram item — a lista `['login', '']` criaria uma área de interesse em branco na tela
 * pública, que é pior que a lista vazia.
 */
export function listaLinhaPorLinha(bruto: string): string[] {
  return bruto
    .split('\n')
    .map((linha) => linha.trim())
    .filter((linha) => linha !== '');
}

/** Array do contrato → texto do controle (uma por linha) — o caminho de volta. */
export function textoDeLista(itens: string[]): string {
  return itens.join('\n');
}

/** `EdicaoPerfil.slug` — formato e tamanho; unicidade (409) é do servidor (RN009). */
export function mensagemDeSlug(slug: string): string | undefined {
  const valor = slug.trim();
  if (valor === '') return 'Informe o identificador público do perfil — é ele que forma o endereço /u/seu-slug.';
  if (valor.length < SLUG_MINIMO)
    return `O identificador precisa de ${SLUG_MINIMO} caracteres no mínimo.`;
  if (valor.length > SLUG_MAXIMO)
    return `O identificador não pode passar de ${SLUG_MAXIMO} caracteres.`;
  if (!SLUG_PADRAO.test(valor))
    return 'Use apenas letras minúsculas, números e hífen simples entre as palavras (exemplo: maria-silva).';
  return undefined;
}

function validarLista(
  itens: string[],
  { maximo, tamanhoItem, singular, campo }: { maximo: number; tamanhoItem: number; singular: string; campo: string },
): string | undefined {
  if (itens.length > maximo) {
    return `Você informou ${itens.length} ${campo}. O limite é ${maximo} — deixe os ${maximo} mais importantes e remova os outros.`;
  }
  const grande = itens.find((item) => item.length > tamanhoItem);
  if (grande) {
    return `${singular} "${grande}" passa de ${tamanhoItem} caracteres. Encurte ou divida em duas entradas.`;
  }
  return undefined;
}

/**
 * Regras de `EdicaoPerfil` (RF011) — os campos como o formulário os tem.
 *
 * As listas chegam aqui JÁ convertidas (`listaLinhaPorLinha`), porque a validação é sobre o
 * que seria enviado ao servidor, não sobre o texto do controle.
 */
export function validarEdicaoPerfil(valores: {
  nomePublico: string;
  slug: string;
  biografia: string;
  afiliacaoDeclarada: string;
  areasInteresse: string[];
  linksExternos: string[];
}): ErrosDeCampo {
  const erros: ErrosDeCampo = {};

  const nome = valores.nomePublico.trim();
  if (nome === '') {
    erros.nomePublico = 'Informe o nome público — é ele que assina os seus materiais.';
  } else if (nome.length > NOME_MAXIMO_PERFIL) {
    erros.nomePublico = `O nome público não pode passar de ${NOME_MAXIMO_PERFIL} caracteres.`;
  }

  const slug = mensagemDeSlug(valores.slug);
  if (slug) erros.slug = slug;

  if (valores.biografia.length > BIOGRAFIA_MAXIMA) {
    erros.biografia = `A biografia não pode passar de ${BIOGRAFIA_MAXIMA} caracteres (a atual tem ${valores.biografia.length}).`;
  }
  if (valores.afiliacaoDeclarada.length > AFILIACAO_MAXIMA) {
    erros.afiliacaoDeclarada = `A afiliação declarada não pode passar de ${AFILIACAO_MAXIMA} caracteres.`;
  }

  const areas = validarLista(valores.areasInteresse, {
    maximo: AREAS_MAXIMAS,
    tamanhoItem: AREA_MAXIMA,
    singular: 'A área de interesse',
    campo: 'áreas de interesse',
  });
  if (areas) erros.areasInteresse = areas;

  const links = validarLista(valores.linksExternos, {
    maximo: LINKS_MAXIMOS,
    tamanhoItem: LINK_MAXIMO,
    singular: 'O link',
    campo: 'links',
  });
  if (links) erros.linksExternos = links;

  return erros;
}

/* ---------------------------------------------------------------------------
   Publicação de material (RF012, RF018) — card E4.6.

   Os limites abaixo são os que o contrato PUBLICA no schema `PublicacaoMaterial`
   (`contracts/openapi.yaml` v0.5.0): `titulo` 1–200, `descricao` 1–2000;
   `categoriaSlug`/`tipoSlug`/`licencaSlug` são os `slug` estáveis da taxonomia;
   `arquivo` é PDF (RF013, ADR-0005 §1) com o limite de upload configurável; e
   `declaracaoDireito` é obrigatório e precisa ser `true` (RF012).

   O servidor valida de novo — esta função antecipa o erro, não substitui a
   autoridade dele. Duas coisas NÃO estão aqui, de propósito:

   1. **a lista de categorias, tipos e licenças**: elas vêm da API (`/categorias`,
      `/tipos-material`, `/licencas`) e são exibidas como vieram. Repeti-las no
      cliente é exatamente o que o RF018 proíbe ("não codificados de forma
      divergente em várias telas") — o que se valida aqui é que uma escolha FOI
      feita, não QUAIS escolhas existem;
   2. **a validação do CONTEÚDO do PDF**: o servidor confere o arquivo por
      conteúdo (assinatura `%PDF-`), não pela extensão. O cliente olha o que dá
      para olhar sem ler o arquivo (extensão e tipo declarado) e diz que a
      palavra final é do servidor — um PDF renomeado passa aqui e é recusado lá
      com 415, e é a mensagem dele que a tela mostra.
   --------------------------------------------------------------------------- */

/** `PublicacaoMaterial.titulo`: minLength 1, maxLength 200 (é o `title` de SEO). */
const TITULO_MAXIMO = 200;
/** `PublicacaoMaterial.descricao`: minLength 1, maxLength 2000. */
const DESCRICAO_MAXIMA = 2000;

/** Limite de upload padrão do ADR-0005 §1 (30 MB), usado quando o ambiente não o define. */
export const LIMITE_PADRAO_DE_ARQUIVO_BYTES = 30 * 1024 * 1024;

/**
 * Nome da configuração do limite de upload (`SCIPATH_UPLOAD_MAX_BYTES`, ADR-0005 §1).
 *
 * É a MESMA variável que o backend lê: a tela exibe o limite corrente em vez de
 * inventar um número próprio. Quando ela não está definida ou não é um inteiro
 * positivo, vale o padrão do ADR — que é também o que o backend aplica nesse caso.
 */
export const VARIAVEL_DO_LIMITE_DE_ARQUIVO = 'SCIPATH_UPLOAD_MAX_BYTES';

/** Lê o limite de upload da configuração; valor ausente/inválido cai no padrão do ADR-0005. */
export function limiteDeArquivoEmBytes(bruto: string | undefined): number {
  if (bruto === undefined) return LIMITE_PADRAO_DE_ARQUIVO_BYTES;
  const valor = Number.parseInt(bruto, 10);
  return Number.isFinite(valor) && valor > 0 ? valor : LIMITE_PADRAO_DE_ARQUIVO_BYTES;
}

/** Tamanho em bytes → texto com uma casa quando não é múltiplo exato (`30 MB`, `1,5 MB`). */
export function formatarBytes(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  if (mb >= 1) {
    const arredondado = Math.round(mb * 10) / 10;
    return `${Number.isInteger(arredondado) ? arredondado : arredondado.toFixed(1).replace('.', ',')} MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

/**
 * O arquivo escolhido, no recorte que o cliente consegue avaliar sem lê-lo:
 * o nome (extensão) e o tamanho que o navegador já informou. `null` = nada escolhido.
 *
 * O tipo declarado (`type`) NÃO entra na decisão: ele é do sistema operacional e
 * mente com facilidade — o que vale é a extensão aqui e o CONTEÚDO no servidor.
 */
export type ArquivoEscolhido = { nome: string; tamanhoBytes: number } | null;

export type AvaliacaoDeArquivo = 'ausente' | 'formato' | 'tamanho' | 'ok';

/** Avalia o arquivo escolhido contra as regras de cliente do ADR-0005 §1. */
export function avaliarArquivo(arquivo: ArquivoEscolhido, limiteBytes: number): AvaliacaoDeArquivo {
  if (arquivo === null || arquivo.nome.trim() === '') return 'ausente';
  if (!/\.pdf$/i.test(arquivo.nome.trim())) return 'formato';
  if (arquivo.tamanhoBytes > limiteBytes) return 'tamanho';
  return 'ok';
}

/**
 * Regras de `PublicacaoMaterial` (RF012) — os campos como o formulário os tem.
 *
 * `arquivo` chega como `ArquivoEscolhido` (nada escolhido = `null`); o tamanho do
 * arquivo NÃO aparece na mensagem daqui porque quem a escreve é a tela, com o
 * texto do `<FormState>` — o campo `arquivo` só diz o que está errado.
 */
export function validarPublicacao(
  valores: {
    titulo: string;
    descricao: string;
    categoriaSlug: string;
    tipoSlug: string;
    licencaSlug: string;
    declaracaoDireito: boolean;
    arquivo: ArquivoEscolhido;
  },
  { limiteBytes }: { limiteBytes: number },
): ErrosDeCampo {
  const erros: ErrosDeCampo = {};

  const titulo = valores.titulo.trim();
  if (titulo === '') {
    erros.titulo = 'Informe o título do material — é ele que aparece na busca e no topo da página pública.';
  } else if (titulo.length > TITULO_MAXIMO) {
    erros.titulo = `O título não pode passar de ${TITULO_MAXIMO} caracteres (o atual tem ${titulo.length}).`;
  }

  const descricao = valores.descricao.trim();
  if (descricao === '') {
    erros.descricao =
      'Escreva a descrição do material: ela é o que o visitante lê antes de decidir abrir o arquivo.';
  } else if (descricao.length > DESCRICAO_MAXIMA) {
    erros.descricao = `A descrição não pode passar de ${DESCRICAO_MAXIMA} caracteres (a atual tem ${descricao.length}).`;
  }

  if (valores.categoriaSlug === '') {
    erros.categoriaSlug = 'Escolha a categoria do material. A lista é a da plataforma — não há categoria digitada à mão.';
  }
  if (valores.tipoSlug === '') {
    erros.tipoSlug = 'Escolha o tipo do material (nota de aula, slides, artigo…).';
  }
  if (valores.licencaSlug === '') {
    // Nenhuma licença é atribuída automaticamente: a escolha é do autor (RF012, RN006).
    erros.licencaSlug =
      'Escolha a licença de reutilização. Nenhuma é atribuída automaticamente: é você quem diz o que pode ser feito com o material.';
  }

  const arquivo = avaliarArquivo(valores.arquivo, limiteBytes);
  if (arquivo === 'ausente') {
    erros.arquivo = 'Escolha o arquivo PDF do material — a publicação sem arquivo não é aceita.';
  } else if (arquivo === 'formato') {
    erros.arquivo = `O arquivo "${valores.arquivo?.nome ?? ''}" não tem extensão .pdf. Envie um PDF — o servidor confere o conteúdo do arquivo, não só o nome.`;
  } else if (arquivo === 'tamanho') {
    erros.arquivo = `O arquivo tem ${formatarBytes(valores.arquivo?.tamanhoBytes ?? 0)} e o limite é ${formatarBytes(limiteBytes)}. Reduza o PDF e envie novamente.`;
  }

  if (!valores.declaracaoDireito) {
    // RN007/RF012: a declaração é obrigatória e explícita. O servidor recusa com 422 no
    // campo; aqui ela chega antes para o erro não custar uma requisição com arquivo grande.
    erros.declaracaoDireito =
      'Confirme que você tem o direito de compartilhar este material. Sem essa declaração o servidor recusa a publicação.';
  }

  return erros;
}
