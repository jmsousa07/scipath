import { Chip } from '@/components/ui/Chip';
import type { PerfilPublico } from '@/lib/api/tipos';

/**
 * Prévia do perfil público (RF011, PAGE_BLUEPRINTS.md §10 — "pré-visualização de como o perfil
 * público aparece").
 *
 * Por que existe: quem edita o próprio perfil precisa ver o RESULTADO do que escreveu antes de
 * salvar — nome, bio, afiliação, áreas e links são texto que outra pessoa vai ler. A prévia
 * mostra exatamente os mesmos rótulos e o mesmo desenho da página pública `/u/{slug}`
 * (card E2.10), de propósito: prévia que diverge da tela real informa errado.
 *
 * Componente SEM estado e sem `'use client'`: ele é renderizado no servidor (o HTML entregue
 * carrega a prévia com os valores atuais) e reusado dentro do formulário, que o atualiza a
 * cada tecla enquanto a pessoa edita. Nada aqui decide permissão — quem recusa leitura ou
 * escrita sem sessão é o servidor (RNF010).
 *
 * RN011 / CA012, o ponto que não pode escapar: **instituição é autodeclaração**. O rótulo é
 * "Afiliação declarada", a frase é atribuída a quem a escreveu ("Declarado por …") e o rodapé
 * diz que a plataforma não verifica vínculo institucional. Não existe — e não é omitido, não
 * existe mesmo — selo, medalha, marca de "verificado" ou nota de instituição em lugar nenhum
 * desta tela: o contrato não tem esse campo (v0.4.0, `PerfilPublico`), e a tela não inventa um.
 */
export function PreviaDoPerfil({
  perfil,
  publicamenteVisivel,
}: {
  perfil: PerfilPublico;
  /**
   * `true` quando a conta está `ATIVO` (e-mail confirmado). `/u/{slug}` só responde para conta
   * ativa (ADR-0003 (b)): a prévia é honesta sobre isso em vez de prometer uma página que
   * ainda responde 404.
   */
  publicamenteVisivel: boolean;
}) {
  const {
    nomePublico,
    slug,
    biografia = '',
    afiliacaoDeclarada = '',
    areasInteresse,
    linksExternos,
  } = perfil;

  return (
    <section className="sc-card sc-card--pad" aria-labelledby="previa-titulo" data-previa="perfil">
      <h2 className="sc-subhead mt-0" id="previa-titulo">
        Como o seu perfil público aparece
      </h2>
      <p className="sc-helper">
        Esta é a prévia do que outras pessoas leem quando abrem o seu perfil. Ela acompanha o que
        você digita no formulário — nada é publicado por aqui além do que você salvar.
      </p>

      <h3 className="sc-h1 mt-6">{nomePublico.trim() === '' ? 'O seu nome público' : nomePublico}</h3>

      {biografia.trim() === '' ? (
        <p className="sc-helper">
          Sem biografia: a página pública mostra a identidade e diz que esta pessoa não escreveu
          uma biografia. Não existe texto genérico de preenchimento.
        </p>
      ) : (
        <p className="sc-sec__lead">{biografia}</p>
      )}

      <dl className="mt-8 grid gap-5 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <dt className="sc-caption">Afiliação declarada</dt>
          <dd className="sc-body m-0">
            {afiliacaoDeclarada.trim() === '' ? (
              <>Sem instituição informada — nenhuma é inferida ou preenchida pela plataforma.</>
            ) : (
              <>
                <span className="sc-mono">Declarado por {nomePublico}:</span> {afiliacaoDeclarada}
              </>
            )}
          </dd>
        </div>

        <div>
          <dt className="sc-caption">Áreas de interesse</dt>
          <dd className="sc-body m-0">
            {areasInteresse.length === 0 ? (
              <>Nenhuma área informada.</>
            ) : (
              <ul className="m-0 flex list-none flex-wrap gap-2 p-0">
                {areasInteresse.map((area) => (
                  <li key={area}>
                    <Chip dot={false}>{area}</Chip>
                  </li>
                ))}
              </ul>
            )}
          </dd>
        </div>

        <div>
          <dt className="sc-caption">Links externos</dt>
          <dd className="sc-body m-0">
            {linksExternos.length === 0 ? (
              <>Nenhum link informado.</>
            ) : (
              <ul className="m-0 list-none space-y-1 p-0">
                {linksExternos.map((link) => (
                  <li key={link}>
                    {ehEnderecoWeb(link) ? (
                      <a href={link} target="_blank" rel="noopener noreferrer nofollow">
                        {link}
                      </a>
                    ) : (
                      // Texto livre que não é endereço web: exibido COMO TEXTO, como na página
                      // pública. Adivinhar o protocolo de um valor digitado pela pessoa criaria
                      // link `mailto:`/`javascript:` por conta própria.
                      <span>{link}</span>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </dd>
        </div>

        <div className="sm:col-span-2">
          <dt className="sc-caption">Endereço estável</dt>
          <dd className="sc-body m-0">
            <span className="sc-mono">/u/{slug.trim() === '' ? 'seu-slug' : slug}</span>
            {publicamenteVisivel ? (
              <>
                {' '}
                — aberto a visitantes, sem sessão.
              </>
            ) : (
              <>
                {' '}
                — este endereço passa a responder a visitantes quando a confirmação do e-mail for
                concluída (a leitura pública exige conta com e-mail confirmado).
              </>
            )}
          </dd>
        </div>
      </dl>

      <p className="sc-helper sc-measure mt-6" data-aviso="afiliacao-autodeclarada">
        A instituição e a afiliação informadas aqui são <strong>autodeclarações de quem escreve o
        perfil</strong>: a plataforma não verifica vínculo institucional, titulação ou identidade
        acadêmica, e não existe selo, medalha ou marca de &ldquo;verificado&rdquo; em nenhuma tela
        do SciPath. Os links também são texto declarado, sem checagem no MVP.
      </p>
    </section>
  );
}

/** Só `http` e `https` viram link — o resto é texto, sem adivinhação de protocolo. */
function ehEnderecoWeb(valor: string): boolean {
  return /^https?:\/\/\S+$/i.test(valor.trim());
}
