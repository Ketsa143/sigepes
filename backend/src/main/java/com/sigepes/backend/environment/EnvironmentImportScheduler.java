package com.sigepes.backend.environment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * HU-14 · AC-14.1 · Importa las variables ambientales al arrancar y luego cada 6 h. Se desactiva con
 * {@code sigepes.environment.import-enabled=false} (las pruebas lo invocan a mano).
 */
@Component
@ConditionalOnProperty(name = "sigepes.environment.import-enabled", havingValue = "true", matchIfMissing = true)
public class EnvironmentImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentImportScheduler.class);

    private final EnvironmentImporter importer;

    public EnvironmentImportScheduler(EnvironmentImporter importer) {
        this.importer = importer;
    }

    @Scheduled(initialDelayString = "${sigepes.environment.import-initial-delay-ms:60000}",
            fixedDelayString = "${sigepes.environment.import-interval-ms:21600000}")
    public void run() {
        try {
            EnvironmentImporter.ImportResult result = importer.importNow();
            log.info("Importación ambiental: {} lectura(s) nuevas, {} anomalía(s).", result.readingsStored(),
                    result.anomalies());
        } catch (RuntimeException e) {
            // Un fallo no debe detener el programador; la siguiente importación lo reintenta.
            log.warn("La importación ambiental falló: {}", e.getMessage());
        }
    }
}
