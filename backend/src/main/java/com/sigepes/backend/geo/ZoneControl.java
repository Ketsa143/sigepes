package com.sigepes.backend.geo;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sigepes.backend.audit.AuditActionType;
import com.sigepes.backend.audit.AuditService;
import com.sigepes.backend.common.Ids;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.trips.FishingTripEntity;

/**
 * RN-08 / HU-13 · Control punto-en-zona compartido por la bitácora y la geolocalización.
 *
 * <p>Decide si un punto cae en una zona donde la faena no puede estar (según su arte) y deja el
 * evento de control con su alerta crítica. Vive aparte de {@link GeoService} para que
 * {@code TripService} pueda usarlo al registrar capturas sin crear una dependencia circular.
 */
@Component
public class ZoneControl {

    private final RestrictedZoneRepository zoneRepository;
    private final TripPositionRepository positionRepository;
    private final ZoneEventRepository eventRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public ZoneControl(RestrictedZoneRepository zoneRepository, TripPositionRepository positionRepository,
            ZoneEventRepository eventRepository, AuditService auditService, ApplicationEventPublisher events) {
        this.zoneRepository = zoneRepository;
        this.positionRepository = positionRepository;
        this.eventRepository = eventRepository;
        this.auditService = auditService;
        this.events = events;
    }

    /**
     * Zona activa que contiene el punto y prohíbe faenar con el arte dado. Primero la base descarta
     * por caja envolvente (AC-04.5) y luego se hace la prueba exacta de punto en polígono.
     */
    public Optional<RestrictedZoneEntity> locate(double lat, double lon, String gearId) {
        return zoneRepository.findActiveCandidates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon))
                .stream()
                .filter(zone -> zone.prohibits(gearId))
                .filter(zone -> zone.contains(lat, lon))
                .findFirst();
    }

    /**
     * HU-13 · TASK-13.2 · Una captura registrada mientras la última posición de la faena está en una
     * zona de AMP que no permite esa especie genera el evento de control. Si esa misma posición ya
     * dejó un evento en la zona (por ejemplo, al entrar a una intangible) no se duplica.
     */
    @Transactional
    public Optional<ZoneEventEntity> checkCatch(FishingTripEntity trip, SpeciesEntity species) {
        Optional<TripPositionEntity> last = positionRepository.findTopByTripIdOrderByRecordedAtDesc(trip.getId());
        if (last.isEmpty()) {
            return Optional.empty();
        }
        TripPositionEntity position = last.get();
        double lat = position.getLatitude().doubleValue();
        double lon = position.getLongitude().doubleValue();
        Optional<RestrictedZoneEntity> zone = zoneRepository
                .findActiveCandidates(position.getLatitude(), position.getLongitude())
                .stream()
                .filter(candidate -> candidate.getMpaZoneKind() != null)
                .filter(candidate -> !candidate.allowsSpecies(species.getId()))
                .filter(candidate -> candidate.contains(lat, lon))
                .findFirst();
        if (zone.isEmpty()
                || eventRepository.existsByTripIdAndZoneIdAndPositionId(trip.getId(), zone.get().getId(),
                        position.getId())) {
            return Optional.empty();
        }
        return Optional.of(registerIntrusion(trip, zone.get(), position,
                "Captura de «" + species.getCommonName() + "» en la zona «" + zone.get().getName()
                        + "», donde la especie no está permitida"));
    }

    /** RN-08 · Deja el evento de control, lo audita y avisa al módulo de alertas. */
    @Transactional
    public ZoneEventEntity registerIntrusion(FishingTripEntity trip, RestrictedZoneEntity zone,
            TripPositionEntity position, String detail) {
        ZoneEventEntity event = eventRepository.save(new ZoneEventEntity(Ids.generate("evz"), trip, zone, position));
        auditService.record(AuditActionType.SECURITY, "ZONE_ENTERED", AuditService.MODULE_GEO, "Faena", trip.getId(),
                detail);
        events.publishEvent(new RestrictedZoneEntered(event.getId(), trip.getId(), trip.getOwner().getId(),
                zone.getName(), zone.getZoneType().name(), trip.getVessel().getName(),
                position.getLatitude().doubleValue(), position.getLongitude().doubleValue()));
        return event;
    }
}
