package com.sigepes.backend.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasLength;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
import com.sigepes.backend.common.FileStorageService;
import com.sigepes.backend.gears.FishingGearEntity;
import com.sigepes.backend.permits.PermitStatus;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.support.ApiTestSupport;
import com.sigepes.backend.users.Role;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.vessels.VesselEntity;
import com.sigepes.backend.vessels.VesselStatus;

/** HU-22 · Inspecciones en campo con acta digital: hallazgos, idempotencia, inmutabilidad y PDF. */
class InspectionRulesTest extends ApiTestSupport {

    @Autowired
    private InspectionRepository inspectionRepository;

    @Autowired
    private AuditLogRepository auditRepository;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private FileStorageService storage;

    private UserEntity fisher;
    private VesselEntity vessel;
    private FishingGearEntity handline;
    private FishingGearEntity dynamite;
    private SpeciesEntity snapper;
    private String inspectorToken;
    private String fisherToken;
    private String otherFisherToken;

    @BeforeEach
    void setUp() throws Exception {
        fisher = givenUser("6060606060", "pescador.act@sigepes.gov.co", Role.FISHER);
        givenUser("6161616161", "otro.act@sigepes.gov.co", Role.FISHER);
        givenUser("6262626262", "inspector.act@sigepes.gov.co", Role.INSPECTOR);
        handline = givenGear("Línea act", false);
        dynamite = givenGear("Explosivos act", true);
        vessel = givenVessel(fisher, "DIMAR-ACT-001", VesselStatus.ACTIVE, handline);
        givenPermit(fisher, "PES-ACT-1", PermitStatus.APPROVED, AppClock.today().minusMonths(2),
                AppClock.today().plusMonths(6));
        snapper = givenSpecies("Pargo act", new BigDecimal("25.00"));
        inspectorToken = bearerFor("inspector.act@sigepes.gov.co");
        fisherToken = bearerFor("pescador.act@sigepes.gov.co");
        otherFisherToken = bearerFor("otro.act@sigepes.gov.co");
    }

    @Test
    @DisplayName("TC-HU22-01 · AC-22.1 / AC-22.3 · Acta conforme con foto, sellada, en PDF y verificable sin sesión")
    void compliantInspectionIsSealedAndVerifiable() throws Exception {
        String body = submit(UUID.randomUUID().toString(), "27.5", handline.getId(), true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("COMPLIANT"))
                .andExpect(jsonPath("$.findings.length()").value(0))
                .andExpect(jsonPath("$.permitNumber").value("PES-ACT-1"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.photos").value(1))
                .andExpect(jsonPath("$.contentHash", hasLength(64)))
                .andReturn().getResponse().getContentAsString();
        String id = extract(body, "id");
        String code = extract(body, "code");

        byte[] pdf = mockMvc.perform(get("/api/v1/inspecciones/" + id + "/pdf").header("Authorization", fisherToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        mockMvc.perform(get("/api/v1/inspecciones/verificar/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("COMPLIANT"))
                .andExpect(jsonPath("$.integrityOk").value(true))
                .andExpect(jsonPath("$.responsibleName").doesNotExist());
    }

    @Test
    @DisplayName("TC-HU22-02 · AC-22.2 · Talla bajo la mínima y arte prohibido son hallazgos y avisan al armador")
    void findingsAreComputedAndOwnerNotified() throws Exception {
        String body = submit(UUID.randomUUID().toString(), "20", dynamite.getId(), false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result").value("WITH_FINDINGS"))
                .andExpect(jsonPath("$.findings.length()").value(3))
                .andExpect(jsonPath("$.findings[0].code").value("SIZE_BELOW_MIN"))
                .andExpect(jsonPath("$.findings[0].description")
                        .value("Talla inferior a la mínima (25 cm): Pargo act de 20 cm."))
                .andExpect(jsonPath("$.findings[1].code").value("PROHIBITED_GEAR"))
                .andExpect(jsonPath("$.findings[2].code").value("OTHER"))
                .andExpect(jsonPath("$.findings[2].automatic").value(false))
                .andExpect(jsonPath("$.items[0].finding").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(alertRepository.existsByRecipientIdAndSourceKey(fisher.getId(), "inspection:" + extract(body, "id")))
                .isTrue();
    }

    @Test
    @DisplayName("TC-HU22-03 · AC-22.4 · D5 · Reenviar la misma acta desde la cola sin señal no la duplica")
    void resendingSameClientIdIsIdempotent() throws Exception {
        String clientId = UUID.randomUUID().toString();
        String first = submit(clientId, "30", handline.getId(), false).andExpect(status().isCreated())
                .andExpect(jsonPath("$.savedOffline").value(true))
                .andReturn().getResponse().getContentAsString();
        String second = submit(clientId, "30", handline.getId(), false).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(extract(second, "id")).isEqualTo(extract(first, "id"));
        assertThat(inspectionRepository.findAll()).filteredOn(act -> act.getClientId().equals(clientId)).hasSize(1);
    }

    @Test
    @DisplayName("TC-HU22-04 · AC-22.5 · S4 · Modificar o borrar un acta firmada responde 409/403 y queda auditado")
    void signedInspectionIsImmutable() throws Exception {
        String id = extract(submit(UUID.randomUUID().toString(), "30", handline.getId(), false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");
        long before = auditRepository.countByAction("INSPECTION_TAMPER_ATTEMPT");

        mockMvc.perform(put("/api/v1/inspecciones/" + id).header("Authorization", inspectorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"COMPLIANT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSPECTION_IMMUTABLE"));
        mockMvc.perform(delete("/api/v1/inspecciones/" + id).header("Authorization", fisherToken))
                .andExpect(status().isForbidden());

        assertThat(auditRepository.countByAction("INSPECTION_TAMPER_ATTEMPT")).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("TC-HU22-05 · AC-22.5 · Si se altera un archivo del acta, la verificación lo detecta")
    void tamperingIsDetected() throws Exception {
        String body = submit(UUID.randomUUID().toString(), "30", handline.getId(), false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        InspectionEntity act = inspectionRepository.findById(extract(body, "id")).orElseThrow();
        Path signature = storage.load(act.getResponsibleSignaturePath()).getFile().toPath();
        Files.write(signature, png(3));

        mockMvc.perform(get("/api/v1/inspecciones/verificar/" + act.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integrityOk").value(false));
    }

    @Test
    @DisplayName("TC-HU22-06 · Solo la autoridad levanta actas; el armador ve solo las suyas; la firma debe ser PNG")
    void accessAndSignatureValidation() throws Exception {
        String id = extract(submit(UUID.randomUUID().toString(), "30", handline.getId(), false)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/v1/inspecciones/" + id).header("Authorization", fisherToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/inspecciones/" + id).header("Authorization", otherFisherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/v1/inspecciones").file(request(UUID.randomUUID().toString(), "30",
                handline.getId(), false, signature())).header("Authorization", fisherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/v1/inspecciones").file(request(UUID.randomUUID().toString(), "30",
                handline.getId(), false, "data:image/png;base64,SG9sYQ==")).header("Authorization", inspectorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNATURE"));
    }

    // ------------------------------------------------------------------

    private ResultActions submit(String clientId, String sizeCm, String gearId, boolean withPhoto) throws Exception {
        var builder = multipart("/api/v1/inspecciones").file(request(clientId, sizeCm, gearId, !withPhoto,
                signature()));
        if (withPhoto) {
            builder.file(new MockMultipartFile("photos", "foto.jpg", "image/jpeg", jpeg()));
        }
        return mockMvc.perform(builder.header("Authorization", inspectorToken));
    }

    private MockMultipartFile request(String clientId, String sizeCm, String gearId, boolean offline,
            String responsibleSignature) throws Exception {
        boolean prohibited = gearId.equals(dynamite.getId());
        String json = """
                {"clientId":"%s","vesselId":"%s","inspectedAt":"%s","latitude":6.21,"longitude":-77.40,
                 "locationText":"Bahía Solano, frente al muelle",
                 "catches":[{"speciesId":"%s","sizeCm":%s,"weightKg":12.5,"quantity":4}],
                 "gearIds":["%s"],
                 "otherFindings":%s,
                 "observations":"Revisión de rutina.","responsibleName":"Capitán de prueba",
                 "responsibleIdNumber":"1234567","inspectorSignature":"%s","responsibleSignature":"%s",
                 "savedOffline":%s}""".formatted(clientId, vessel.getId(),
                Instant.now().minus(2, ChronoUnit.HOURS), snapper.getId(), sizeCm, gearId,
                prohibited ? "[\"Sin chaleco salvavidas para toda la tripulación\"]" : "[]", signature(),
                responsibleSignature, offline);
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
    }

    private static String signature() throws Exception {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(png(2));
    }

    private static byte[] png(int size) throws Exception {
        BufferedImage image = new BufferedImage(size * 40, size * 20, BufferedImage.TYPE_INT_ARGB);
        image.getGraphics().drawLine(0, 0, size * 40, size * 20);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg() throws Exception {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
