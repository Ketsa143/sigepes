package com.sigepes.backend.environment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.common.Ids;

/**
 * HU-14 · AC-14.1 / AC-14.3 / AC-14.5 · Importa las variables ambientales de las fuentes externas.
 *
 * <p>Las llamadas de red van fuera de la transacción; cada fuente tiene su circuit breaker y su
 * fallo no impide guardar lo que trajeron las demás. Lo guardado es la caché que leen el tablero y
 * las consultas: si una fuente se cae, el sistema sigue mostrando la última lectura con su
 * antigüedad (D4). Tras guardar, se revisan los umbrales y se publica la anomalía para HU-11.
 */
@Component
public class EnvironmentImporter {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentImporter.class);

    /** Fuentes y su estado: una lista fija mientras dure la aplicación. */
    private final Map<String, SourceBreaker> breakers = new LinkedHashMap<>();
    private final List<MarineDataSource> sources;
    private final EnvZoneRepository zoneRepository;
    private final EnvReadingRepository readingRepository;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final double sstThresholdC;
    private final double waveThresholdM;
    private volatile Instant lastAttemptAt;

    public EnvironmentImporter(List<MarineDataSource> sources, EnvZoneRepository zoneRepository,
            EnvReadingRepository readingRepository, ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager,
            @Value("${sigepes.environment.sst-anomaly-threshold-c:2.0}") double sstThresholdC,
            @Value("${sigepes.environment.wave-threshold-m:3.0}") double waveThresholdM) {
        this.sources = List.copyOf(sources);
        this.zoneRepository = zoneRepository;
        this.readingRepository = readingRepository;
        this.events = events;
        this.tx = new TransactionTemplate(transactionManager);
        this.sstThresholdC = sstThresholdC;
        this.waveThresholdM = waveThresholdM;
        for (MarineDataSource source : this.sources) {
            breakers.put(source.name(), new SourceBreaker(3, Duration.ofMinutes(30)));
        }
    }

    /** Resultado de una importación: zonas con lectura nueva y estado de cada fuente. */
    public record ImportResult(int readingsStored, int anomalies, List<SourceState> sources) {
    }

    public record SourceState(String name, boolean available, boolean skipped, Instant lastSuccessAt,
            Instant lastFailureAt, String lastError) {
    }

    public ImportResult importNow() {
        return importFrom(sources);
    }

    /** Las pruebas pasan fuentes simuladas; en producción son los beans {@link MarineDataSource}. */
    ImportResult importFrom(List<MarineDataSource> fromSources) {
        Instant now = Instant.now();
        lastAttemptAt = now;
        List<EnvZoneEntity> zones = zoneRepository.findByActiveTrueOrderByNameAsc();

        Map<String, List<MarineDataSource.Sample>> byZone = new HashMap<>();
        Map<String, String> sourceByZone = new HashMap<>();
        List<String> skipped = new ArrayList<>();
        for (MarineDataSource source : fromSources) {
            SourceBreaker breaker = breakers.computeIfAbsent(source.name(),
                    ignored -> new SourceBreaker(3, Duration.ofMinutes(30)));
            if (!breaker.allows(now)) {
                skipped.add(source.name());
                continue;
            }
            try {
                Map<String, MarineDataSource.Sample> samples = source.fetch(zones);
                breaker.success(now);
                samples.forEach((zoneId, sample) -> {
                    byZone.computeIfAbsent(zoneId, ignored -> new ArrayList<>()).add(sample);
                    sourceByZone.merge(zoneId, source.name(), (a, b) -> a + " + " + b);
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                breaker.failure(now, truncate(message, 200));
                log.warn("La fuente ambiental {} no respondió: {}", source.name(), message);
            }
        }

        int[] counters = new int[2];
        tx.executeWithoutResult(status -> {
            for (EnvZoneEntity zone : zones) {
                List<MarineDataSource.Sample> samples = byZone.get(zone.getId());
                if (samples == null || samples.isEmpty()) {
                    continue;
                }
                Optional<EnvReadingEntity> stored = store(zone, merge(samples, now), sourceByZone.get(zone.getId()));
                if (stored.isPresent()) {
                    counters[0]++;
                    counters[1] += evaluate(zone, stored.get());
                }
            }
        });
        return new ImportResult(counters[0], counters[1], states(now, skipped));
    }

    /**
     * AC-14.3 · Revisa los umbrales de una lectura y publica las anomalías. Devuelve cuántas hubo.
     * El seed DEMO también la usa para dejar alertas de ejemplo.
     */
    int evaluate(EnvZoneEntity zone, EnvReadingEntity reading) {
        int published = 0;
        var day = reading.getObservedAt().atZone(AppClock.ZONE).toLocalDate();
        Double anomaly = anomaly(zone, reading);
        if (anomaly != null && Math.abs(anomaly) > sstThresholdC) {
            events.publishEvent(new EnvironmentalAnomaly(zone.getId(), zone.getName(),
                    EnvironmentalAnomaly.SST_ANOMALY, round(anomaly, 2), sstThresholdC, day));
            published++;
        }
        if (reading.getWaveHeightM() != null && reading.getWaveHeightM().doubleValue() >= waveThresholdM) {
            events.publishEvent(new EnvironmentalAnomaly(zone.getId(), zone.getName(),
                    EnvironmentalAnomaly.HIGH_WAVES, reading.getWaveHeightM().doubleValue(), waveThresholdM, day));
            published++;
        }
        return published;
    }

    /** Anomalía térmica: diferencia con la temperatura de referencia de la zona. */
    static Double anomaly(EnvZoneEntity zone, EnvReadingEntity reading) {
        if (reading.getSstC() == null || zone.getSstBaselineC() == null) {
            return null;
        }
        return reading.getSstC().doubleValue() - zone.getSstBaselineC().doubleValue();
    }

    public double sstThresholdC() {
        return sstThresholdC;
    }

    public double waveThresholdM() {
        return waveThresholdM;
    }

    /** Estado de las fuentes para el aviso de D4. */
    public List<SourceState> states() {
        return states(Instant.now(), List.of());
    }

    public Instant lastAttemptAt() {
        return lastAttemptAt;
    }

    // ------------------------------------------------------------------

    private Optional<EnvReadingEntity> store(EnvZoneEntity zone, MarineDataSource.Sample sample, String source) {
        // Una lectura por zona y hora: si la fuente aún no publica un dato nuevo, no se duplica.
        Instant observedAt = sample.observedAt().truncatedTo(ChronoUnit.HOURS);
        if (readingRepository.existsByZoneIdAndObservedAt(zone.getId(), observedAt)) {
            return Optional.empty();
        }
        return Optional.of(readingRepository.save(new EnvReadingEntity(Ids.generate("amb"), zone.getId(), observedAt,
                decimal(plausible(sample.sstC(), -5, 45), 2), decimal(plausible(sample.chlorophyllMgM3(), 0, 1000), 3),
                decimal(plausible(sample.waveHeightM(), 0, 50), 2),
                truncate(source == null ? "desconocida" : source, 60))));
    }

    /** Combina las muestras de varias fuentes: cada variable de la primera que la trae. */
    private static MarineDataSource.Sample merge(List<MarineDataSource.Sample> samples, Instant now) {
        Instant observedAt = null;
        Double sst = null;
        Double chlorophyll = null;
        Double wave = null;
        for (MarineDataSource.Sample sample : samples) {
            // La hora de la lectura es la de la variable más frecuente (temperatura u ola); la
            // clorofila es un compuesto de 8 días y solo se adjunta.
            if (observedAt == null && (sample.sstC() != null || sample.waveHeightM() != null)) {
                observedAt = sample.observedAt();
            }
            sst = sst == null ? sample.sstC() : sst;
            chlorophyll = chlorophyll == null ? sample.chlorophyllMgM3() : chlorophyll;
            wave = wave == null ? sample.waveHeightM() : wave;
        }
        return new MarineDataSource.Sample(observedAt == null ? now : observedAt, sst, chlorophyll, wave);
    }

    private List<SourceState> states(Instant now, List<String> skipped) {
        return breakers.entrySet().stream()
                .map(entry -> new SourceState(entry.getKey(), entry.getValue().healthy(),
                        skipped.contains(entry.getKey()), entry.getValue().lastSuccessAt(),
                        entry.getValue().lastFailureAt(), entry.getValue().lastError()))
                .toList();
    }

    /** Un valor fuera de lo físicamente posible es un error de la fuente: se descarta, no se guarda. */
    private static Double plausible(Double value, double min, double max) {
        return value == null || !Double.isFinite(value) || value < min || value > max ? null : value;
    }

    private static BigDecimal decimal(Double value, int scale) {
        return value == null || !Double.isFinite(value) ? null : BigDecimal.valueOf(value).setScale(scale,
                RoundingMode.HALF_UP);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
