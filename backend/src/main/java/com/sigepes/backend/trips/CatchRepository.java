package com.sigepes.backend.trips;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatchRepository extends JpaRepository<CatchEntity, String> {

    long countByTripId(String tripId);

    /**
     * RF-12 · Desembarcos validados agregados por especie y zona: solo faenas cerradas (RN-24).
     * Filas {@code [speciesId, commonName, zone, kg, faenas]}.
     */
    @Query("""
            SELECT c.species.id, c.species.commonName, t.zone, SUM(c.weightKg), COUNT(DISTINCT t.id)
            FROM CatchEntity c JOIN c.trip t
            WHERE t.status = com.sigepes.backend.trips.TripStatus.CLOSED AND t.tripDate BETWEEN :from AND :to
            GROUP BY c.species.id, c.species.commonName, t.zone
            """)
    List<Object[]> landings(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Igual que {@link #landings}, limitado a las faenas de un titular (AC-12.6). */
    @Query("""
            SELECT c.species.id, c.species.commonName, t.zone, SUM(c.weightKg), COUNT(DISTINCT t.id)
            FROM CatchEntity c JOIN c.trip t
            WHERE t.status = com.sigepes.backend.trips.TripStatus.CLOSED AND t.tripDate BETWEEN :from AND :to
              AND t.owner.id = :ownerId
            GROUP BY c.species.id, c.species.commonName, t.zone
            """)
    List<Object[]> landingsOf(@Param("ownerId") String ownerId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * HU-17 · TASK-17.1 · Captura total (kg) y esfuerzo (faenas cerradas que la registraron) por
     * especie y año, solo para años completos (anteriores a {@code beforeYear}).
     */
    @Query("""
            SELECT c.species.id, EXTRACT(YEAR FROM t.tripDate), SUM(c.weightKg), COUNT(DISTINCT t.id)
            FROM CatchEntity c JOIN c.trip t
            WHERE t.status = com.sigepes.backend.trips.TripStatus.CLOSED AND t.tripDate < :before
            GROUP BY c.species.id, EXTRACT(YEAR FROM t.tripDate)
            """)
    List<Object[]> yearlyCatchAndEffort(@Param("before") LocalDate before);

    /**
     * HU-14 · AC-14.4 · Kilos diarios de una especie en una zona de pesca (por el nombre de zona que
     * declara la faena), para superponerlos a la serie ambiental del mismo periodo.
     */
    @Query("""
            SELECT t.tripDate, SUM(c.weightKg)
            FROM CatchEntity c JOIN c.trip t
            WHERE c.species.id = :speciesId AND LOWER(t.zone) = LOWER(:zone)
              AND t.status <> com.sigepes.backend.trips.TripStatus.CANCELLED
              AND t.tripDate BETWEEN :from AND :to
            GROUP BY t.tripDate
            ORDER BY t.tripDate
            """)
    List<Object[]> dailyCatchInZone(@Param("speciesId") String speciesId, @Param("zone") String zone,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
