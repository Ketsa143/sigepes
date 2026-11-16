package com.sigepes.backend.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.http.MediaType;

import com.sigepes.backend.alerts.AlertEntity;
import com.sigepes.backend.alerts.AlertRepository;
import com.sigepes.backend.alerts.AlertType;
import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.permits.PermitStatus;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.support.ApiTestSupport;
import com.sigepes.backend.users.Role;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.vessels.VesselEntity;
import com.sigepes.backend.vessels.VesselStatus;

/** HU-14 · Monitoreo de datos ambientales: importación, degradación controlada, umbrales y capturas. */
class EnvironmentRulesTest extends ApiTestSupport {

    private static final String NORTE = "env-pac-norte";

    @Autowired
    private EnvironmentImporter importer;

    @Autowired
    private EnvReadingRepository readingRepository;

    @Autowired
    private AlertRepository alertRepository;

    private UserEntity analyst;
    private String analystToken;
    private String fisherToken;

    @BeforeEach
    void setUp() throws Exception {
        analyst = givenUser("7070707070", "analista.amb@sigepes.gov.co", Role.ANALYST);
        givenUser("7171717171", "pescador.amb@sigepes.gov.co", Role.FISHER);
        analystToken = bearerFor("analista.amb@sigepes.gov.co");
        fisherToken = bearerFor("pescador.amb@sigepes.gov.co");
    }

    @Test
    @DisplayName("TC-HU14-01 · AC-14.1 · La importación guarda temperatura, clorofila y oleaje de cada zona")
    void importStoresReadingsPerZone() throws Exception {
        Instant observed = Instant.now().truncatedTo(ChronoUnit.HOURS);
        EnvironmentImporter.ImportResult result = importer.importFrom(List.of(
                source("SST y olas", Map.of(NORTE, new MarineDataSource.Sample(observed, 28.6, null, 1.4))),
                source("Clorofila", Map.of(NORTE, new MarineDataSource.Sample(observed.minus(3, ChronoUnit.DAYS),
                        null, 0.82, null)))));

        assertThat(result.readingsStored()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/ambiental/zonas").header("Authorization", analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones.length()").value(7))
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].latest.sstC").value(28.6))
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].latest.chlorophyllMgM3").value(0.82))
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].latest.waveHeightM").value(1.4))
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].latest.source")
                        .value("SST y olas + Clorofila"))
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].stale").value(false));

        // La misma hora no se guarda dos veces.
        assertThat(importer.importFrom(List.of(source("SST y olas",
                Map.of(NORTE, new MarineDataSource.Sample(observed, 28.7, null, 1.5))))).readingsStored()).isZero();

        // D4 · La hora siguiente llega sin clorofila (su fuente falló): se muestra la última guardada.
        importer.importFrom(List.of(source("SST y olas",
                Map.of(NORTE, new MarineDataSource.Sample(observed.plus(1, ChronoUnit.HOURS), 28.7, null, 1.5)))));
        mockMvc.perform(get("/api/v1/ambiental/zonas").header("Authorization", analystToken))
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].latest.chlorophyllMgM3").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].lastChlorophyll.value").value(0.82));
    }

    @Test
    @DisplayName("TC-HU14-02 · AC-14.5 · D4 · Con la fuente caída el tablero muestra la última lectura y su antigüedad")
    void sourceDownServesLastReading() throws Exception {
        Instant eightHoursAgo = Instant.now().minus(8, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        importer.importFrom(List.of(source("Fuente D4",
                Map.of(NORTE, new MarineDataSource.Sample(eightHoursAgo, 28.1, 0.5, 1.2)))));

        AtomicInteger calls = new AtomicInteger();
        MarineDataSource down = failing("Fuente D4", calls);
        for (int i = 0; i < 4; i++) {
            importer.importFrom(List.of(down));
        }
        // Circuit breaker: tras 3 fallos seguidos deja de llamar la fuente.
        assertThat(calls.get()).isEqualTo(3);

        mockMvc.perform(get("/api/v1/ambiental/zonas").header("Authorization", analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].latest.sstC").value(28.1))
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].ageHours").value(8))
                .andExpect(jsonPath("$.zones[?(@.id=='env-pac-norte')].stale").value(true))
                .andExpect(jsonPath("$.sources[?(@.name=='Fuente D4')].available").value(false));
    }

    @Test
    @DisplayName("TC-HU14-03 · AC-14.3 · Una anomalía térmica > 2 °C alerta a los analistas una vez por día")
    void thermalAnomalyAlertsAnalystsOncePerDay() {
        // Dos lecturas del mismo día en Colombia (10:00 y 16:00), sin depender de la hora en que corra la prueba.
        Instant morning = AppClock.today().atStartOfDay(AppClock.ZONE).plusHours(10).toInstant();
        // Línea base de Pacífico Norte: 28,60 °C → 31,00 °C es una anomalía de +2,40 °C.
        EnvironmentImporter.ImportResult first = importer.importFrom(List.of(source("Calor",
                Map.of(NORTE, new MarineDataSource.Sample(morning, 31.0, null, 3.4)))));
        importer.importFrom(List.of(source("Calor",
                Map.of(NORTE, new MarineDataSource.Sample(morning.plus(6, ChronoUnit.HOURS), 31.1, null, 1.0)))));

        assertThat(first.anomalies()).isEqualTo(2);
        List<AlertEntity> alerts = alertRepository
                .findByRecipientIdAndArchivedAtIsNullOrderByCreatedAtDesc(analyst.getId(), Limit.of(20)).stream()
                .filter(alert -> alert.getAlertType() == AlertType.ENVIRONMENT)
                .toList();
        // Una de temperatura (no se repite la segunda vez) y una de oleaje.
        assertThat(alerts).hasSize(2);
        assertThat(alerts).anyMatch(alert -> alert.getTitle().contains("Anomalía térmica en Pacífico Norte: +2.4"));
        assertThat(alerts).anyMatch(alert -> alert.getTitle().contains("Oleaje fuerte en Pacífico Norte"));
    }

    @Test
    @DisplayName("TC-HU14-04 · AC-14.2 / AC-14.4 · La serie de la zona superpone las capturas de la especie")
    void seriesOverlaysCatches() throws Exception {
        UserEntity fisher = userRepository.findByEmailIgnoreCase("pescador.amb@sigepes.gov.co").orElseThrow();
        var gear = givenGear("Línea amb", false);
        VesselEntity vessel = givenVessel(fisher, "DIMAR-AMB-001", VesselStatus.ACTIVE, gear);
        givenPermit(fisher, "PES-AMB-1", PermitStatus.APPROVED, AppClock.today().minusMonths(1),
                AppClock.today().plusMonths(6));
        SpeciesEntity snapper = givenSpecies("Pargo amb", new BigDecimal("25.00"));
        String tripId = extract(mockMvc.perform(post("/api/v1/faenas").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"vesselId":"%s","zone":"pacífico norte","originPort":"Bahía Solano","gearId":"%s"}"""
                        .formatted(vessel.getId(), gear.getId())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");
        mockMvc.perform(post("/api/v1/faenas/" + tripId + "/capturas").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"speciesId":"%s","quantity":5,"weightKg":42.5,"averageSizeCm":30}""".formatted(snapper.getId())))
                .andExpect(status().isOk());
        importer.importFrom(List.of(source("Serie", Map.of(NORTE,
                new MarineDataSource.Sample(Instant.now().truncatedTo(ChronoUnit.HOURS), 28.8, 0.7, 1.1)))));

        mockMvc.perform(get("/api/v1/ambiental/zonas/" + NORTE + "/serie").header("Authorization", analystToken)
                .param("especieId", snapper.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readings.length()").value(1))
                .andExpect(jsonPath("$.readings[0].anomalyC").value(0.2))
                .andExpect(jsonPath("$.species").value("Pargo amb"))
                .andExpect(jsonPath("$.catches.length()").value(1))
                .andExpect(jsonPath("$.catches[0].weightKg").value(42.5));
    }

    @Test
    @DisplayName("TC-HU14-05 · Solo la autoridad consulta el tablero; un periodo inválido responde 400")
    void accessAndValidation() throws Exception {
        mockMvc.perform(get("/api/v1/ambiental/zonas").header("Authorization", fisherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/ambiental/importaciones").header("Authorization", fisherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/ambiental/zonas/" + NORTE + "/serie").header("Authorization", analystToken)
                .param("desde", "2026-05-10").param("hasta", "2026-05-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/ambiental/zonas/no-existe/serie").header("Authorization", analystToken))
                .andExpect(status().isNotFound());
        assertThat(readingRepository.count()).isZero();
    }

    @Test
    @DisplayName("Lectura de las respuestas de Open-Meteo (arreglo) y ERDDAP (CSV, con NaN sobre tierra)")
    void parsesExternalFormats() throws Exception {
        List<EnvZoneEntity> zones = List.of(
                new EnvZoneEntity("a", "A", "PACIFICO", new BigDecimal("6.5"), new BigDecimal("-77.6"), null),
                new EnvZoneEntity("b", "B", "CARIBE", new BigDecimal("10.4"), new BigDecimal("-75.6"), null));
        Map<String, MarineDataSource.Sample> samples = OpenMeteoMarineSource.parse("""
                [{"current":{"time":"2026-11-16T16:15","sea_surface_temperature":29.6,"wave_height":1.00}},
                 {"current":{"time":"2026-11-16T16:15","sea_surface_temperature":null,"wave_height":null}}]""", zones);
        assertThat(samples).containsOnlyKeys("a");
        assertThat(samples.get("a").sstC()).isEqualTo(29.6);
        assertThat(samples.get("a").observedAt()).isEqualTo(Instant.parse("2026-11-16T16:15:00Z"));

        assertThat(ErddapChlorophyllSource.parse("""
                time,latitude,longitude,chlorophyll
                UTC,degrees_north,degrees_east,mg m-3
                2026-11-08T00:00:00Z,6.5,-77.6,0.734""").chlorophyllMgM3()).isEqualTo(0.734);
        assertThat(ErddapChlorophyllSource.parse("""
                time,latitude,longitude,chlorophyll
                UTC,degrees_north,degrees_east,mg m-3
                2026-11-08T00:00:00Z,6.5,-77.6,NaN""")).isNull();
    }

    // ------------------------------------------------------------------

    private static MarineDataSource source(String name, Map<String, MarineDataSource.Sample> samples) {
        return new MarineDataSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Map<String, Sample> fetch(List<EnvZoneEntity> zones) {
                return new HashMap<>(samples);
            }
        };
    }

    private static MarineDataSource failing(String name, AtomicInteger calls) {
        return new MarineDataSource() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Map<String, Sample> fetch(List<EnvZoneEntity> zones) {
                calls.incrementAndGet();
                throw new IllegalStateException("Tiempo de espera agotado");
            }
        };
    }
}
