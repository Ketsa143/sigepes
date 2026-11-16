package com.sigepes.backend.environment;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sigepes.backend.environment.EnvironmentDtos.ImportResponse;
import com.sigepes.backend.environment.EnvironmentDtos.OverviewResponse;
import com.sigepes.backend.environment.EnvironmentDtos.SeriesResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** HU-14 · RF-14 · Monitoreo de datos ambientales por zona de pesca. */
@RestController
@RequestMapping("/api/v1/ambiental")
@Tag(name = "Datos ambientales")
@PreAuthorize("hasAnyRole('ANALYST','ADMIN','INSPECTOR')")
public class EnvironmentController {

    private final EnvironmentService service;

    public EnvironmentController(EnvironmentService service) {
        this.service = service;
    }

    @GetMapping("/zonas")
    @Operation(summary = "Zonas de pesca con su última lectura ambiental y el estado de las fuentes")
    public OverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/zonas/{id}/serie")
    @Operation(summary = "Serie de temperatura, clorofila y oleaje de la zona en el periodo; con especieId "
            + "superpone las capturas de esa especie en la zona")
    public SeriesResponse series(@PathVariable String id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String especieId) {
        return service.series(id, desde, hasta, especieId);
    }

    @PostMapping("/importaciones")
    @PreAuthorize("hasAnyRole('ANALYST','ADMIN')")
    @Operation(summary = "Importa ahora las lecturas de las fuentes externas (además de la importación cada 6 h)")
    public ImportResponse importNow() {
        return service.importNow();
    }
}
