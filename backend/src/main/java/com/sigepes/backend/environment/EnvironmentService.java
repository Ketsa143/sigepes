package com.sigepes.backend.environment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigepes.backend.audit.AuditActionType;
import com.sigepes.backend.audit.AuditService;
import com.sigepes.backend.common.ApiException;
import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.common.ErrorCode;
import com.sigepes.backend.environment.EnvironmentDtos.CatchPoint;
import com.sigepes.backend.environment.EnvironmentDtos.ImportResponse;
import com.sigepes.backend.environment.EnvironmentDtos.LastValue;
import com.sigepes.backend.environment.EnvironmentDtos.OverviewResponse;
import com.sigepes.backend.environment.EnvironmentDtos.ReadingResponse;
import com.sigepes.backend.environment.EnvironmentDtos.SeriesResponse;
import com.sigepes.backend.environment.EnvironmentDtos.SourceStatus;
import com.sigepes.backend.environment.EnvironmentDtos.ZoneSnapshot;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.species.SpeciesRepository;
import com.sigepes.backend.trips.CatchRepository;

/**
 * HU-14 · RF-14 · Consultas del tablero ambiental. Todo se lee de las lecturas guardadas, nunca de la
 * fuente externa: el tablero responde igual con la fuente caída (AC-14.5).
 */
@Service
public class EnvironmentService {

    /** Las importaciones son cada 6 h; pasadas 7 h sin dato nuevo la lectura se marca como vieja. */
    static final Duration STALE_AFTER = Duration.ofHours(7);
    /** Tope del periodo consultable: un año de lecturas cada 6 h son ~1 460 puntos por zona. */
    static final int MAX_PERIOD_DAYS = 366;

    private final EnvZoneRepository zoneRepository;
    private final EnvReadingRepository readingRepository;
    private final CatchRepository catchRepository;
    private final SpeciesRepository speciesRepository;
    private final EnvironmentImporter importer;
    private final AuditService auditService;

    public EnvironmentService(EnvZoneRepository zoneRepository, EnvReadingRepository readingRepository,
            CatchRepository catchRepository, SpeciesRepository speciesRepository, EnvironmentImporter importer,
            AuditService auditService) {
        this.zoneRepository = zoneRepository;
        this.readingRepository = readingRepository;
        this.catchRepository = catchRepository;
        this.speciesRepository = speciesRepository;
        this.importer = importer;
        this.auditService = auditService;
    }

    /** AC-14.2 · Todas las zonas con su última lectura y el estado de las fuentes. */
    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        Instant now = Instant.now();
        List<ZoneSnapshot> zones = zoneRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(zone -> snapshot(zone, now))
                .toList();
        return new OverviewResponse(zones, sources(), importer.lastAttemptAt(), importer.sstThresholdC(),
                importer.waveThresholdM());
    }

    /** AC-14.2 / AC-14.4 · Serie de la zona en el periodo y, si se pide, las capturas de una especie. */
    @Transactional(readOnly = true)
    public SeriesResponse series(String zoneId, LocalDate from, LocalDate to, String speciesId) {
        EnvZoneEntity zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> ApiException.of(ErrorCode.ENV_ZONE_NOT_FOUND));
        LocalDate end = to == null ? AppClock.today() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR, "La fecha inicial no puede ser posterior a la final.");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_PERIOD_DAYS) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR,
                    "El periodo no puede superar " + MAX_PERIOD_DAYS + " días.");
        }
        Instant fromInstant = start.atStartOfDay(AppClock.ZONE).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(AppClock.ZONE).toInstant().minusMillis(1);
        List<ReadingResponse> readings = readingRepository
                .findByZoneIdAndObservedAtBetweenOrderByObservedAtAsc(zone.getId(), fromInstant, toInstant).stream()
                .map(reading -> toResponse(zone, reading))
                .toList();

        String speciesName = null;
        List<CatchPoint> catches = List.of();
        if (speciesId != null && !speciesId.isBlank()) {
            SpeciesEntity species = speciesRepository.findById(speciesId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.SPECIES_NOT_FOUND));
            speciesName = species.getCommonName();
            catches = catchRepository.dailyCatchInZone(species.getId(), zone.getName(), start, end).stream()
                    .map(row -> new CatchPoint((LocalDate) row[0], toKg(row[1])))
                    .toList();
        }
        return new SeriesResponse(snapshot(zone, Instant.now()), start, end, readings,
                speciesName == null ? null : speciesId, speciesName, catches);
    }

    /** Importación inmediata a pedido del analista (además de la programada cada 6 h). */
    public ImportResponse importNow() {
        EnvironmentImporter.ImportResult result = importer.importNow();
        auditService.record(AuditActionType.CREATE, "ENV_IMPORTED", AuditService.MODULE_ENVIRONMENT,
                "Datos ambientales", "importacion", "Importación manual: " + result.readingsStored()
                        + " lectura(s) nuevas, " + result.anomalies() + " anomalía(s)");
        return new ImportResponse(result.readingsStored(), result.anomalies(), sources());
    }

    // ------------------------------------------------------------------

    private ZoneSnapshot snapshot(EnvZoneEntity zone, Instant now) {
        EnvReadingEntity latest = readingRepository.findTopByZoneIdOrderByObservedAtDesc(zone.getId()).orElse(null);
        ReadingResponse reading = latest == null ? null : toResponse(zone, latest);
        Long ageHours = latest == null ? null : Math.max(0, Duration.between(latest.getObservedAt(), now).toHours());
        boolean stale = latest == null || Duration.between(latest.getObservedAt(), now).compareTo(STALE_AFTER) > 0;
        Double anomaly = latest == null ? null : EnvironmentImporter.anomaly(zone, latest);
        boolean sstAlert = anomaly != null && Math.abs(anomaly) > importer.sstThresholdC();
        boolean waveAlert = latest != null && latest.getWaveHeightM() != null
                && latest.getWaveHeightM().doubleValue() >= importer.waveThresholdM();
        LastValue lastChlorophyll = null;
        if (latest != null && latest.getChlorophyllMgM3() == null) {
            lastChlorophyll = readingRepository
                    .findTopByZoneIdAndChlorophyllMgM3IsNotNullOrderByObservedAtDesc(zone.getId())
                    .map(previous -> new LastValue(previous.getChlorophyllMgM3(), previous.getObservedAt(),
                            Math.max(0, Duration.between(previous.getObservedAt(), now).toHours())))
                    .orElse(null);
        }
        return new ZoneSnapshot(zone.getId(), zone.getName(), zone.getRegion(), zone.getLatitude().doubleValue(),
                zone.getLongitude().doubleValue(), zone.getSstBaselineC(), reading, ageHours, stale, sstAlert,
                waveAlert, lastChlorophyll);
    }

    private static ReadingResponse toResponse(EnvZoneEntity zone, EnvReadingEntity reading) {
        Double anomaly = EnvironmentImporter.anomaly(zone, reading);
        return new ReadingResponse(reading.getObservedAt(), reading.getSstC(), reading.getChlorophyllMgM3(),
                reading.getWaveHeightM(),
                anomaly == null ? null : BigDecimal.valueOf(anomaly).setScale(2, RoundingMode.HALF_UP),
                reading.getSource());
    }

    private List<SourceStatus> sources() {
        return importer.states().stream()
                .map(state -> new SourceStatus(state.name(), state.available(), state.lastSuccessAt(),
                        state.lastFailureAt(), state.lastError()))
                .toList();
    }

    private static BigDecimal toKg(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2, RoundingMode.HALF_UP);
        }
        return value == null ? BigDecimal.ZERO
                : BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }
}
