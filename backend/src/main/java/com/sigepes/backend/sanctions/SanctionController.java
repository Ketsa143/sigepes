package com.sigepes.backend.sanctions;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sigepes.backend.sanctions.SanctionDtos.DecisionRequest;
import com.sigepes.backend.sanctions.SanctionDtos.FilingRequest;
import com.sigepes.backend.sanctions.SanctionDtos.NoteRequest;
import com.sigepes.backend.sanctions.SanctionDtos.OpenRequest;
import com.sigepes.backend.sanctions.SanctionDtos.ProcessResponse;
import com.sigepes.backend.sanctions.SanctionDtos.ProcessSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * HU-23 · Procesos sancionatorios. Los permisos se revisan en el servicio (AC-23.5): el inspector y
 * el administrador gestionan; el investigado consulta los suyos y presenta descargos y recurso.
 */
@RestController
@RequestMapping("/api/v1/procesos-sancionatorios")
@Tag(name = "Procesos sancionatorios")
public class SanctionController {

    private final SanctionService service;

    public SanctionController(SanctionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abre un proceso desde un acta con hallazgos o desde una denuncia fundada")
    public ProcessResponse open(@Valid @RequestBody OpenRequest request) {
        return service.open(request);
    }

    @GetMapping
    @Operation(summary = "Procesos: la autoridad ve todos; el investigado, los suyos")
    public List<ProcessSummary> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Expediente con sus actuaciones y lo que quien consulta puede hacer")
    public ProcessResponse detail(@PathVariable String id) {
        return service.detail(id);
    }

    @GetMapping("/por-acta/{inspectionId}")
    @Operation(summary = "Proceso abierto a partir de un acta, si lo hay")
    public ResponseEntity<Map<String, String>> byInspection(@PathVariable String inspectionId) {
        return service.processForInspection(inspectionId)
                .map(id -> ResponseEntity.ok(Map.of("processId", id)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/avanzar")
    @Operation(summary = "Apertura → Descargos, o Descargos → Pruebas (sin descargos si venció el plazo)")
    public ProcessResponse advance(@PathVariable String id, @Valid @RequestBody(required = false) NoteRequest request) {
        return service.advance(id, request == null ? null : request.note());
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Pruebas → Decisión: sanción (amonestación, multa en SMMLV, decomiso, suspensión) o archivo")
    public ProcessResponse decide(@PathVariable String id, @Valid @RequestBody DecisionRequest request) {
        return service.decide(id, request);
    }

    @PostMapping("/{id}/cierre")
    @Operation(summary = "Cierra el proceso: decisión en firme o recurso resuelto")
    public ProcessResponse close(@PathVariable String id, @Valid @RequestBody(required = false) NoteRequest request) {
        return service.close(id, request == null ? null : request.note());
    }

    @PostMapping("/{id}/descargos")
    @Operation(summary = "El investigado presenta sus descargos dentro del plazo")
    public ProcessResponse defense(@PathVariable String id, @Valid @RequestBody FilingRequest request) {
        return service.fileDefense(id, request.text());
    }

    @PostMapping("/{id}/recurso")
    @Operation(summary = "El investigado recurre la decisión dentro del plazo")
    public ProcessResponse appeal(@PathVariable String id, @Valid @RequestBody FilingRequest request) {
        return service.fileAppeal(id, request.text());
    }
}
