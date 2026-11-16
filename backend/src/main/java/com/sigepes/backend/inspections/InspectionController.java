package com.sigepes.backend.inspections;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sigepes.backend.inspections.InspectionDtos.CreateInspectionRequest;
import com.sigepes.backend.inspections.InspectionDtos.InspectionResponse;
import com.sigepes.backend.inspections.InspectionDtos.InspectionSummary;
import com.sigepes.backend.inspections.InspectionDtos.PublicInspection;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** HU-22 · Inspecciones en campo con acta digital. */
@RestController
@RequestMapping("/api/v1/inspecciones")
@Tag(name = "Inspecciones")
public class InspectionController {

    private final InspectionService service;
    private final InspectionPdf pdf;

    public InspectionController(InspectionService service, InspectionPdf pdf) {
        this.service = service;
        this.pdf = pdf;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('INSPECTOR','ADMIN')")
    @Operation(summary = "Registra un acta firmada (idempotente por clientId: 201 nueva, 200 si ya había llegado)")
    public ResponseEntity<InspectionResponse> register(@RequestPart("request") @Valid CreateInspectionRequest request,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        InspectionService.Registered registered = service.register(request, photos);
        return ResponseEntity.status(registered.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(registered.inspection());
    }

    @GetMapping
    @Operation(summary = "Actas: la autoridad ve todas; el armador, las de sus embarcaciones")
    public List<InspectionSummary> list(@RequestParam(required = false) String embarcacionId) {
        return service.list(embarcacionId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle del acta con lo revisado, los hallazgos y el sello")
    public InspectionResponse detail(@PathVariable String id) {
        return service.detail(id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Acta en PDF con las firmas y el código de verificación")
    public ResponseEntity<byte[]> pdf(@PathVariable String id) {
        byte[] content = pdf.render(id);
        String code = service.detail(id).code();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("acta-" + code + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    @GetMapping("/verificar/{code}")
    @Operation(summary = "Verificación pública del acta por su código: datos básicos y estado del sello")
    public PublicInspection verify(@PathVariable String code) {
        return service.verify(code);
    }

    /** AC-22.5 · S4: el acta firmada es inmutable; el intento se rechaza (409/403) y queda auditado. */
    @PutMapping("/{id}")
    @Operation(summary = "No permitido: un acta firmada no se modifica (409 y auditoría)")
    public void replace(@PathVariable String id) {
        service.rejectModification(id, "reemplazar");
    }

    @PatchMapping("/{id}")
    @Operation(summary = "No permitido: un acta firmada no se modifica (409 y auditoría)")
    public void patch(@PathVariable String id) {
        service.rejectModification(id, "modificar");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "No permitido: un acta firmada no se borra (409 y auditoría)")
    public void delete(@PathVariable String id) {
        service.rejectModification(id, "borrar");
    }
}
