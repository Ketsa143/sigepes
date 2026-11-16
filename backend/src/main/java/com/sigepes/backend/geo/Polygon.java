package com.sigepes.backend.geo;

import java.util.ArrayList;
import java.util.List;

import com.sigepes.backend.common.ApiException;
import com.sigepes.backend.common.ErrorCode;

/**
 * Polígono geográfico simple (lista de vértices {@code [lat, lon]}) con las operaciones que necesita
 * RF-04.
 *
 * <p>Las zonas costeras de interés son pequeñas (decenas de km), así que se trabaja en coordenadas
 * planas lat/lon: el error de no proyectar es despreciable frente a la precisión del GPS de un
 * teléfono.
 */
public record Polygon(List<double[]> vertices) {

    public static Polygon validated(List<double[]> vertices) {
        if (vertices == null || vertices.size() < 3) {
            throw ApiException.of(ErrorCode.INVALID_ZONE_POLYGON, "El polígono necesita al menos 3 vértices.");
        }
        List<double[]> rounded = new ArrayList<>(vertices.size());
        for (double[] vertex : vertices) {
            // Double.isFinite descarta NaN e infinitos, que Math.abs(...) > 90 deja pasar y que
            // romperían el JSON guardado en la columna polygon.
            if (vertex == null || vertex.length != 2 || !Double.isFinite(vertex[0]) || !Double.isFinite(vertex[1])
                    || Math.abs(vertex[0]) > 90 || Math.abs(vertex[1]) > 180) {
                throw ApiException.of(ErrorCode.INVALID_ZONE_POLYGON,
                        "Cada vértice debe ser [latitud, longitud] con valores válidos.");
            }
            // Seis decimales (≈ 11 cm) bastan para un GPS y acotan el JSON: 300 vértices con 17 cifras
            // cada uno superaban los 12 000 caracteres de la columna polygon.
            rounded.add(new double[] { round6(vertex[0]), round6(vertex[1]) });
        }
        Polygon polygon = new Polygon(List.copyOf(rounded));
        if (polygon.selfIntersects()) {
            throw ApiException.of(ErrorCode.INVALID_ZONE_POLYGON, "Los lados del polígono no pueden cruzarse.");
        }
        return polygon;
    }

    private static double round6(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    public double minLat() {
        return vertices.stream().mapToDouble(v -> v[0]).min().orElseThrow();
    }

    public double maxLat() {
        return vertices.stream().mapToDouble(v -> v[0]).max().orElseThrow();
    }

    public double minLon() {
        return vertices.stream().mapToDouble(v -> v[1]).min().orElseThrow();
    }

    public double maxLon() {
        return vertices.stream().mapToDouble(v -> v[1]).max().orElseThrow();
    }

    /** Prueba de punto en polígono por cruce de rayos (Jordan). */
    public boolean contains(double lat, double lon) {
        boolean inside = false;
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double latI = vertices.get(i)[0];
            double lonI = vertices.get(i)[1];
            double latJ = vertices.get(j)[0];
            double lonJ = vertices.get(j)[1];
            boolean crosses = (latI > lat) != (latJ > lat)
                    && lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI;
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * HU-13 · R4 · Simplificación Douglas–Peucker para pintar el mapa: descarta los vértices que se
     * apartan menos de {@code tolerance} grados de la línea que los rodea. Nunca baja de 3 vértices;
     * si no puede simplificar devuelve el mismo polígono.
     */
    public Polygon simplified(double tolerance) {
        int n = vertices.size();
        if (tolerance <= 0 || n <= 4) {
            return this;
        }
        // Un anillo cerrado se parte en dos líneas: del primer vértice al más lejano y de vuelta.
        int far = 0;
        double best = -1;
        double[] origin = vertices.get(0);
        for (int i = 1; i < n; i++) {
            double[] v = vertices.get(i);
            double d = Math.hypot(v[0] - origin[0], v[1] - origin[1]);
            if (d > best) {
                best = d;
                far = i;
            }
        }
        List<double[]> first = new ArrayList<>(vertices.subList(0, far + 1));
        List<double[]> second = new ArrayList<>(vertices.subList(far, n));
        second.add(origin);

        List<double[]> result = new ArrayList<>(douglasPeucker(first, tolerance));
        List<double[]> back = douglasPeucker(second, tolerance);
        result.addAll(back.subList(1, back.size() - 1));
        return result.size() >= 3 && result.size() < n ? new Polygon(List.copyOf(result)) : this;
    }

    private static List<double[]> douglasPeucker(List<double[]> line, double tolerance) {
        if (line.size() <= 2) {
            return line;
        }
        double[] start = line.get(0);
        double[] end = line.get(line.size() - 1);
        int index = 0;
        double max = 0;
        for (int i = 1; i < line.size() - 1; i++) {
            double d = distanceToSegment(line.get(i), start, end);
            if (d > max) {
                max = d;
                index = i;
            }
        }
        if (max <= tolerance) {
            return List.of(start, end);
        }
        List<double[]> left = douglasPeucker(line.subList(0, index + 1), tolerance);
        List<double[]> right = douglasPeucker(line.subList(index, line.size()), tolerance);
        List<double[]> merged = new ArrayList<>(left.subList(0, left.size() - 1));
        merged.addAll(right);
        return merged;
    }

    private static double distanceToSegment(double[] p, double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0) {
            return Math.hypot(p[0] - a[0], p[1] - a[1]);
        }
        double t = Math.max(0, Math.min(1, ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / lengthSq));
        return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
    }

    /** ¿Algún par de lados no adyacentes se cruza? (n es pequeño: O(n²) basta). */
    boolean selfIntersects() {
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            double[] a1 = vertices.get(i);
            double[] a2 = vertices.get((i + 1) % n);
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(i - j) <= 1 || (i == 0 && j == n - 1)) {
                    continue;
                }
                double[] b1 = vertices.get(j);
                double[] b2 = vertices.get((j + 1) % n);
                if (segmentsCross(a1, a2, b1, b2)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentsCross(double[] p1, double[] p2, double[] q1, double[] q2) {
        double d1 = orientation(q1, q2, p1);
        double d2 = orientation(q1, q2, p2);
        double d3 = orientation(p1, p2, q1);
        double d4 = orientation(p1, p2, q2);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double orientation(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }
}
