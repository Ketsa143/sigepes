package com.sigepes.backend.sanctions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.sigepes.backend.alerts.AlertRepository;
import com.sigepes.backend.audit.AuditLogRepository;
import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.common.BusinessDays;
import com.sigepes.backend.gears.FishingGearEntity;
import com.sigepes.backend.permits.PermitEntity;
import com.sigepes.backend.permits.PermitRepository;
import com.sigepes.backend.permits.PermitStatus;
import com.sigepes.backend.support.ApiTestSupport;
import com.sigepes.backend.users.Role;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.vessels.VesselEntity;
import com.sigepes.backend.vessels.VesselStatus;

/** HU-23 · Procesos sancionatorios: apertura, etapas con plazos, decisión, recurso y permisos. */
class SanctionRulesTest extends ApiTestSupport {

    @Autowired
    private SanctionProcessRepository processRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AuditLogRepository auditRepository;

    @Autowired
    private PermitRepository permitRepository;

    @Autowired
    private com.sigepes.backend.reports.IllegalReportRepository reportRepository;

    private UserEntity fisher;
    private VesselEntity vessel;
    private FishingGearEntity handline;
    private FishingGearEntity dynamite;
    private PermitEntity permit;
    private String inspectorToken;
    private String fisherToken;
    private String otherToken;
    private String analystToken;

    @BeforeEach
    void setUp() throws Exception {
        fisher = givenUser("5050505050", "pescador.ps@sigepes.gov.co", Role.FISHER);
        givenUser("5151515151", "otro.ps@sigepes.gov.co", Role.FISHER);
        givenUser("5252525252", "inspector.ps@sigepes.gov.co", Role.INSPECTOR);
        givenUser("5353535353", "analista.ps@sigepes.gov.co", Role.ANALYST);
        handline = givenGear("Línea ps", false);
        dynamite = givenGear("Explosivos ps", true);
        vessel = givenVessel(fisher, "DIMAR-PS-001", VesselStatus.ACTIVE, handline);
        permit = givenPermit(fisher, "PES-PS-1", PermitStatus.APPROVED, AppClock.today().minusMonths(2),
                AppClock.today().plusMonths(6));
        inspectorToken = bearerFor("inspector.ps@sigepes.gov.co");
        fisherToken = bearerFor("pescador.ps@sigepes.gov.co");
        otherToken = bearerFor("otro.ps@sigepes.gov.co");
        analystToken = bearerFor("analista.ps@sigepes.gov.co");
    }

    @Test
    @DisplayName("TC-HU23-01 · AC-23.1 / AC-23.3 · Abrir desde un acta con «Arte prohibido»: Apertura, infracción y aviso")
    void opensFromInspectionWithFindings() throws Exception {
        String inspectionId = inspection(dynamite.getId());
        LocalDate expectedDeadline = BusinessDays.add(AppClock.today(), SanctionTypes.DEFENSE_DAYS);

        String body = open(inspectionId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("OPENED"))
                .andExpect(jsonPath("$.stageLabel").value("Apertura"))
                .andExpect(jsonPath("$.infraction").value("PROHIBITED_GEAR"))
                .andExpect(jsonPath("$.stageDeadline").value(expectedDeadline.toString()))
                .andExpect(jsonPath("$.number").value(org.hamcrest.Matchers.startsWith("PS-" + AppClock.today().getYear() + "-")))
                .andExpect(jsonPath("$.actions.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        assertThat(alertRepository.findAll()).anyMatch(alert -> alert.getRecipientId().equals(fisher.getId())
                && alert.getTitle().startsWith("Proceso " + extract(body, "number")));
        // Una sola por acta.
        open(inspectionId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SANCTION_ALREADY_OPEN"));
        // Un acta conforme no da lugar a proceso.
        open(inspection(handline.getId())).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SANCTION_NOT_APPLICABLE"));
    }

    @Test
    @DisplayName("TC-HU23-02 · AC-23.2 · Vencido el plazo de descargos sin respuesta pasa a Pruebas «Sin descargos»")
    void expiredDefenseMovesToEvidenceWithoutDefense() throws Exception {
        String id = extract(open(inspection(dynamite.getId())).andReturn().getResponse().getContentAsString(), "id");
        action(id, "avanzar", "{\"note\":\"Notificado en persona\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DEFENSE"));

        // Antes del vencimiento no se puede pasar a pruebas sin descargos.
        action(id, "avanzar", "{}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SANCTION_DEADLINE"));

        expireDeadline(id);
        long audited = auditRepository.countByAction("SANCTION_ADVANCED");
        action(id, "avanzar", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("EVIDENCE"))
                .andExpect(jsonPath("$.actions[2].note").value("Sin descargos"))
                .andExpect(jsonPath("$.stageDeadline")
                        .value(BusinessDays.add(AppClock.today(), SanctionTypes.EVIDENCE_DAYS).toString()));
        assertThat(auditRepository.countByAction("SANCTION_ADVANCED")).isEqualTo(audited + 1);
    }

    @Test
    @DisplayName("TC-HU23-03 · AC-23.4 · Descargos, suspensión del permiso, recurso y cierre")
    void fullLifecycleWithSuspensionAndAppeal() throws Exception {
        String id = extract(open(inspection(dynamite.getId())).andReturn().getResponse().getContentAsString(), "id");
        mockMvc.perform(get("/api/v1/procesos-sancionatorios/" + id).header("Authorization", fisherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedActions[0]").value("FILE_DEFENSE"));

        action(id, "avanzar", "{}").andExpect(status().isOk());
        filing(id, "descargos", "La red pertenecía a otra embarcación.").andExpect(status().isOk())
                .andExpect(jsonPath("$.defenseSubmitted").value(true));
        filing(id, "descargos", "Otra vez").andExpect(status().isUnprocessableEntity());
        // Con descargos presentados ya puede pasar a pruebas.
        action(id, "avanzar", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("EVIDENCE"));

        action(id, "decision", """
                {"sanction":"PERMIT_SUSPENSION","text":"Se comprobó el uso de explosivos a bordo."}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DECISION"))
                .andExpect(jsonPath("$.sanction").value("PERMIT_SUSPENSION"));
        assertThat(permitRepository.findById(permit.getId()).orElseThrow().getStatus())
                .isEqualTo(PermitStatus.SUSPENDED);

        filing(id, "recurso", "Solicito revisar las pruebas.").andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("APPEAL"));
        action(id, "cierre", "{}").andExpect(status().isBadRequest());
        action(id, "cierre", "{\"note\":\"Se confirma la decisión\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").exists());
    }

    @Test
    @DisplayName("TC-HU23-04 · AC-23.5 · S4 · Un pescador no ve procesos ajenos (403) ni gestiona; el analista tampoco")
    void onlyManagersAndInvestigated() throws Exception {
        String id = extract(open(inspection(dynamite.getId())).andReturn().getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/v1/procesos-sancionatorios/" + id).header("Authorization", otherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/procesos-sancionatorios").header("Authorization", otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(post("/api/v1/procesos-sancionatorios/" + id + "/avanzar").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/procesos-sancionatorios").header("Authorization", analystToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"inspectionId\":\"x\"}"))
                .andExpect(status().isForbidden());
        filing(id, "descargos", "No soy el investigado", otherToken).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-HU23-05 · AC-23.1 · Desde una denuncia fundada, con la embarcación investigada")
    void opensFromFoundedReport() throws Exception {
        MockMultipartFile request = new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, """
                {"reportType":"CLOSURE_FISHING","description":"Pesca de camarón en plena veda","occurredOn":"%s",
                 "latitude":6.21,"longitude":-77.5,"anonymous":true}""".formatted(AppClock.today()).getBytes());
        String code = extract(mockMvc.perform(multipart("/api/v1/denuncias").file(request))
                .andReturn().getResponse().getContentAsString(), "trackingCode");
        String reportId = reportRepository.findByTrackingCode(code).orElseThrow().getId();

        String json = "{\"reportId\":\"%s\",\"vesselId\":\"%s\"}".formatted(reportId, vessel.getId());
        mockMvc.perform(post("/api/v1/procesos-sancionatorios").header("Authorization", inspectorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableEntity());

        reportStatus(reportId, "INVESTIGATING");
        reportStatus(reportId, "CLOSED_FOUNDED");
        mockMvc.perform(post("/api/v1/procesos-sancionatorios").header("Authorization", inspectorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("REPORT"))
                .andExpect(jsonPath("$.reportCode").value(code))
                .andExpect(jsonPath("$.infraction").value("PROTECTED_AREA"));
    }

    @Test
    @DisplayName("Días hábiles: sin fines de semana ni festivos de Colombia (Ley Emiliani y Pascua)")
    void businessDaysSkipHolidays() {
        // Lunes 16 de noviembre de 2026: Independencia de Cartagena trasladado.
        assertThat(BusinessDays.isHoliday(LocalDate.of(2026, 11, 16))).isTrue();
        assertThat(BusinessDays.add(LocalDate.of(2026, 11, 13), 1)).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(BusinessDays.easter(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(BusinessDays.isHoliday(LocalDate.of(2026, 4, 3))).isTrue();   // Viernes Santo
        assertThat(BusinessDays.isHoliday(LocalDate.of(2026, 6, 8))).isTrue();   // Corpus Christi
        assertThat(BusinessDays.isHoliday(LocalDate.of(2026, 3, 23))).isTrue();  // San José
        assertThat(BusinessDays.isHoliday(LocalDate.of(2026, 3, 19))).isFalse();
    }

    // ------------------------------------------------------------------

    private void expireDeadline(String id) {
        SanctionProcessEntity process = processRepository.findById(id).orElseThrow();
        process.moveTo(process.getStage(), AppClock.today().minusDays(1));
        processRepository.saveAndFlush(process);
    }

    private ResultActions open(String inspectionId) throws Exception {
        return mockMvc.perform(post("/api/v1/procesos-sancionatorios").header("Authorization", inspectorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"inspectionId\":\"" + inspectionId + "\"}"));
    }

    private ResultActions action(String id, String path, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/procesos-sancionatorios/" + id + "/" + path)
                .header("Authorization", inspectorToken).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions filing(String id, String path, String text) throws Exception {
        return filing(id, path, text, fisherToken);
    }

    private ResultActions filing(String id, String path, String text, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/procesos-sancionatorios/" + id + "/" + path).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"));
    }

    private void reportStatus(String reportId, String target) throws Exception {
        mockMvc.perform(patch("/api/v1/denuncias/" + reportId + "/estado").header("Authorization", inspectorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + target + "\",\"note\":\"Revisión\"}"))
                .andExpect(status().isOk());
    }

    /** Acta firmada por el inspector; con explosivos tiene el hallazgo «Arte prohibido». */
    private String inspection(String gearId) throws Exception {
        String json = """
                {"clientId":"%s","vesselId":"%s","inspectedAt":"%s","locationText":"Bahía Solano",
                 "catches":[],"gearIds":["%s"],"otherFindings":[],"responsibleName":"Capitán",
                 "inspectorSignature":"%s","responsibleSignature":"%s","savedOffline":false}"""
                .formatted(UUID.randomUUID(), vessel.getId(), Instant.now().minus(1, ChronoUnit.HOURS), gearId,
                        signature(), signature());
        return extract(mockMvc.perform(multipart("/api/v1/inspecciones")
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes()))
                .header("Authorization", inspectorToken))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");
    }

    private static String signature() throws Exception {
        BufferedImage image = new BufferedImage(60, 30, BufferedImage.TYPE_INT_ARGB);
        image.getGraphics().drawLine(0, 0, 60, 30);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
    }
}
