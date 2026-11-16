package com.sigepes.backend.environment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sigepes.backend.common.Ids;

/**
 * HU-14 · Serie DEMO de 60 días (una lectura cada 6 h) para que el tablero tenga historia desde el
 * primer día. Las lecturas quedan marcadas como {@code demo} y con la fuente «DEMO (sintético)»; las
 * importaciones reales se guardan junto a ellas. Pacífico Norte termina con una anomalía térmica para
 * mostrar la alerta de AC-14.3.
 */
@Component
@Profile("!test")
public class EnvironmentDemoSeeder {

    static final String SOURCE = "DEMO (sintético)";
    private static final Logger log = LoggerFactory.getLogger(EnvironmentDemoSeeder.class);
    private static final int DAYS = 60;

    private final boolean enabled;
    private final EnvZoneRepository zoneRepository;
    private final EnvReadingRepository readingRepository;
    private final EnvironmentImporter importer;

    public EnvironmentDemoSeeder(@Value("${sigepes.seed.enabled:true}") boolean enabled,
            EnvZoneRepository zoneRepository, EnvReadingRepository readingRepository, EnvironmentImporter importer) {
        this.enabled = enabled;
        this.zoneRepository = zoneRepository;
        this.readingRepository = readingRepository;
        this.importer = importer;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(5)
    @Transactional
    public void seedIfMissing() {
        if (!enabled || readingRepository.count() > 0) {
            return;
        }
        List<EnvZoneEntity> zones = zoneRepository.findByActiveTrueOrderByNameAsc();
        Instant end = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS);
        int stored = 0;
        for (int z = 0; z < zones.size(); z++) {
            EnvZoneEntity zone = zones.get(z);
            boolean pacific = "PACIFICO".equals(zone.getRegion());
            double baseline = zone.getSstBaselineC() == null ? 28.0 : zone.getSstBaselineC().doubleValue();
            EnvReadingEntity last = null;
            for (int step = DAYS * 4 - 1; step >= 0; step--) {
                Instant at = end.minus(step * 6L, ChronoUnit.HOURS);
                double day = (DAYS * 4 - step) / 4.0;
                double noise = Math.sin(step * 1.7 + z) * 0.25 + Math.cos(step * 0.37 + z * 2) * 0.15;
                double sst = baseline + 0.6 * Math.sin(day / 9.0 + z) + noise;
                // Calentamiento de los últimos dos días en Pacífico Norte (anomalía > 2 °C).
                if ("env-pac-norte".equals(zone.getId()) && step < 8) {
                    sst = baseline + 2.3 + noise * 0.4;
                }
                double chlorophyll = (pacific ? 0.9 : 0.35) + 0.25 * Math.sin(day / 6.0 + z) + Math.abs(noise) * 0.3;
                double wave = (pacific ? 1.6 : 1.1) + 0.5 * Math.sin(day / 4.0 + z * 1.3) + noise;
                EnvReadingEntity reading = new EnvReadingEntity(Ids.generate("amb"), zone.getId(), at,
                        decimal(sst, 2), decimal(chlorophyll, 3), decimal(Math.max(0.2, wave), 2), SOURCE);
                reading.setDemo(true);
                reading.setFetchedAt(at);
                last = readingRepository.save(reading);
                stored++;
            }
            if (last != null) {
                importer.evaluate(zone, last);
            }
        }
        log.info("Serie DEMO ambiental cargada: {} lecturas en {} zonas.", stored, zones.size());
    }

    private static BigDecimal decimal(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
