package com.sigepes.backend.rms;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sigepes.backend.rms.RmsDtos.CreateProposalRequest;
import com.sigepes.backend.rms.RmsDtos.DecisionRequest;
import com.sigepes.backend.rms.RmsDtos.EstimateResponse;
import com.sigepes.backend.rms.RmsDtos.ProposalResponse;
import com.sigepes.backend.rms.RmsDtos.RunResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** HU-17 · RF-17 · Rendimiento Máximo Sostenible y propuestas de cuota. */
@RestController
@RequestMapping("/api/v1/rms")
@Tag(name = "Rendimiento Máximo Sostenible")
@PreAuthorize("hasAnyRole('ANALYST','ADMIN','INSPECTOR')")
public class RmsController {

    private final RmsService rmsService;

    public RmsController(RmsService rmsService) {
        this.rmsService = rmsService;
    }

    @PostMapping("/calculos")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Encola el recálculo del RMS de todas las especies (corre en segundo plano)")
    public RunResponse requestRun() {
        return rmsService.requestRun();
    }

    @GetMapping("/calculos/ultimo")
    @Operation(summary = "Estado del último cálculo de RMS")
    public ResponseEntity<RunResponse> latestRun() {
        return rmsService.latestRun().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/calculos/{id}")
    @Operation(summary = "Estado de un cálculo de RMS")
    public RunResponse run(@PathVariable String id) {
        return rmsService.run(id);
    }

    @GetMapping
    @Operation(summary = "RMS precalculado de cada especie: RMS, f-RMS, r, K y estado del recurso")
    public List<EstimateResponse> estimates() {
        return rmsService.estimates();
    }

    @GetMapping("/especies/{speciesId}")
    @Operation(summary = "RMS de una especie con su serie de captura y esfuerzo y la curva de producción")
    public EstimateResponse estimate(@PathVariable String speciesId) {
        return rmsService.estimate(speciesId);
    }

    @PostMapping("/especies/{speciesId}/propuestas")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Propone una cuota menor o igual al RMS de la especie")
    public ProposalResponse propose(@PathVariable String speciesId, @Valid @RequestBody CreateProposalRequest request) {
        return rmsService.propose(speciesId, request);
    }

    @GetMapping("/propuestas")
    @Operation(summary = "Propuestas de cuota basadas en el RMS")
    public List<ProposalResponse> proposals() {
        return rmsService.proposals();
    }

    @PostMapping("/propuestas/{id}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aprueba la propuesta y crea la cuota (HU-07); si el RMS es indicativo exige nota")
    public ProposalResponse approve(@PathVariable String id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return rmsService.approve(id, request);
    }

    @PostMapping("/propuestas/{id}/rechazar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rechaza la propuesta de cuota")
    public ProposalResponse reject(@PathVariable String id, @Valid @RequestBody(required = false) DecisionRequest request) {
        return rmsService.reject(id, request);
    }
}
