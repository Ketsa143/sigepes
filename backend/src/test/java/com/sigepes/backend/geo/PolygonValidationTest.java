package com.sigepes.backend.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sigepes.backend.common.ApiException;

/** Revisión HU-13 · Validación de vértices y tamaño del polígono guardado. */
class PolygonValidationTest {

    @Test
    @DisplayName("Un vértice NaN o infinito se rechaza en lugar de guardarse como JSON inválido")
    void nonFiniteVerticesAreRejected() {
        assertThatThrownBy(() -> Polygon.validated(List.of(new double[] { Double.NaN, -77.3 },
                new double[] { 6.0, -77.2 }, new double[] { 6.1, -77.2 })))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Polygon.validated(List.of(new double[] { 6.0, Double.POSITIVE_INFINITY },
                new double[] { 6.0, -77.2 }, new double[] { 6.1, -77.2 })))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Los vértices se redondean a 6 decimales y 300 vértices caben en la columna polygon")
    void verticesAreRoundedAndFitTheColumn() {
        List<double[]> vertices = new ArrayList<>();
        // Círculo de 300 vértices con coordenadas de 15+ decimales, como las que manda un mapa web.
        for (int i = 0; i < 300; i++) {
            double angle = 2 * Math.PI * i / 300;
            vertices.add(new double[] { -12.345678912345678 + 0.1 * Math.sin(angle),
                    -77.123456789012345 + 0.1 * Math.cos(angle) });
        }
        Polygon polygon = Polygon.validated(vertices);

        assertThat(polygon.vertices().get(0)[1]).isEqualTo(-77.023457);
        RestrictedZoneEntity zone = new RestrictedZoneEntity("zon-x", "Prueba", ZoneType.MPA, polygon, "RES-1");
        // La columna restricted_zones.polygon admite 12 000 caracteres.
        assertThat(zone.polygon().vertices()).hasSize(300);
        assertThat(polygonJsonLength(polygon)).isLessThan(12_000);
    }

    private static int polygonJsonLength(Polygon polygon) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(polygon.vertices()).length();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
