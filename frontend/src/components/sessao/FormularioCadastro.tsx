'use client';

import Link from 'next/link';
import { useState } from 'react';
import { SubmitButton } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { FormState } from '@/components/ui/FormState';
import type { Desfecho } from '@/lib/sessao/erros';
import { desfechoDeCamposInvalidos } from '@/lib/sessao/erros';
import { aplicarSessao } from '@/lib/sessao/estado';
import { criarConta, desfechoDeContaCriada } from '@/lib/sessao/navegador';
import { validarCadastro, type ErrosDeCampo } from '@/lib/sessao/validacao';
import { JaAutenticado } from './JaAutenticado';
import { useEnvioUnico } from './useEnvioUnico';
import { useSessao } from './useSessao';

/**
 * Formulário de cadastro (RF008, CA004) — POST /api/contas pelo proxy da mesma origem.
 *
 * O que este formulário promete, e o que ele NÃO promete:
 *   - a conta nasce `PENDENTE_VERIFICACAO` e a sessão JÁ fica aberta (o contrato devolve
 *     201 com o estado de sessão e o `Set-Cookie`): a pessoa entra sem passar pelo login
 *     (DRS §7.2, item 4);
 *   - publicar exige e-mail confirmado (ADR-0003 (b)) — e o sucesso DIZ isso em vez de
 *     deixar a pessoa descobrir na hora de publicar (ADR-0003 (h)). O aviso persistente
 *     do site (`<AvisoDeVerificacao>`) aparece a partir daí, com o reenvio;
 *   - navegar e editar o perfil continuam liberados: nada aqui bloqueia a navegação
 *     pública (§7.2, item 5).
 *
 * `noValidate` + `required` nos campos, pelo mesmo motivo do formulário de login: a
 * mensagem que a pessoa lê é a do projeto, associada ao controle (CA009/RNF006), e não a
 * bolha nativa do navegador. A autoridade continua sendo o servidor — 409 (e-mail já
 * cadastrado) e 422 (campo inválido) chegam dele e caem no campo certo.
 *
 * A confirmação de senha é regra DESTE formulário (o contrato não a recebe): ela existe
 * para o erro de digitação não virar uma senha errada que a pessoa não conhece.
 *
 * Sessão já aberta ⇒ sem formulário: entra o bloco `JaAutenticado`, pelo mesmo motivo do
 * login (duas telas de entrada não podem discordar sobre quem está autenticado).
 */
export function FormularioCadastro() {
  const [nomePublico, setNomePublico] = useState('');
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [senhaConfirmacao, setSenhaConfirmacao] = useState('');
  const [errosLocais, setErrosLocais] = useState<ErrosDeCampo>({});
  const [errosDoServidor, setErrosDoServidor] = useState<ErrosDeCampo>({});
  const envio = useEnvioUnico(enviar);
  const sessao = useSessao();

  async function enviar(): Promise<Desfecho> {
    const locais = validarCadastro({ email, senha, senhaConfirmacao, nomePublico });
    if (Object.keys(locais).length > 0) {
      setErrosLocais(locais);
      setErrosDoServidor({});
      return desfechoDeCamposInvalidos(locais);
    }
    setErrosLocais({});

    const resultado = await criarConta({ email: email.trim(), senha, nomePublico: nomePublico.trim() });
    if (!resultado.ok) {
      setErrosDoServidor(resultado.desfecho.campos);
      return resultado.desfecho;
    }

    aplicarSessao(resultado.dados);
    setErrosDoServidor({});
    // As duas senhas saem do formulário: a operação terminou e o segredo não fica na tela.
    setSenha('');
    setSenhaConfirmacao('');
    return desfechoDeContaCriada(resultado.dados.perfil?.nomePublico ?? nomePublico.trim());
  }

  const desfecho = envio.desfecho;
  const sucesso = desfecho?.estado === 'success';

  if (sessao.situacao === 'autenticado' && !sucesso) {
    return <JaAutenticado />;
  }

  const erroDe = (campo: string) => errosLocais[campo] ?? errosDoServidor[campo];

  return (
    <section className="sc-card sc-card--pad" aria-labelledby="cadastro-titulo">
      <h2 className="sc-subhead mt-0" id="cadastro-titulo">
        Criar conta
      </h2>
      <p className="sc-body sc-measure">
        A conta é o que liga o seu nome ao material que você publica. Peça o mínimo: nome público,
        e-mail e senha. Nada além disso é exigido para começar.
      </p>

      {sucesso ? (
        <div className="mt-4">
          <FormState
            id="cadastro-resumo"
            kind="success"
            title={desfecho?.titulo}
            message={desfecho?.mensagem}
            action={
              <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/explorar">
                Explorar materiais
              </Link>
            }
          />
        </div>
      ) : (
        <form
          className="space-y-4 mt-4"
          method="post"
          noValidate
          onSubmit={envio.submeter}
          aria-busy={envio.pendente}
          aria-describedby={envio.estado !== 'inicial' ? 'cadastro-resumo' : undefined}
        >
          {envio.estado !== 'inicial' ? (
            <FormState
              id="cadastro-resumo"
              kind={envio.estado === 'submitting' ? 'submitting' : envio.estado}
              title={desfecho?.titulo}
              message={desfecho?.mensagem}
              fieldId={desfecho?.campoFoco}
              action={
                // 409 é o único caso em que o cadastro revela que o e-mail já tem conta (o
                // contrato só faz isso aqui, porque o formulário precisa ser útil) — e o
                // caminho útil, então, é ir para o login.
                desfecho?.classe === 'conflito_de_unicidade' ? (
                  <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/entrar">
                    Entrar com este e-mail
                  </Link>
                ) : null
              }
            />
          ) : null}

          <TextField
            id="cadastro-nome"
            name="nomePublico"
            label="Nome público"
            autoComplete="name"
            maxLength={120}
            required
            value={nomePublico}
            onChange={(evento) => setNomePublico(evento.target.value)}
            error={erroDe('nomePublico')}
            hint="Como você quer aparecer como autor. Pode ser o seu nome ou um apelido estável."
          />

          <TextField
            id="cadastro-email"
            name="email"
            label="E-mail"
            type="email"
            autoComplete="email"
            inputMode="email"
            maxLength={254}
            required
            value={email}
            onChange={(evento) => setEmail(evento.target.value)}
            error={erroDe('email')}
            hint="É por ele que você entra e por onde chega o link de confirmação."
          />

          <TextField
            id="cadastro-senha"
            name="senha"
            label="Senha"
            type="password"
            autoComplete="new-password"
            minLength={8}
            maxLength={72}
            required
            value={senha}
            onChange={(evento) => setSenha(evento.target.value)}
            error={erroDe('senha')}
            hint="De 8 a 72 caracteres e até 72 bytes em UTF-8 (limite do bcrypt). A plataforma guarda só o hash da senha, nunca o texto."
          />

          <TextField
            id="cadastro-senha-confirmacao"
            name="senhaConfirmacao"
            label="Repita a senha"
            type="password"
            autoComplete="new-password"
            required
            value={senhaConfirmacao}
            onChange={(evento) => setSenhaConfirmacao(evento.target.value)}
            error={erroDe('senhaConfirmacao')}
            hint="Serve para pegar erro de digitação antes de a conta existir."
          />

          <SubmitButton pending={envio.pendente} variant="primary">
            Criar conta e entrar
          </SubmitButton>
        </form>
      )}

      <p className="sc-helper mt-4">
        Já tem conta? <Link href="/entrar">Entrar</Link>.
      </p>
    </section>
  );
}
