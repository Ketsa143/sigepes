package com.sigepes.backend.environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HU-14 · Temperatura superficial del mar y altura de ola de Open-Meteo Marine, que publica sin clave
 * los modelos oceánicos de NOAA y Copernicus. Una sola petición trae todas las zonas.
 */
@Component
public class OpenMeteoMarineSource implements MarineDataSource {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http;
    private final Duration timeout;

    public OpenMeteoMarineSource(
            @Value("${sigepes.environment.open-meteo-url:https://marine-api.open-meteo.com/v1/marine}") String baseUrl,
            @Value("${sigepes.environment.timeout-seconds:8}") long timeoutSeconds) {
        this.baseUrl = baseUrl;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String name() {
        return "Open-Meteo Marine";
    }

    @Override
    public Map<String, Sample> fetch(List<EnvZoneEntity> zones) throws Exception {
        if (zones.isEmpty()) {
            return Map.of();
        }
        String latitudes = zones.stream().map(zone -> coordinate(zone.getLatitude().doubleValue()))
                .collect(Collectors.joining(","));
        String longitudes = zones.stream().map(zone -> coordinate(zone.getLongitude().doubleValue()))
                .collect(Collectors.joining(","));
        URI uri = URI.create(baseUrl + "?latitude=" + latitudes + "&longitude=" + longitudes
                + "&current=sea_surface_temperature,wave_height&timezone=UTC");
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Open-Meteo respondió " + response.statusCode());
        }
        return parse(response.body(), zones);
    }

    /** Con varias coordenadas Open-Meteo devuelve un arreglo en el mismo orden; con una, un objeto. */
    static Map<String, Sample> parse(String body, List<EnvZoneEntity> zones) throws Exception {
        JsonNode root = JSON.readTree(body);
        Map<String, Sample> samples = new HashMap<>();
        for (int i = 0; i < zones.size(); i++) {
            JsonNode node = root.isArray() ? root.get(i) : (i == 0 ? root : null);
            JsonNode current = node == null ? null : node.get("current");
            if (current == null || !current.hasNonNull("time")) {
                continue;
            }
            Double sst = number(current.get("sea_surface_temperature"));
            Double wave = number(current.get("wave_height"));
            if (sst == null && wave == null) {
                continue;
            }
            Instant observedAt = LocalDateTime.parse(current.get("time").asText()).toInstant(ZoneOffset.UTC);
            samples.put(zones.get(i).getId(), new Sample(observedAt, sst, null, wave));
        }
        return samples;
    }

    private static Double number(JsonNode node) {
        return node == null || node.isNull() || !node.isNumber() ? null : node.asDouble();
    }

    private static String coordinate(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
