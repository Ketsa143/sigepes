package com.sigepes.backend.education;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sigepes.backend.education.EducationDtos.AdminCourse;
import com.sigepes.backend.education.EducationDtos.AttemptRequest;
import com.sigepes.backend.education.EducationDtos.AttemptResult;
import com.sigepes.backend.education.EducationDtos.CourseCard;
import com.sigepes.backend.education.EducationDtos.CourseDetail;
import com.sigepes.backend.education.EducationDtos.CourseInput;
import com.sigepes.backend.education.EducationDtos.PublicCertificate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** HU-19 · Módulo educativo de pesca responsable. */
@RestController
@RequestMapping("/api/v1/educacion")
@Tag(name = "Educación")
public class EducationController {

    private final EducationService service;
    private final CertificatePdf pdf;

    public EducationController(EducationService service, CertificatePdf pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    @GetMapping("/cursos")
    @Operation(summary = "Cursos publicados con el avance de quien consulta")
    public List<CourseCard> courses() {
        return service.courses();
    }

    @GetMapping("/cursos/{id}")
    @Operation(summary = "Curso completo (lecciones y preguntas sin respuestas) para verlo también sin señal")
    public CourseDetail course(@PathVariable String id) {
        return service.course(id);
    }

    @PostMapping("/cursos/{id}/lecciones/{lessonId}/completar")
    @Operation(summary = "Marca la lección como vista (idempotente)")
    public CourseDetail complete(@PathVariable String id, @PathVariable String lessonId) {
        return service.completeLesson(id, lessonId);
    }

    @PostMapping("/cursos/{id}/intentos")
    @Operation(summary = "Califica el cuestionario; con la nota mínima emite la constancia")
    public AttemptResult attempt(@PathVariable String id, @Valid @RequestBody AttemptRequest request) {
        return service.attempt(id, request.answers());
    }

    @GetMapping(value = "/constancias/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Constancia en PDF con su código de verificación")
    public ResponseEntity<byte[]> certificate(@PathVariable String id) {
        var certificate = service.ownCertificate(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("constancia-" + certificate.getCode() + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.render(certificate));
    }

    @GetMapping("/constancias/verificar/{code}")
    @Operation(summary = "Verificación pública de una constancia por su código")
    public PublicCertificate verify(@PathVariable String code) {
        return service.verify(code);
    }

    // --- ADMIN (AC-19.4) ------------------------------------------------------

    @GetMapping("/admin/cursos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Todos los cursos, publicados y borradores, con las respuestas correctas")
    public List<AdminCourse> adminCourses() {
        return service.adminCourses();
    }

    @PostMapping("/admin/cursos")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un curso en borrador")
    public AdminCourse create(@Valid @RequestBody CourseInput input) {
        return service.create(input);
    }

    @PutMapping("/admin/cursos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reemplaza el contenido del curso y sube su versión")
    public AdminCourse update(@PathVariable String id, @Valid @RequestBody CourseInput input) {
        return service.update(id, input);
    }

    @PostMapping("/admin/cursos/{id}/publicar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publica el curso")
    public AdminCourse publish(@PathVariable String id) {
        return service.setPublished(id, true);
    }

    @PostMapping("/admin/cursos/{id}/retirar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Retira el curso (vuelve a borrador)")
    public AdminCourse unpublish(@PathVariable String id) {
        return service.setPublished(id, false);
    }
}
