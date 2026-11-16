package com.sigepes.backend.environment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvReadingRepository extends JpaRepository<EnvReadingEntity, String> {

    /** AC-14.5 · La última lectura guardada de la zona: lo que se muestra si la fuente no responde. */
    Optional<EnvReadingEntity> findTopByZoneIdOrderByObservedAtDesc(String zoneId);

    /** AC-14.2 · Serie de la zona en el periodo elegido (índice zone_id, observed_at). */
    List<EnvReadingEntity> findByZoneIdAndObservedAtBetweenOrderByObservedAtAsc(String zoneId, Instant from,
            Instant to);

    /** AC-14.5 · Última clorofila conocida: es un compuesto de 8 días y su fuente puede fallar sola. */
    Optional<EnvReadingEntity> findTopByZoneIdAndChlorophyllMgM3IsNotNullOrderByObservedAtDesc(String zoneId);

    boolean existsByZoneIdAndObservedAt(String zoneId, Instant observedAt);
}
