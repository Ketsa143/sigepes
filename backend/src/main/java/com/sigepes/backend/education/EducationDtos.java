package com.sigepes.backend.education;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HU-19 · Contratos del módulo educativo. */
public final class EducationDtos {

    private EducationDtos() {
    }

    // --- Para el pescador -------------------------------------------------

    public record CourseCard(String id, String title, String summary, String icon, int lessons, int minutes,
            int completedLessons, boolean passed, String certificateId, String certificateCode, int version) {
    }

    public record LessonView(String id, int position, String title, String body, String imageUrl, String audioUrl,
            int minutes, boolean completed) {
    }

    /** Pregunta sin la respuesta correcta: se califica en el servidor. */
    public record QuestionView(String id, int position, String prompt, List<String> options) {
    }

    /**
     * AC-19.2 · Curso completo: el teléfono lo guarda entero para verlo sin señal. {@code version}
     * dice si la copia guardada sigue al día.
     */
    public record CourseDetail(String id, String title, String summary, String icon, int version, int passScore,
            List<LessonView> lessons, List<QuestionView> questions, int completedLessons, boolean passed,
            String certificateId, String certificateCode) {
    }

    public record AttemptRequest(@NotNull(message = "Faltan las respuestas") List<Integer> answers) {
    }

    public record AnswerResult(String questionId, boolean correct, int correctIndex, String explanation) {
    }

    public record AttemptResult(int score, int passScore, boolean passed, List<AnswerResult> answers,
            String certificateId, String certificateCode) {
    }

    /** Verificación pública de la constancia: nombre del titular, curso, nota y fecha. */
    public record PublicCertificate(String code, String holderName, String courseTitle, int score, Instant issuedAt) {
    }

    // --- Para el ADMIN (AC-19.4) -------------------------------------------

    public record LessonInput(
            @NotBlank(message = "Cada lección necesita un título") @Size(max = 160) String title,
            @NotBlank(message = "Cada lección necesita un texto") @Size(max = 4000) String body,
            @Size(max = 400) @Pattern(regexp = "^$|^(https://|/).*", message = "La imagen debe ser una URL https o una ruta del sitio") String imageUrl,
            @Size(max = 400) @Pattern(regexp = "^$|^(https://|/).*", message = "El audio debe ser una URL https o una ruta del sitio") String audioUrl,
            @Min(1) @Max(30) int minutes) {
    }

    public record QuestionInput(
            @NotBlank(message = "Cada pregunta necesita un enunciado") @Size(max = 400) String prompt,
            @Size(min = 2, max = 6, message = "Cada pregunta tiene entre 2 y 6 opciones") List<@NotBlank @Size(max = 200) String> options,
            @Min(0) int correctIndex,
            @Size(max = 600) String explanation) {
    }

    public record CourseInput(
            @NotBlank(message = "El título es obligatorio") @Size(max = 160) String title,
            @NotBlank(message = "El resumen es obligatorio") @Size(max = 600) String summary,
            @NotBlank @Pattern(regexp = "ruler|calendar|park|hand|fish|boat|book", message = "El ícono no es válido") String icon,
            @Min(1) @Max(100) int passScore,
            @NotEmpty(message = "El curso necesita al menos una lección") @Size(max = 12) List<@Valid LessonInput> lessons,
            @NotEmpty(message = "El curso necesita al menos una pregunta") @Size(max = 20) List<@Valid QuestionInput> questions) {
    }

    /** Vista completa para editar, con las respuestas correctas. */
    public record AdminCourse(String id, String title, String summary, String icon, String status, int version,
            int passScore, List<LessonInput> lessons, List<QuestionInput> questions, Instant updatedAt) {
    }
}
