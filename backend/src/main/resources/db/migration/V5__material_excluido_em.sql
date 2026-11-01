-- V5 — instante da exclusão lógica e base do prazo de retenção (E5.4, ADR-0011 §6).
-- A rotina E5.5 usa este instante para purgar o binário após sete dias; metadados permanecem.

ALTER TABLE material
    ADD COLUMN excluido_em timestamptz NULL;

COMMENT ON COLUMN material.excluido_em IS
    'Instante da exclusão lógica do material; base do prazo de retenção de 7 dias do binário (ADR-0005 §3, ADR-0011 §6). NULL enquanto o material não foi excluído.';
