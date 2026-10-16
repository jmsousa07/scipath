package com.scipath.health;

/**
 * Corpo de {@code GET /api/health}, conforme contracts/openapi.yaml.
 *
 * <p>{@code status} é o estado da API: {@code UP} com o banco no ar,
 * {@code DEGRADED} quando ela responde mas o banco está fora.
 * {@code database} é o estado da conexão: {@code UP} ou {@code DOWN}.
 */
public record HealthResponse(String status, String version, String database) {
}
