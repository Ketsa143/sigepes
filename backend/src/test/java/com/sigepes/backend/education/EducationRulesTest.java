package com.sigepes.backend.education;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.sigepes.backend.support.ApiTestSupport;
import com.sigepes.backend.users.Role;

/** HU-19 · Módulo educativo: gestión por el ADMIN, avance, cuestionario, constancia y verificación. */
class EducationRulesTest extends ApiTestSupport {

    /** Curso con 2 lecciones y 4 preguntas; la respuesta correcta siempre es la opción 0. */
    private static final String COURSE = """
            {"title":"%s","summary":"Curso de prueba","icon":"ruler","passScore":70,
             "lessons":[{"title":"Lección 1","body":"Texto 1","minutes":2},
                        {"title":"Lección 2","body":"Texto 2","imageUrl":"/img/talla.png","minutes":3}],
             "questions":[{"prompt":"P1","options":["Sí","No"],"correctIndex":0},
                          {"prompt":"P2","options":["Sí","No"],"correctIndex":0},
                          {"prompt":"P3","options":["Sí","No","Tal vez"],"correctIndex":0,"explanation":"Porque sí"},
                          {"prompt":"P4","options":["Sí","No"],"correctIndex":0}]}""";

    private String adminToken;
    private String fisherToken;

    @BeforeEach
    void setUp() throws Exception {
        givenUser("3030303030", "admin.edu@sigepes.gov.co", Role.ADMIN);
        givenUser("3131313131", "pescador.edu@sigepes.gov.co", Role.FISHER);
        adminToken = bearerFor("admin.edu@sigepes.gov.co");
        fisherToken = bearerFor("pescador.edu@sigepes.gov.co");
    }

    @Test
    @DisplayName("TC-HU19-01 · AC-19.4 · El ADMIN crea un curso en borrador y lo publica sin desplegar código")
    void adminPublishesWithoutDeploy() throws Exception {
        String id = create("Tallas edu").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        String courseId = extract(id, "id");

        mockMvc.perform(get("/api/v1/educacion/cursos").header("Authorization", fisherToken))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/educacion/cursos/" + courseId).header("Authorization", fisherToken))
                .andExpect(status().isNotFound());

        publish(courseId);
        mockMvc.perform(get("/api/v1/educacion/cursos").header("Authorization", fisherToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lessons").value(2))
                .andExpect(jsonPath("$[0].minutes").value(5));

        // Un pescador no gestiona cursos.
        mockMvc.perform(post("/api/v1/educacion/admin/cursos").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON).content(COURSE.formatted("Otro")))
                .andExpect(status().isForbidden());
        // La respuesta correcta debe estar entre las opciones.
        mockMvc.perform(post("/api/v1/educacion/admin/cursos").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(COURSE.formatted("Malo").replace(
                        "\"correctIndex\":0,\"explanation\"", "\"correctIndex\":7,\"explanation\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-HU19-02 · AC-19.1 / AC-19.2 · El curso llega sin respuestas y el avance es idempotente")
    void courseHidesAnswersAndProgressIsIdempotent() throws Exception {
        String courseId = published("Vedas edu");
        String detail = mockMvc.perform(get("/api/v1/educacion/cursos/" + courseId).header("Authorization", fisherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessons.length()").value(2))
                .andExpect(jsonPath("$.questions[2].options.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain("correctIndex").doesNotContain("Porque sí");

        String lessonId = extract(detail.substring(detail.indexOf("\"lessons\"")), "id");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/educacion/cursos/" + courseId + "/lecciones/" + lessonId + "/completar")
                    .header("Authorization", fisherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completedLessons").value(1))
                    .andExpect(jsonPath("$.lessons[0].completed").value(true));
        }
    }

    @Test
    @DisplayName("TC-HU19-03 · AC-19.3 · Con 50 % no aprueba; con 75 % recibe la constancia en PDF, verificable")
    void certificateFromSeventyPercent() throws Exception {
        String courseId = published("AMP edu");
        attempt(courseId, "[0,0,1,1]").andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(50))
                .andExpect(jsonPath("$.passed").value(false))
                .andExpect(jsonPath("$.certificateCode").doesNotExist())
                .andExpect(jsonPath("$.answers[2].explanation").value("Porque sí"));

        String result = attempt(courseId, "[0,0,0,1]").andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(75))
                .andExpect(jsonPath("$.passed").value(true))
                .andReturn().getResponse().getContentAsString();
        String code = extract(result, "certificateCode");
        String certificateId = extract(result, "certificateId");
        assertThat(code).startsWith("CST-");

        byte[] pdf = mockMvc.perform(get("/api/v1/educacion/constancias/" + certificateId + "/pdf")
                .header("Authorization", fisherToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        mockMvc.perform(get("/api/v1/educacion/constancias/verificar/" + code.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseTitle").value("AMP edu"))
                .andExpect(jsonPath("$.score").value(75));
        // Una sola constancia por curso, aunque vuelva a aprobar.
        attempt(courseId, "[0,0,0,0]").andExpect(jsonPath("$.certificateCode").value(code));
        // Hay que responder todas las preguntas.
        attempt(courseId, "[0]").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-HU19-04 · AC-19.4 · Editar el curso sube la versión: los teléfonos renuevan su copia")
    void editingBumpsVersion() throws Exception {
        String courseId = published("Prácticas edu");
        mockMvc.perform(put("/api/v1/educacion/admin/cursos/" + courseId).header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(COURSE.formatted("Prácticas edu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/educacion/cursos/" + courseId).header("Authorization", fisherToken))
                .andExpect(jsonPath("$.version").value(2));
    }

    private ResultActions create(String title) throws Exception {
        return mockMvc.perform(post("/api/v1/educacion/admin/cursos").header("Authorization", adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(COURSE.formatted(title)));
    }

    private void publish(String courseId) throws Exception {
        mockMvc.perform(post("/api/v1/educacion/admin/cursos/" + courseId + "/publicar").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    private String published(String title) throws Exception {
        String courseId = extract(create(title).andReturn().getResponse().getContentAsString(), "id");
        publish(courseId);
        return courseId;
    }

    private ResultActions attempt(String courseId, String answers) throws Exception {
        return mockMvc.perform(post("/api/v1/educacion/cursos/" + courseId + "/intentos").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"answers\":" + answers + "}"));
    }
}
