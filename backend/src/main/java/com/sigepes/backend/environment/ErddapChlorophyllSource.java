package com.sigepes.backend.environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * HU-14 · Clorofila-a (mg/m³) del servidor ERDDAP de NOAA CoastWatch: compuesto satelital de 8 días,
 * en el punto de la grilla más cercano a cada zona. Se desactiva con
 * {@code sigepes.environment.erddap-enabled=false}.
 */
@Component
@ConditionalOnProperty(name = "sigepes.environment.erddap-enabled", havingValue = "true", matchIfMissing = true)
public class ErddapChlorophyllSource implements MarineDataSource {

    private final String griddapUrl;
    private final String variable;
    private final HttpClient http;
    private final Duration timeout;

    public ErddapChlorophyllSource(
            @Value("${sigepes.environment.erddap-url:https://coastwatch.pfeg.noaa.gov/erddap/griddap/erdMH1chla8day.csv}") String griddapUrl,
            @Value("${sigepes.environment.erddap-variable:chlorophyll}") String variable,
            @Value("${sigepes.environment.timeout-seconds:8}") long timeoutSeconds) {
        this.griddapUrl = griddapUrl;
        this.variable = variable;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String name() {
        return "NOAA ERDDAP";
    }

    @Override
    public Map<String, Sample> fetch(List<EnvZoneEntity> zones) throws Exception {
        Map<String, Sample> samples = new HashMap<>();
        for (EnvZoneEntity zone : zones) {
            // Los corchetes van codificados; los paréntesis de ERDDAP se aceptan tal cual.
            String query = String.format(Locale.ROOT, "%s%%5B(last)%%5D%%5B(%.4f)%%5D%%5B(%.4f)%%5D", variable,
                    zone.getLatitude().doubleValue(), zone.getLongitude().doubleValue());
            URI uri = URI.create(griddapUrl + "?" + query);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("ERDDAP respondió " + response.statusCode());
            }
            Sample sample = parse(response.body());
            if (sample != null) {
                samples.put(zone.getId(), sample);
            }
        }
        return samples;
    }

    /**
     * CSV de griddap: encabezado, fila de unidades y una fila de datos
     * {@code time,latitude,longitude,valor}. Sobre tierra o bajo nubes el valor llega como NaN.
     */
    static Sample parse(String csv) {
        String[] lines = csv.strip().split("\\R");
        if (lines.length < 3) {
            return null;
        }
        String[] cells = lines[2].split(",");
        if (cells.length < 4) {
            return null;
        }
        try {
            double value = Double.parseDouble(cells[cells.length - 1].trim());
            if (!Double.isFinite(value) || value < 0) {
                return null;
            }
            return new Sample(Instant.parse(cells[0].trim()), null, value, null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
