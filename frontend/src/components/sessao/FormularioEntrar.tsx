'use client';

import Link from 'next/link';
import { useState } from 'react';
import { SubmitButton } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { FormState } from '@/components/ui/FormState';
import { rotaPreservada } from '@/lib/rotas';
import type { Desfecho } from '@/lib/sessao/erros';
import { desfechoDeCamposInvalidos } from '@/lib/sessao/erros';
import { aplicarSessao } from '@/lib/sessao/estado';
import { abrirSessao, desfechoDeSessaoAberta } from '@/lib/sessao/navegador';
import { validarCredenciais, type ErrosDeCampo } from '@/lib/sessao/validacao';
import { JaAutenticado } from './JaAutenticado';
import { useEnvioUnico } from './useEnvioUnico';
import { useSessao } from './useSessao';

/**
 * Formulário de login (RF009, CA004) — POST /api/sessoes pelo proxy da mesma origem.
 *
 * Decisões que estão no código e não são óbvias de fora:
 *
 * 1. **`noValidate` com `required` nos campos.** O navegador tem a sua própria validação
 *    (bolha nativa) e ela bloquearia o `onSubmit` antes de chegar aqui — o resultado seria
 *    duas apresentações de erro diferentes para o mesmo campo. A validação que a pessoa vê
 *    é a do projeto: `aria-invalid` + mensagem associada ao controle por `aria-describedby`
 *    (CA009/RNF006). O `required` continua no campo: ele diz à tecnologia assistiva que o
 *    campo é obrigatório, e a regra de verdade continua sendo a do servidor.
 * 2. **A resposta 401 não aponta campo.** O contrato devolve o MESMO corpo para e-mail
 *    inexistente e senha errada (RNF013) e o `detail` não diz qual falhou; marcar um campo
 *    seria inventar o que o servidor não disse. A mensagem fica no resumo.
 * 3. **Sem redirecionamento automático.** O sucesso é mostrado no lugar do formulário, com
 *    o caminho de volta para onde a pessoa queria ir (`?proximo=`, preservado por
 *    `Entrar?proximo=` quando a página anterior recebeu 401 — §15.3). Levar direto embora
 *    esconderia o estado de sucesso e deixaria a pessoa sem saber o que aconteceu.
 * 4. **Uma coisa de cada vez na tela.** Sessão aberta ⇒ o formulário sai e entra o bloco
 *    "você já está autenticado" (`JaAutenticado`, estado de página `sucesso`). Só quando o
 *    sucesso veio de um envio desta própria tela é que o desfecho do envio fica no lugar
 *    (estado de formulário `sucesso`): dois blocos dizendo "você entrou" seria ruído.
 * 5. **A senha sai do formulário depois do sucesso.** Não é enfeite: manter o segredo
 *    digitado numa tela que não vai mais ser enviada é higiene (RNF009).
 */
export function FormularioEntrar({ proximo }: { proximo?: string }) {
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [errosLocais, setErrosLocais] = useState<ErrosDeCampo>({});
  const [errosDoServidor, setErrosDoServidor] = useState<ErrosDeCampo>({});
  const envio = useEnvioUnico(enviar);
  const sessao = useSessao();

  async function enviar(): Promise<Desfecho> {
    const locais = validarCredenciais({ email, senha });
    if (Object.keys(locais).length > 0) {
      setErrosLocais(locais);
      setErrosDoServidor({});
      return desfechoDeCamposInvalidos(locais);
    }
    setErrosLocais({});

    const resultado = await abrirSessao({ email: email.trim(), senha });
    if (!resultado.ok) {
      setErrosDoServidor(resultado.desfecho.campos);
      return resultado.desfecho;
    }

    aplicarSessao(resultado.dados);
    setErrosDoServidor({});
    setSenha('');
    return desfechoDeSessaoAberta(
      resultado.dados.perfil?.nomePublico ?? '',
      resultado.dados.estadoConta === 'ATIVO',
    );
  }

  const desfecho = envio.desfecho;
  const sucesso = desfecho?.estado === 'success';

  if (sessao.situacao === 'autenticado' && !sucesso) {
    return <JaAutenticado {...(proximo ? { proximo } : {})} />;
  }

  const erroDe = (campo: string) => errosLocais[campo] ?? errosDoServidor[campo];

  return (
    <section className="sc-card sc-card--pad" aria-labelledby="entrar-titulo">
      <h2 className="sc-subhead mt-0" id="entrar-titulo">
        Entrar na sua conta
      </h2>
      <p className="sc-body sc-measure">
        Use o e-mail e a senha do cadastro. A sessão fica guardada numa cookie do próprio site —
        nada de token no endereço ou no armazenamento do navegador.
      </p>

      {sucesso ? (
        <div className="mt-4">
          <FormState
            id="entrar-resumo"
            kind="success"
            title={desfecho?.titulo}
            message={desfecho?.mensagem}
            action={
              <Link
                className="sc-btn sc-btn--primary sc-btn--sm"
                href={rotaPreservada(proximo ?? '/explorar')}
              >
                {proximo ? 'Continuar de onde parei' : 'Explorar materiais'}
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
          aria-describedby={envio.estado !== 'inicial' ? 'entrar-resumo' : undefined}
        >
          {envio.estado !== 'inicial' ? (
            <FormState
              id="entrar-resumo"
              kind={envio.estado === 'submitting' ? 'submitting' : envio.estado}
              title={desfecho?.titulo}
              message={desfecho?.mensagem}
              fieldId={desfecho?.campoFoco}
            />
          ) : null}

          <TextField
            id="entrar-email"
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
            hint="O mesmo e-mail usado no cadastro."
          />

          <TextField
            id="entrar-senha"
            name="senha"
            label="Senha"
            type="password"
            autoComplete="current-password"
            required
            value={senha}
            onChange={(evento) => setSenha(evento.target.value)}
            error={erroDe('senha')}
            hint="A senha não aparece na tela nem é guardada no navegador."
          />

          <SubmitButton pending={envio.pendente} variant="primary">
            Entrar
          </SubmitButton>
        </form>
      )}

      <p className="sc-helper mt-4">
        Ainda não tem conta? <Link href="/cadastro">Criar conta leva um minuto</Link> — e você já sai
        autenticado.
      </p>
    </section>
  );
}
