import Link from 'next/link';
import { Card } from '@/components/ui/Card';
import { dataCurta } from '@/lib/format';
import { rotaAutor, rotaCategoria, rotaMaterial } from '@/lib/rotas';
import type { MaterialResumo } from '@/lib/api/tipos';

/**
 * Card de material da descoberta pública (RF002).
 *
 * Mostra o que o contrato garante em cada item e nada além: título, descrição, autoria,
 * categoria, tipo, licença EXPLICADA (`descricaoCurta`, RN006) e data de publicação.
 * Nenhuma marca de revisão, certificação, curadoria ou verificação (RN010, RN011,
 * CA012) — nada disso existe no dado, então não pode existir na tela.
 *
 * Reuso: este é o card do E2.8 e é o mesmo componente que o E2.10 usa nas rotas de
 * perfil e de categoria (não duplicar tela). Por isso ele só depende do `MaterialResumo`
 * do contrato, e não do estado da URL de `/explorar`.
 *
 * Categoria e autoria são LINKS (RF007): o card é o ponto onde a navegação material →
 * categoria e material → autor começa. A categoria aponta para `/categoria/[slug]` — a
 * página que dá contexto (o card E2.10) —, e não para `/explorar?categoria=`, que é a
 * listagem filtrada: as duas existem, com propósitos diferentes, e a que explica o que é
 * a categoria é esta.
 */
export function CardMaterial({ material }: { material: MaterialResumo }) {
  const { slug, titulo, descricao, autor, categoria, tipo, licenca, publicadoEm, atualizadoEm } =
    material;

  return (
    <Card
      as="article"
      eyebrow={tipo.nome}
      title={
        <Link href={rotaMaterial(slug)}>
          {titulo}
        </Link>
      }
      copy={descricao}
    >
      <div className="sc-card__meta">
        <p className="sc-helper">
          Categoria:{' '}
          <Link href={rotaCategoria(categoria.slug)}>{categoria.nome}</Link> · Autoria:{' '}
          <Link href={rotaAutor(autor.slug)}>{autor.nomePublico}</Link> · Publicado em{' '}
          <time dateTime={publicadoEm}>{dataCurta(publicadoEm)}</time>
          {atualizadoEm ? (
            <>
              {' '}
              · Atualizado em <time dateTime={atualizadoEm}>{dataCurta(atualizadoEm)}</time>
            </>
          ) : null}
        </p>
        <p className="sc-helper">
          Licença: <strong>{licenca.nome}</strong> — {licenca.descricaoCurta}
        </p>
      </div>
    </Card>
  );
}
