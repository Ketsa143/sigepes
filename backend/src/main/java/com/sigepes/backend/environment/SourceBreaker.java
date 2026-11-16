package com.sigepes.backend.environment;

import java.time.Duration;
import java.time.Instant;

/**
 * HU-14 · AC-14.5 · Circuit breaker de una fuente externa. Tras {@code threshold} fallos seguidos
 * deja de llamarla durante {@code cooldown}: así una fuente caída no hace esperar cada importación
 * hasta el tiempo de espera de red. Mientras tanto el tablero sigue sirviendo la última lectura
 * guardada (degradación controlada).
 */
final class SourceBreaker {

    private final int threshold;
    private final Duration cooldown;
    private int consecutiveFailures;
    private Instant openUntil;
    private Instant lastSuccessAt;
    private Instant lastFailureAt;
    private String lastError;

    SourceBreaker(int threshold, Duration cooldown) {
        this.threshold = threshold;
        this.cooldown = cooldown;
    }

    synchronized boolean allows(Instant now) {
        return openUntil == null || !now.isBefore(openUntil);
    }

    synchronized void success(Instant now) {
        consecutiveFailures = 0;
        openUntil = null;
        lastSuccessAt = now;
        lastError = null;
    }

    synchronized void failure(Instant now, String error) {
        consecutiveFailures++;
        lastFailureAt = now;
        lastError = error;
        if (consecutiveFailures >= threshold) {
            openUntil = now.plus(cooldown);
        }
    }

    synchronized Instant lastSuccessAt() {
        return lastSuccessAt;
    }

    synchronized Instant lastFailureAt() {
        return lastFailureAt;
    }

    synchronized String lastError() {
        return lastError;
    }

    /** Disponible si el último intento salió bien (o si todavía no se ha intentado). */
    synchronized boolean healthy() {
        return lastFailureAt == null || (lastSuccessAt != null && lastSuccessAt.isAfter(lastFailureAt));
    }
}
