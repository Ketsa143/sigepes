package com.sigepes.backend.geo;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigepes.backend.audit.AuditActionType;
import com.sigepes.backend.audit.AuditService;
import com.sigepes.backend.common.ApiException;
import com.sigepes.backend.common.ErrorCode;
import com.sigepes.backend.common.Ids;
import com.sigepes.backend.gears.FishingGearEntity;
import com.sigepes.backend.gears.FishingGearRepository;
import com.sigepes.backend.geo.MpaDtos.CreateMpaRequest;
import com.sigepes.backend.geo.MpaDtos.CreateMpaZoneRequest;
import com.sigepes.backend.geo.MpaDtos.MpaResponse;
import com.sigepes.backend.geo.MpaDtos.MpaZoneResponse;
import com.sigepes.backend.geo.MpaDtos.NamedRef;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.species.SpeciesRepository;

/**
 * HU-13 · Áreas Marinas Protegidas: registro (TASK-13.1) y consulta pública para el mapa y la
 * ficha de cada área (TASK-13.3).
 *
 * <p>R4 (500 polígonos con p95 &lt; 500 ms) se cubre con tres medidas: la consulta filtra por caja
 * envolvente con el índice de RF-04, la geometría se simplifica según el zoom del mapa y la
 * versión simplificada queda en memoria hasta que la zona cambie. El controlador además permite a
 * navegadores y CDN cachear la respuesta unos minutos.
 */
@Service
public class MpaService {

    /** Tope de geometrías simplificadas en memoria (≈ 500 zonas × 20 niveles de zoom). */
    private static final int SIMPLIFIED_CACHE_LIMIT = 10_000;

    private final MarineProtectedAreaRepository mpaRepository;
    private final RestrictedZoneRepository zoneRepository;
    private final FishingGearRepository gearRepository;
    private final SpeciesRepository speciesRepository;
    private final AuditService auditService;
    private final Map<String, List<double[]>> simplifiedCache = new ConcurrentHashMap<>();

    public MpaService(MarineProtectedAreaRepository mpaRepository, RestrictedZoneRepository zoneRepository,
            FishingGearRepository gearRepository, SpeciesRepository speciesRepository, AuditService auditService) {
        this.mpaRepository = mpaRepository;
        this.zoneRepository = zoneRepository;
        this.gearRepository = gearRepository;
        this.speciesRepository = speciesRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------
    // Consulta pública (TASK-13.2 / TASK-13.3)
    // ------------------------------------------------------------------

    /**
     * AMP activos con sus zonas de manejo. Con {@code bbox} solo devuelve las zonas que se cruzan con
     * la vista; con {@code zoom} simplifica los polígonos a la resolución de ese nivel de mapa.
     */
    @Transactional(readOnly = true)
    public List<MpaResponse> areas(Integer zoom, double[] bbox) {
        List<RestrictedZoneEntity> zones = bbox == null
                ? zoneRepository.findActiveMpaZones()
                : zoneRepository.findActiveMpaZonesInBox(BigDecimal.valueOf(bbox[0]), BigDecimal.valueOf(bbox[2]),
                        BigDecimal.valueOf(bbox[1]), BigDecimal.valueOf(bbox[3]));
        Map<String, List<RestrictedZoneEntity>> byArea = zones.stream()
                .collect(Collectors.groupingBy(RestrictedZoneEntity::getMpaId, LinkedHashMap::new,
                        Collectors.toList()));
        Catalog catalog = catalog();
        return mpaRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(area -> bbox == null || byArea.containsKey(area.getId()))
                .map(area -> toResponse(area, byArea.getOrDefault(area.getId(), List.of()), zoom, catalog))
                .toList();
    }

    /** Ficha pública de un AMP con la geometría completa de sus zonas. */
    @Transactional(readOnly = true)
    public MpaResponse area(String id) {
        MarineProtectedAreaEntity area = mpaRepository.findById(id)
                .filter(MarineProtectedAreaEntity::isActive)
                .orElseThrow(() -> ApiException.of(ErrorCode.MPA_NOT_FOUND));
        List<RestrictedZoneEntity> zones = zoneRepository.findByMpaIdOrderByNameAsc(id).stream()
                .filter(RestrictedZoneEntity::isActive)
                .toList();
        return toResponse(area, zones, null, catalog());
    }

    // ------------------------------------------------------------------
    // Registro (TASK-13.1)
    // ------------------------------------------------------------------

    @Transactional
    public MpaResponse create(CreateMpaRequest request) {
        String name = request.name().trim();
        if (mpaRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.of(ErrorCode.MPA_ALREADY_REGISTERED);
        }
        MarineProtectedAreaEntity area = new MarineProtectedAreaEntity(Ids.generate("amp"), name,
                MpaCategory.valueOf(request.category()), request.legalAct().trim());
        area.setAuthority(blankToNull(request.authority()));
        area.setDescription(blankToNull(request.description()));
        MarineProtectedAreaEntity saved = mpaRepository.save(area);
        auditService.record(AuditActionType.CREATE, "MPA_CREATED", AuditService.MODULE_GEO, "Área marina protegida",
                saved.getId(), "AMP «" + saved.getName() + "» (" + saved.getCategory() + "), " + saved.getLegalAct());
        return toResponse(saved, List.of(), null, catalog());
    }

    @Transactional
    public MpaResponse addZone(String mpaId, CreateMpaZoneRequest request) {
        MarineProtectedAreaEntity area = mpaRepository.findById(mpaId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MPA_NOT_FOUND));
        MpaZoneKind kind = MpaZoneKind.valueOf(request.kind());
        List<String> gearIds = request.allowedGearIds() == null ? List.of() : request.allowedGearIds();
        List<String> speciesIds = request.allowedSpeciesIds() == null ? List.of() : request.allowedSpeciesIds();
        if (kind != MpaZoneKind.SUSTAINABLE_USE && (!gearIds.isEmpty() || !speciesIds.isEmpty())) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR,
                    "Solo las zonas de uso sostenible llevan artes y especies permitidas.");
        }
        for (String gearId : gearIds) {
            if (!gearRepository.existsById(gearId)) {
                throw ApiException.of(ErrorCode.GEAR_NOT_FOUND);
            }
        }
        for (String speciesId : speciesIds) {
            if (!speciesRepository.existsById(speciesId)) {
                throw ApiException.of(ErrorCode.SPECIES_NOT_FOUND);
            }
        }

        RestrictedZoneEntity zone = new RestrictedZoneEntity(Ids.generate("zon"), request.name().trim(),
                ZoneType.MPA, Polygon.validated(request.polygon()), truncate(area.getLegalAct(), 120));
        zone.setMpaId(area.getId());
        zone.setMpaZoneKind(kind);
        zone.setAllowedGearIds(gearIds);
        zone.setAllowedSpeciesIds(speciesIds);
        zone.setDescription(blankToNull(request.description()));
        zone.setDemo(area.isDemo());
        RestrictedZoneEntity saved = zoneRepository.save(zone);
        auditService.record(AuditActionType.CREATE, "MPA_ZONE_CREATED", AuditService.MODULE_GEO,
                "Área marina protegida", area.getId(), "Zona «" + saved.getName() + "» (" + kind + ") en el AMP «"
                        + area.getName() + "» con " + saved.polygon().vertices().size() + " vértices");
        return toResponse(area, zoneRepository.findByMpaIdOrderByNameAsc(area.getId()), null, catalog());
    }

    // ------------------------------------------------------------------

    private MpaResponse toResponse(MarineProtectedAreaEntity area, List<RestrictedZoneEntity> zones, Integer zoom,
            Catalog catalog) {
        List<MpaZoneResponse> zoneResponses = zones.stream()
                .map(zone -> new MpaZoneResponse(zone.getId(), zone.getName(),
                        zone.getMpaZoneKind() == null ? null : zone.getMpaZoneKind().name(), geometry(zone, zoom),
                        zone.getDescription(),
                        zone.allowedGearIds().stream()
                                .map(gearId -> new NamedRef(gearId, catalog.gears().getOrDefault(gearId, gearId)))
                                .toList(),
                        zone.allowedSpeciesIds().stream()
                                .map(speciesId -> new NamedRef(speciesId,
                                        catalog.species().getOrDefault(speciesId, speciesId)))
                                .toList()))
                .toList();
        return new MpaResponse(area.getId(), area.getName(), area.getCategory().name(), area.getLegalAct(),
                area.getAuthority(), area.getDescription(), zoneResponses);
    }

    /** Polígono a la resolución del zoom pedido (≈ 1 píxel de tolerancia), cacheado por versión. */
    private List<double[]> geometry(RestrictedZoneEntity zone, Integer zoom) {
        if (zoom == null) {
            return zone.polygon().vertices();
        }
        int level = Math.max(0, Math.min(22, zoom));
        String key = zone.getId() + "|" + zone.getUpdatedAt() + "|" + level;
        if (simplifiedCache.size() > SIMPLIFIED_CACHE_LIMIT) {
            simplifiedCache.clear();
        }
        return simplifiedCache.computeIfAbsent(key,
                ignored -> zone.polygon().simplified(toleranceForZoom(level)).vertices());
    }

    /** Grados que ocupa un píxel en un mapa web Mercator de teselas de 256 px a ese zoom. */
    static double toleranceForZoom(int zoom) {
        return 360.0 / (256.0 * Math.pow(2, zoom));
    }

    private Catalog catalog() {
        return new Catalog(
                gearRepository.findAll().stream()
                        .collect(Collectors.toMap(FishingGearEntity::getId, FishingGearEntity::getName)),
                speciesRepository.findAll().stream()
                        .collect(Collectors.toMap(SpeciesEntity::getId, SpeciesEntity::getCommonName)));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Nombres de artes y especies para las fichas (catálogos pequeños: se leen completos). */
    private record Catalog(Map<String, String> gears, Map<String, String> species) {
    }
}
