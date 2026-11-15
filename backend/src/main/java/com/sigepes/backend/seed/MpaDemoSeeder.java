package com.sigepes.backend.seed;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sigepes.backend.gears.FishingGearEntity;
import com.sigepes.backend.gears.FishingGearRepository;
import com.sigepes.backend.geo.MarineProtectedAreaEntity;
import com.sigepes.backend.geo.MarineProtectedAreaRepository;
import com.sigepes.backend.geo.MpaCategory;
import com.sigepes.backend.geo.MpaZoneKind;
import com.sigepes.backend.geo.Polygon;
import com.sigepes.backend.geo.RestrictedZoneEntity;
import com.sigepes.backend.geo.RestrictedZoneRepository;
import com.sigepes.backend.geo.ZoneType;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.species.SpeciesRepository;

/**
 * HU-13 · AMP de demostración con su zonificación.
 *
 * <p>A diferencia de {@link DemoDataSeeder}, también corre sobre una base que ya tiene datos (la de
 * Railway, sembrada antes de este sprint): solo actúa si todavía no hay ningún AMP. Reemplaza la
 * zona «MPA» sin zonificación de RF-04 por el área con sus tres zonas de manejo. Las delimitaciones
 * son aproximadas y no corresponden a la cartografía oficial de Parques Nacionales.
 */
@Component
@Profile("!test")
public class MpaDemoSeeder {

    static final String LEGACY_ZONE_NAME = "DEMO Área Marina Protegida · Ensenada de Utría";

    private static final Logger log = LoggerFactory.getLogger(MpaDemoSeeder.class);
    private static final String AREA_ID = "amp-demo-utria";

    private final MarineProtectedAreaRepository mpaRepository;
    private final RestrictedZoneRepository zoneRepository;
    private final FishingGearRepository gearRepository;
    private final SpeciesRepository speciesRepository;

    public MpaDemoSeeder(MarineProtectedAreaRepository mpaRepository, RestrictedZoneRepository zoneRepository,
            FishingGearRepository gearRepository, SpeciesRepository speciesRepository) {
        this.mpaRepository = mpaRepository;
        this.zoneRepository = zoneRepository;
        this.gearRepository = gearRepository;
        this.speciesRepository = speciesRepository;
    }

    @Transactional
    public void seedIfMissing() {
        if (mpaRepository.count() > 0) {
            return;
        }
        zoneRepository.findAll().stream()
                .filter(zone -> LEGACY_ZONE_NAME.equals(zone.getName()) && zone.getMpaId() == null)
                .forEach(zone -> {
                    zone.setActive(false);
                    zoneRepository.save(zone);
                });

        MarineProtectedAreaEntity area = new MarineProtectedAreaEntity(AREA_ID,
                "DEMO Parque Nacional Natural Utría", MpaCategory.NATIONAL_PARK, "MADS-PNN-UTRIA-DEMO");
        area.setAuthority("Parques Nacionales Naturales de Colombia");
        area.setDescription("Área de demostración en el Pacífico chocoano. La zonificación y los polígonos son "
                + "aproximados para pruebas; no corresponden a la cartografía oficial.");
        area.setDemo(true);
        mpaRepository.save(area);

        zone("zon-demo-utria-int", "DEMO Utría · Zona intangible de la ensenada", MpaZoneKind.INTANGIBLE,
                List.of(new double[] { 6.00, -77.36 }, new double[] { 6.06, -77.36 }, new double[] { 6.06, -77.31 },
                        new double[] { 6.00, -77.31 }),
                "Interior de la ensenada: no se permite ninguna actividad de pesca.", List.of(), List.of());
        zone("zon-demo-utria-uso", "DEMO Utría · Uso sostenible pesca artesanal", MpaZoneKind.SUSTAINABLE_USE,
                List.of(new double[] { 5.95, -77.39 }, new double[] { 6.00, -77.39 }, new double[] { 6.00, -77.29 },
                        new double[] { 5.95, -77.29 }),
                "Pesca artesanal solo con línea de mano o nasa y especies autorizadas.",
                ids(gear("Línea de mano"), gear("Nasa")),
                speciesIds(species("Pargo rojo"), species("Sierra")));
        zone("zon-demo-utria-amo", "DEMO Utría · Zona de amortiguación", MpaZoneKind.BUFFER,
                List.of(new double[] { 6.06, -77.40 }, new double[] { 6.10, -77.40 }, new double[] { 6.10, -77.28 },
                        new double[] { 6.06, -77.28 }),
                "Transición hacia mar abierto; sin restricción de arte ni especie.", List.of(), List.of());
        log.info("AMP DEMO «{}» cargada con 3 zonas de manejo.", area.getName());
    }

    private void zone(String id, String name, MpaZoneKind kind, List<double[]> vertices, String description,
            List<String> gearIds, List<String> speciesIds) {
        RestrictedZoneEntity zone = new RestrictedZoneEntity(id, name, ZoneType.MPA, Polygon.validated(vertices),
                "MADS-PNN-UTRIA-DEMO");
        zone.setMpaId(AREA_ID);
        zone.setMpaZoneKind(kind);
        zone.setAllowedGearIds(gearIds);
        zone.setAllowedSpeciesIds(speciesIds);
        zone.setDescription(description);
        zone.setDemo(true);
        zoneRepository.save(zone);
    }

    private Optional<FishingGearEntity> gear(String name) {
        return gearRepository.findByNameIgnoreCase(name);
    }

    private Optional<SpeciesEntity> species(String commonName) {
        return speciesRepository.findByCommonNameIgnoreCase(commonName);
    }

    @SafeVarargs
    private static List<String> ids(Optional<FishingGearEntity>... gears) {
        return java.util.Arrays.stream(gears).flatMap(Optional::stream).map(FishingGearEntity::getId).toList();
    }

    @SafeVarargs
    private static List<String> speciesIds(Optional<SpeciesEntity>... species) {
        return java.util.Arrays.stream(species).flatMap(Optional::stream).map(SpeciesEntity::getId).toList();
    }
}
