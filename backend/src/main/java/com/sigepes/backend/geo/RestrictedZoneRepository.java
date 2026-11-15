package com.sigepes.backend.geo;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestrictedZoneRepository extends JpaRepository<RestrictedZoneEntity, String> {

    List<RestrictedZoneEntity> findAllByOrderByNameAsc();

    /** AC-04.5: prefiltro por caja envolvente (índice ix_restricted_zones_bbox). */
    @Query("""
            SELECT z FROM RestrictedZoneEntity z
            WHERE z.active = TRUE
              AND z.minLat <= :lat AND z.maxLat >= :lat
              AND z.minLon <= :lon AND z.maxLon >= :lon
            """)
    List<RestrictedZoneEntity> findActiveCandidates(@Param("lat") BigDecimal lat, @Param("lon") BigDecimal lon);

    /** HU-13: zonas de manejo de un AMP. */
    List<RestrictedZoneEntity> findByMpaIdOrderByNameAsc(String mpaId);

    /**
     * HU-13 · R4: zonas de AMP activas cuya caja envolvente se cruza con la vista del mapa. Usa el
     * mismo índice de caja envolvente que el control punto-en-zona.
     */
    @Query("""
            SELECT z FROM RestrictedZoneEntity z
            WHERE z.active = TRUE AND z.mpaId IS NOT NULL
              AND z.maxLat >= :minLat AND z.minLat <= :maxLat
              AND z.maxLon >= :minLon AND z.minLon <= :maxLon
            ORDER BY z.name ASC
            """)
    List<RestrictedZoneEntity> findActiveMpaZonesInBox(@Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat, @Param("minLon") BigDecimal minLon,
            @Param("maxLon") BigDecimal maxLon);

    @Query("""
            SELECT z FROM RestrictedZoneEntity z
            WHERE z.active = TRUE AND z.mpaId IS NOT NULL
            ORDER BY z.name ASC
            """)
    List<RestrictedZoneEntity> findActiveMpaZones();
}
