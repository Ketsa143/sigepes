package com.sigepes.backend.rms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.common.Ids;
import com.sigepes.backend.quotas.QuotaRepository;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.support.ApiTestSupport;
import com.sigepes.backend.trips.CatchEntity;
import com.sigepes.backend.trips.FishingTripEntity;
import com.sigepes.backend.trips.FishingTripRepository;
import com.sigepes.backend.trips.TripStatus;
import com.sigepes.backend.users.Role;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.vessels.VesselEntity;
import com.sigepes.backend.vessels.VesselStatus;

/**
 * HU-17 · RF-17 · Rendimiento Máximo Sostenible.
 *
 * <p>Las series se generan con el propio modelo de Schaefer a partir de parámetros conocidos
 * (r, K, q), así que el ajuste debe recuperarlos: con r = 0,5, K = 200 000 kg y q = 0,02 por faena,
 * el RMS es r·K/4 = 25 000 kg y el esfuerzo óptimo r/(2q) = 12,5 faenas por año.
 */
class RmsRulesTest extends ApiTestSupport {

    private static final int[] EFFORT = { 4, 6, 8, 10, 12, 14, 16, 14, 12, 10 };

    @Autowired
    private FishingTripRepository tripRepository;

    @Autowired
    private CatchEffortHistoryRepository historyRepository;

    @Autowired
    private QuotaRepository quotaRepository;

    @Autowired
    private RmsService rmsService;

    @Autowired
    private RmsRunRepository runRepository;

    private UserEntity fisher;
    private VesselEntity vessel;
    private String analystToken;
    private String adminToken;
    private String fisherToken;

    @BeforeEach
    void setUp() throws Exception {
        fisher = givenUser("9090909090", "pescador.rms@sigepes.gov.co", Role.FISHER);
        givenUser("9191919191", "analista.rms@sigepes.gov.co", Role.ANALYST);
        givenUser("9292929292", "admin.rms@sigepes.gov.co", Role.ADMIN);
        vessel = givenVessel(fisher, "DIMAR-RMS-001", VesselStatus.ACTIVE, givenGear("Palangre rms", false));
        analystToken = bearerFor("analista.rms@sigepes.gov.co");
        adminToken = bearerFor("admin.rms@sigepes.gov.co");
        fisherToken = bearerFor("pescador.rms@sigepes.gov.co");
    }

    @Test
    @DisplayName("TC-HU17-01 · Con 10 años de faenas el cálculo en segundo plano da RMS, f-RMS, r, K y la curva")
    void tenYearsFromTripsRecoverKnownParameters() throws Exception {
        SpeciesEntity tuna = givenSpecies("Atún rms", new BigDecimal("50.00"));
        givenTripSeries(List.of(tuna), new double[] { 0.5 }, new double[] { 200_000 }, 0.02);

        String runId = extract(requestRun(analystToken).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString(), "id");
        assertThat(rmsService.runPending()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/rms/calculos/" + runId).header("Authorization", analystToken))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.speciesCount").value(1));

        String body = mockMvc.perform(get("/api/v1/rms/especies/" + tuna.getId()).header("Authorization", analystToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fitted").value(true))
                .andExpect(jsonPath("$.indicative").value(false))
                .andExpect(jsonPath("$.yearsCount").value(10))
                .andExpect(jsonPath("$.series.length()").value(10))
                .andExpect(jsonPath("$.series[0].source").value("FAENAS"))
                .andExpect(jsonPath("$.curve.length()").value(RmsService.CURVE_POINTS + 1))
                .andReturn().getResponse().getContentAsString();

        RmsDtos.EstimateResponse estimate = rmsService.estimate(tuna.getId());
        assertThat(estimate.msyKg().doubleValue()).isCloseTo(25_000, within(1_250.0));
        assertThat(estimate.fMsy().doubleValue()).isCloseTo(12.5, within(0.7));
        assertThat(estimate.r().doubleValue()).isCloseTo(0.5, within(0.05));
        assertThat(estimate.kKg().doubleValue()).isCloseTo(200_000, within(20_000.0));
        assertThat(estimate.stockStatus()).isIn("UNDER_EXPLOITED", "FULLY_EXPLOITED", "OVER_EXPLOITED");
        assertThat(body).contains("\"yieldKg\"");
    }

    @Test
    @DisplayName("TC-HU17-02 · Con 3 años el RMS es indicativo: aprobar sin nota se rechaza; con nota crea la cuota")
    void indicativeEstimateNeedsJustification() throws Exception {
        SpeciesEntity shrimp = givenSpecies("Camarón rms", new BigDecimal("12.00"));
        givenHistory(shrimp, 0.9, 400_000, 0.0008, new int[] { 300, 350, 400 });
        calculate();

        mockMvc.perform(get("/api/v1/rms").header("Authorization", analystToken))
                .andExpect(jsonPath("$[0].species").value("Camarón rms"))
                .andExpect(jsonPath("$[0].indicative").value(true))
                .andExpect(jsonPath("$[0].fitted").value(true));
        BigDecimal msy = rmsService.estimate(shrimp.getId()).msyKg();
        String proposalId = extract(propose(analystToken, shrimp.getId(), half(msy), null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.indicative").value(true))
                .andReturn().getResponse().getContentAsString(), "id");

        decide(adminToken, proposalId, "aprobar", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RMS_JUSTIFICATION_REQUIRED"));
        long quotasBefore = quotaRepository.count();
        decide(adminToken, proposalId, "aprobar", "Serie corta; se revisará con la evaluación de 2027.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.quotaId").isNotEmpty());
        assertThat(quotaRepository.count()).isEqualTo(quotasBefore + 1);
    }

    @Test
    @DisplayName("TC-HU17-03 · La propuesta de cuota no puede superar el RMS")
    void proposalCannotExceedMsy() throws Exception {
        SpeciesEntity tuna = givenSpecies("Atún rms", new BigDecimal("50.00"));
        givenHistory(tuna, 0.5, 200_000, 0.002, new int[] { 40, 60, 80, 100, 120, 140, 160, 140, 120, 100 });
        calculate();
        BigDecimal msy = rmsService.estimate(tuna.getId()).msyKg();

        propose(analystToken, tuna.getId(), msy.add(BigDecimal.ONE), null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RMS_PROPOSAL_ABOVE_MSY"));
        propose(analystToken, tuna.getId(), msy, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.indicative").value(false));
    }

    @Test
    @DisplayName("TC-HU17-04 · Solo el ADMIN aprueba la cuota; el pescador no consulta el RMS")
    void onlyAdminApproves() throws Exception {
        SpeciesEntity tuna = givenSpecies("Atún rms", new BigDecimal("50.00"));
        givenHistory(tuna, 0.5, 200_000, 0.002, new int[] { 40, 60, 80, 100, 120, 140, 160, 140, 120, 100 });
        calculate();
        String proposalId = extract(propose(analystToken, tuna.getId(), half(rmsService.estimate(tuna.getId()).msyKg()),
                null).andReturn().getResponse().getContentAsString(), "id");

        decide(analystToken, proposalId, "aprobar", "Intento del analista").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/rms").header("Authorization", fisherToken)).andExpect(status().isForbidden());
        requestRun(fisherToken).andExpect(status().isForbidden());
        decide(adminToken, proposalId, "aprobar", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        decide(adminToken, proposalId, "rechazar", null).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("TC-HU17-05 · R5: 20 especies × 10 años en segundo plano en < 30 s; la consulta en caché < 300 ms")
    void twentySpeciesTenYearsWithinBudget() throws Exception {
        List<SpeciesEntity> species = new ArrayList<>();
        double[] r = new double[20];
        double[] k = new double[20];
        for (int i = 0; i < 20; i++) {
            species.add(givenSpecies("Especie rms " + i, new BigDecimal("10.00")));
            r[i] = 0.3 + i * 0.02;
            k[i] = 100_000 + i * 10_000;
        }
        givenTripSeries(species, r, k, 0.02);

        long start = System.nanoTime();
        calculate();
        long jobMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(jobMs).as("duración del cálculo de 20 especies × 10 años (ms)").isLessThan(30_000);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/rms").header("Authorization", analystToken)).andExpect(status().isOk());
        }
        List<Long> millis = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long t0 = System.nanoTime();
            mockMvc.perform(get("/api/v1/rms").header("Authorization", analystToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(20));
            millis.add((System.nanoTime() - t0) / 1_000_000);
        }
        millis.sort(Long::compare);
        assertThat(millis.get(18)).as("p95 de GET /api/v1/rms (ms)").isLessThan(300);
    }

    // ------------------------------------------------------------------

    /**
     * Faenas cerradas en los 10 años completos anteriores: cada año tiene {@code EFFORT[t]} faenas y
     * cada faena captura de cada especie {@code q·B(t)} kg, con B siguiendo el modelo de Schaefer.
     */
    @Test
    @DisplayName("Revisión · Un cálculo que quedó en curso por un reinicio se marca como fallido al arrancar")
    void interruptedRunIsMarkedFailed() {
        RmsRunEntity run = new RmsRunEntity(Ids.generate("rms"), null);
        run.start();
        runRepository.save(run);

        assertThat(rmsService.failInterrupted()).isEqualTo(1);

        RmsRunEntity stored = runRepository.findById(run.getId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(RmsRunEntity.Status.FAILED);
        assertThat(stored.getError()).contains("reinicio");
        assertThat(stored.getFinishedAt()).isNotNull();
    }

    private void givenTripSeries(List<SpeciesEntity> species, double[] r, double[] k, double q) {
        int firstYear = AppClock.today().getYear() - EFFORT.length;
        double[] biomass = k.clone();
        for (int t = 0; t < EFFORT.length; t++) {
            for (int trip = 0; trip < EFFORT[t]; trip++) {
                FishingTripEntity entity = new FishingTripEntity(Ids.generate("fae"), fisher, vessel, "Pacífico Norte",
                        LocalDate.of(firstYear + t, 1 + trip % 12, 1 + trip % 28));
                entity.setOriginPort("Bahía Solano");
                entity.setStatus(TripStatus.CLOSED);
                for (int s = 0; s < species.size(); s++) {
                    BigDecimal perTrip = BigDecimal.valueOf(q * biomass[s]).setScale(2, RoundingMode.HALF_UP);
                    entity.addCatch(new CatchEntity(Ids.generate("cap"), species.get(s), 1, perTrip,
                            new BigDecimal("60.00"), species.get(s).getMinSizeCm()));
                }
                tripRepository.save(entity);
            }
            for (int s = 0; s < species.size(); s++) {
                double catchKg = q * EFFORT[t] * biomass[s];
                biomass[s] = biomass[s] + r[s] * biomass[s] * (1 - biomass[s] / k[s]) - catchKg;
            }
        }
    }

    /** Serie histórica (anterior a SIGEPES) generada con el mismo modelo. */
    private void givenHistory(SpeciesEntity species, double r, double k, double q, int[] effort) {
        int firstYear = AppClock.today().getYear() - effort.length;
        double biomass = k;
        for (int t = 0; t < effort.length; t++) {
            double catchKg = q * effort[t] * biomass;
            historyRepository.save(new CatchEffortHistoryEntity(Ids.generate("hce"), species.getId(), firstYear + t,
                    BigDecimal.valueOf(catchKg).setScale(2, RoundingMode.HALF_UP), effort[t], "Prueba"));
            biomass = biomass + r * biomass * (1 - biomass / k) - catchKg;
        }
    }

    /** Pide el cálculo como lo hace la pantalla y lo procesa (en pruebas no hay commit que lo lance). */
    private void calculate() throws Exception {
        requestRun(analystToken).andExpect(status().isAccepted());
        assertThat(rmsService.runPending()).isEqualTo(1);
    }

    private static BigDecimal half(BigDecimal value) {
        return value.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
    }

    private ResultActions requestRun(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/rms/calculos").header("Authorization", token));
    }

    private ResultActions propose(String token, String speciesId, BigDecimal kg, String justification)
            throws Exception {
        LocalDate from = AppClock.today().plusMonths(1).withDayOfMonth(1);
        return mockMvc.perform(post("/api/v1/rms/especies/" + speciesId + "/propuestas").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"proposedKg":%s,"zone":"Pacífico Norte","from":"%s","to":"%s"%s}"""
                        .formatted(kg.toPlainString(), from, from.plusMonths(6),
                                justification == null ? "" : ",\"justification\":\"" + justification + "\"")));
    }

    private ResultActions decide(String token, String proposalId, String action, String note) throws Exception {
        return mockMvc.perform(post("/api/v1/rms/propuestas/" + proposalId + "/" + action)
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(note == null ? "{}" : "{\"note\":\"" + note + "\"}"));
    }
}
