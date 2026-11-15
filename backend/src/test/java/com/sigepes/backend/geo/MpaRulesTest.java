package com.sigepes.backend.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.common.Ids;
import com.sigepes.backend.gears.FishingGearEntity;
import com.sigepes.backend.permits.PermitStatus;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.support.ApiTestSupport;
import com.sigepes.backend.users.Role;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.vessels.VesselEntity;
import com.sigepes.backend.vessels.VesselStatus;

/** HU-13 · Áreas Marinas Protegidas: zonificación, control punto-en-zona y consulta pública. */
@RecordApplicationEvents
class MpaRulesTest extends ApiTestSupport {

    /** Zona intangible y de uso sostenible, una al lado de la otra. */
    private static final String INTANGIBLE = "[[6.00,-77.36],[6.06,-77.36],[6.06,-77.31],[6.00,-77.31]]";
    private static final String SUSTAINABLE = "[[5.95,-77.39],[6.00,-77.39],[6.00,-77.29],[5.95,-77.29]]";
    private static final String BUFFER = "[[6.06,-77.40],[6.10,-77.40],[6.10,-77.28],[6.06,-77.28]]";

    @Autowired
    private MarineProtectedAreaRepository mpaRepository;

    @Autowired
    private RestrictedZoneRepository zoneRepository;

    @Autowired
    private ZoneEventRepository eventRepository;

    @Autowired
    private ApplicationEvents events;

    private VesselEntity vessel;
    private FishingGearEntity handline;
    private FishingGearEntity gillnet;
    private SpeciesEntity snapper;
    private SpeciesEntity shark;
    private String fisherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        UserEntity fisher = givenUser("8080808080", "pescador.amp@sigepes.gov.co", Role.FISHER);
        givenUser("8181818181", "admin.amp@sigepes.gov.co", Role.ADMIN);
        handline = givenGear("Línea amp", false);
        gillnet = givenGear("Trasmallo amp", false);
        vessel = givenVessel(fisher, "DIMAR-AMP-001", VesselStatus.ACTIVE, handline, gillnet);
        givenPermit(fisher, "PES-AMP-1", PermitStatus.APPROVED, AppClock.today().minusMonths(1),
                AppClock.today().plusMonths(6));
        snapper = givenSpecies("Pargo amp", new BigDecimal("30.00"));
        shark = givenSpecies("Tiburón amp", new BigDecimal("30.00"));
        fisherToken = bearerFor("pescador.amp@sigepes.gov.co");
        adminToken = bearerFor("admin.amp@sigepes.gov.co");
    }

    @Test
    @DisplayName("TC-HU13-01 · El administrador registra un AMP zonificado y cualquiera consulta su ficha sin sesión")
    void adminRegistersZonedMpaAndPublicReadsIt() throws Exception {
        String mpaId = createMpa();
        addZone(mpaId, "Ensenada intangible", "INTANGIBLE", INTANGIBLE, List.of(), List.of())
                .andExpect(status().isCreated());
        addZone(mpaId, "Uso artesanal", "SUSTAINABLE_USE", SUSTAINABLE, List.of(handline.getId()),
                List.of(snapper.getId())).andExpect(status().isCreated());
        addZone(mpaId, "Amortiguación", "BUFFER", BUFFER, List.of(), List.of()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/areas-protegidas/" + mpaId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(jsonPath("$.category").value("NATIONAL_PARK"))
                .andExpect(jsonPath("$.legalAct").value("Resolución AMP-TEST-001"))
                .andExpect(jsonPath("$.zones.length()").value(3))
                .andExpect(jsonPath("$.zones[?(@.kind == 'SUSTAINABLE_USE')].allowedGears[0].name")
                        .value("Línea amp"))
                .andExpect(jsonPath("$.zones[?(@.kind == 'SUSTAINABLE_USE')].allowedSpecies[0].name")
                        .value("Pargo amp"));
        mockMvc.perform(get("/api/v1/areas-protegidas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zones.length()").value(3));

        // Solo la autoridad registra AMP; una zona intangible no lleva artes permitidas.
        mockMvc.perform(post("/api/v1/areas-protegidas").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"AMP pirata","category":"DMI","legalAct":"N/A"}"""))
                .andExpect(status().isForbidden());
        addZone(mpaId, "Intangible con arte", "INTANGIBLE", INTANGIBLE, List.of(handline.getId()), List.of())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-HU13-02 · Una posición GPS en zona intangible genera alerta crítica y evento de control")
    void positionInIntangibleZoneRaisesCriticalAlert() throws Exception {
        String mpaId = createMpa();
        addZone(mpaId, "Ensenada intangible", "INTANGIBLE", INTANGIBLE, List.of(), List.of())
                .andExpect(status().isCreated());
        String tripId = startTrip(handline.getId());

        postPositions(tripId, "6.03", "-77.33")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intrusions.length()").value(1))
                .andExpect(jsonPath("$.intrusions[0].zone").value("Ensenada intangible"));

        assertThat(eventRepository.findByTripIdOrderByOccurredAtAsc(tripId)).hasSize(1);
        assertThat(events.stream(RestrictedZoneEntered.class)).singleElement()
                .satisfies(event -> assertThat(event.zoneType()).isEqualTo("MPA"));
        mockMvc.perform(get("/api/v1/alertas").header("Authorization", fisherToken))
                .andExpect(jsonPath("$.alerts[0].type").value("ZONE"))
                .andExpect(jsonPath("$.alerts[0].severity").value("CRITICAL"));
    }

    @Test
    @DisplayName("TC-HU13-03 · En uso sostenible el arte permitido no genera alerta; uno no permitido sí")
    void sustainableUseDependsOnGear() throws Exception {
        String mpaId = createMpa();
        addZone(mpaId, "Uso artesanal", "SUSTAINABLE_USE", SUSTAINABLE, List.of(handline.getId()), List.of())
                .andExpect(status().isCreated());

        String allowedTrip = startTrip(handline.getId());
        postPositions(allowedTrip, "5.97", "-77.34")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intrusions.length()").value(0));

        String forbiddenTrip = startTrip(gillnet.getId());
        postPositions(forbiddenTrip, "5.97", "-77.34")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intrusions.length()").value(1));

        assertThat(eventRepository.findByTripIdOrderByOccurredAtAsc(allowedTrip)).isEmpty();
        assertThat(eventRepository.findByTripIdOrderByOccurredAtAsc(forbiddenTrip)).hasSize(1);
    }

    @Test
    @DisplayName("TC-HU13-04 · Una captura de especie no permitida dentro del AMP deja evento de control")
    void catchOfForbiddenSpeciesInsideMpaRaisesEvent() throws Exception {
        String mpaId = createMpa();
        addZone(mpaId, "Uso artesanal", "SUSTAINABLE_USE", SUSTAINABLE, List.of(handline.getId()),
                List.of(snapper.getId())).andExpect(status().isCreated());
        String tripId = startTrip(handline.getId());
        postPositions(tripId, "5.97", "-77.34").andExpect(jsonPath("$.intrusions.length()").value(0));

        apiAddCatch(fisherToken, tripId, snapper.getId(), "35", "10").andExpect(status().isOk());
        assertThat(eventRepository.findByTripIdOrderByOccurredAtAsc(tripId)).isEmpty();

        // La captura queda en la bitácora (es lo que ocurrió), pero se levanta el control.
        apiAddCatch(fisherToken, tripId, shark.getId(), "40", "8").andExpect(status().isOk());
        apiAddCatch(fisherToken, tripId, shark.getId(), "40", "5").andExpect(status().isOk());
        assertThat(eventRepository.findByTripIdOrderByOccurredAtAsc(tripId)).hasSize(1);
        assertThat(events.stream(RestrictedZoneEntered.class)).hasSize(1);
    }

    @Test
    @DisplayName("TC-HU13-05 · R4: 500 polígonos de AMP se sirven al mapa con p95 < 500 ms")
    void fiveHundredPolygonsUnderBudget() throws Exception {
        for (int a = 0; a < 50; a++) {
            MarineProtectedAreaEntity area = mpaRepository.save(new MarineProtectedAreaEntity(Ids.generate("amp"),
                    "AMP carga " + a, MpaCategory.DMI, "Acto carga " + a));
            for (int z = 0; z < 10; z++) {
                RestrictedZoneEntity zone = new RestrictedZoneEntity(Ids.generate("zon"), "Zona " + a + "-" + z,
                        ZoneType.MPA, circle(2.0 + a * 0.2, -78.0 + z * 0.2, 0.05, 60), "Acto carga " + a);
                zone.setMpaId(area.getId());
                zone.setMpaZoneKind(z % 3 == 0 ? MpaZoneKind.INTANGIBLE : MpaZoneKind.SUSTAINABLE_USE);
                zoneRepository.save(zone);
            }
        }

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/areas-protegidas").param("zoom", "8")).andExpect(status().isOk());
        }
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long start = System.nanoTime();
            mockMvc.perform(get("/api/v1/areas-protegidas").param("zoom", "8"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(50));
            millis.add((System.nanoTime() - start) / 1_000_000);
        }
        millis.sort(Long::compare);
        long p95 = millis.get((int) Math.ceil(millis.size() * 0.95) - 1);
        assertThat(p95).as("p95 de GET /areas-protegidas con 500 polígonos (ms)").isLessThan(500);

        // La simplificación por zoom reduce los vértices y el bbox recorta la respuesta.
        mockMvc.perform(get("/api/v1/areas-protegidas").param("zoom", "6").param("bbox", "1.9,-78.1,2.1,-77.9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].zones.length()").value(1))
                .andExpect(jsonPath("$[0].zones[0].polygon.length()").value(org.hamcrest.Matchers.lessThan(60)));
    }

    // ------------------------------------------------------------------

    private static Polygon circle(double lat, double lon, double radius, int vertices) {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < vertices; i++) {
            double angle = 2 * Math.PI * i / vertices;
            points.add(new double[] { lat + radius * Math.cos(angle), lon + radius * Math.sin(angle) });
        }
        return Polygon.validated(points);
    }

    private String createMpa() throws Exception {
        String response = mockMvc.perform(post("/api/v1/areas-protegidas").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"PNN Prueba","category":"NATIONAL_PARK","legalAct":"Resolución AMP-TEST-001",
                         "authority":"Parques Nacionales Naturales"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extract(response, "id");
    }

    private ResultActions addZone(String mpaId, String name, String kind, String polygon, List<String> gearIds,
            List<String> speciesIds) throws Exception {
        return mockMvc.perform(post("/api/v1/areas-protegidas/" + mpaId + "/zonas")
                .header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","kind":"%s","polygon":%s,"allowedGearIds":%s,"allowedSpeciesIds":%s}"""
                        .formatted(name, kind, polygon, json(gearIds), json(speciesIds))));
    }

    private String startTrip(String gearId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/faenas").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vesselId":"%s","zone":"Pacífico Norte","originPort":"Bahía Solano","gearId":"%s"}"""
                        .formatted(vessel.getId(), gearId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return extract(response, "id");
    }

    private ResultActions postPositions(String tripId, String lat, String lon) throws Exception {
        return mockMvc.perform(post("/api/v1/faenas/" + tripId + "/posiciones").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"positions":[{"latitude":%s,"longitude":%s}]}""".formatted(lat, lon)));
    }

    private static String json(List<String> values) {
        return values.stream().map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
