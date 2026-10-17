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
