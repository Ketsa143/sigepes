package com.sigepes.backend.geo;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HU-13 · Contratos de la API de Áreas Marinas Protegidas. */
public final class MpaDtos {

    private MpaDtos() {
    }

    /** Arte o especie permitida, con su nombre para mostrarlo en la ficha pública. */
    public record NamedRef(String id, String name) {
    }

    /** Zona de manejo de un AMP con su polígono (simplificado si se pidió un zoom). */
    public record MpaZoneResponse(
            String id,
            String name,
            String kind,
            List<double[]> polygon,
            String description,
            List<NamedRef> allowedGears,
            List<NamedRef> allowedSpecies) {
    }

    public record MpaResponse(
            String id,
            String name,
            String category,
            String legalAct,
            String authority,
            String description,
            List<MpaZoneResponse> zones) {
    }

    public record CreateMpaRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 160, message = "El nombre no puede superar 160 caracteres")
            String name,

            @NotBlank(message = "La categoría es obligatoria")
            @Pattern(regexp = "NATIONAL_PARK|DMI|SANCTUARY|OTHER", message = "La categoría no es válida")
            String category,

            @NotBlank(message = "El acto administrativo es obligatorio")
            @Size(max = 160, message = "El acto administrativo no puede superar 160 caracteres")
            String legalAct,

            @Size(max = 120, message = "La autoridad no puede superar 120 caracteres")
            String authority,

            @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres")
            String description) {
    }

    public record CreateMpaZoneRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 160, message = "El nombre no puede superar 160 caracteres")
            String name,

            @NotBlank(message = "El tipo de zona de manejo es obligatorio")
            @Pattern(regexp = "INTANGIBLE|SUSTAINABLE_USE|BUFFER", message = "El tipo de zona de manejo no es válido")
            String kind,

            @NotNull(message = "El polígono es obligatorio")
            @Size(min = 3, max = 300, message = "El polígono debe tener entre 3 y 300 vértices")
            List<double[]> polygon,

            @Size(max = 400, message = "La descripción no puede superar 400 caracteres")
            String description,

            @Size(max = 30, message = "Máximo 30 artes permitidas")
            List<String> allowedGearIds,

            @Size(max = 60, message = "Máximo 60 especies permitidas")
            List<String> allowedSpeciesIds) {
    }
}
