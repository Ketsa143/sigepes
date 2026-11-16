package com.sigepes.backend.geo;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sigepes.backend.common.ApiException;
import com.sigepes.backend.common.ErrorCode;
import com.sigepes.backend.geo.MpaDtos.CreateMpaRequest;
import com.sigepes.backend.geo.MpaDtos.CreateMpaZoneRequest;
import com.sigepes.backend.geo.MpaDtos.MpaResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** HU-13 · Áreas Marinas Protegidas: consulta pública y registro por la autoridad. */
@RestController
@RequestMapping("/api/v1/areas-protegidas")
@Tag(name = "Áreas Marinas Protegidas")
public class MpaController {

    /** R4: la cartografía cambia poco; navegadores y CDN pueden reutilizarla 5 minutos. */
    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final MpaService mpaService;

    public MpaController(MpaService mpaService) {
        this.mpaService = mpaService;
    }

    @GetMapping
    @Operation(summary = "AMP activos con su zonificación; sin sesión. Admite zoom (simplifica) y bbox "
            + "minLat,minLon,maxLat,maxLon (filtra)")
    public ResponseEntity<List<MpaResponse>> areas(@RequestParam(required = false) Integer zoom,
            @RequestParam(required = false) String bbox) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(mpaService.areas(zoom, parseBbox(bbox)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ficha pública de un AMP: categoría, acto administrativo y zonas con artes y especies permitidas")
    public ResponseEntity<MpaResponse> area(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(PUBLIC_CACHE).body(mpaService.area(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra un AMP con su categoría y acto administrativo")
    public MpaResponse create(@Valid @RequestBody CreateMpaRequest request) {
        return mpaService.create(request);
    }

    @PostMapping("/{id}/zonas")
    @PreAuthorize("hasAnyRole('ADMIN','INSPECTOR')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agrega una zona de manejo (intangible, uso sostenible o amortiguación) al AMP")
    public MpaResponse addZone(@PathVariable String id, @Valid @RequestBody CreateMpaZoneRequest request) {
        return mpaService.addZone(id, request);
    }

    private static double[] parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR, "bbox debe ser minLat,minLon,maxLat,maxLon.");
        }
        try {
            double[] values = new double[4];
            for (int i = 0; i < 4; i++) {
                values[i] = Double.parseDouble(parts[i].trim());
                // "NaN" e "Infinity" pasan el parseo pero hacen fallar BigDecimal.valueOf con un 500.
                if (!Double.isFinite(values[i])) {
                    throw new NumberFormatException(parts[i]);
                }
            }
            if (Math.abs(values[0]) > 90 || Math.abs(values[2]) > 90 || Math.abs(values[1]) > 180
                    || Math.abs(values[3]) > 180) {
                throw ApiException.of(ErrorCode.VALIDATION_ERROR, "bbox tiene coordenadas fuera de rango.");
            }
            if (values[0] > values[2] || values[1] > values[3]) {
                throw ApiException.of(ErrorCode.VALIDATION_ERROR, "bbox tiene los extremos invertidos.");
            }
            return values;
        } catch (NumberFormatException e) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR, "bbox debe tener cuatro números.");
        }
    }
}
