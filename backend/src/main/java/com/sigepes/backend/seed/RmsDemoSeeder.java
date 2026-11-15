package com.sigepes.backend.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.rms.CatchEffortHistoryEntity;
import com.sigepes.backend.rms.CatchEffortHistoryRepository;
import com.sigepes.backend.rms.RmsRunEntity;
import com.sigepes.backend.rms.RmsRunRepository;
import com.sigepes.backend.species.SpeciesRepository;

/**
 * HU-17 · Series DEMO de captura y esfuerzo para probar el RMS (escenarios del Gherkin):
 * «Atún aleta amarilla» con 10 años y «Camarón blanco» con 3 (cálculo indicativo).
 *
 * <p>Las series son <b>sintéticas</b>, generadas con el propio modelo de Schaefer más una variación
 * pequeña; no son estadísticas oficiales. Corre al terminar el arranque (después de
 * {@link DemoDataSeeder}), también sobre una base que ya tiene datos, y solo si todavía no hay
 * historia cargada. Deja un cálculo pendiente que {@code RmsConfig} procesa a continuación.
 */
@Component
@Profile("!test")
public class RmsDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(RmsDemoSeeder.class);
    private static final String SOURCE = "DEMO · serie sintética para pruebas (no oficial)";

    /** Variación fija (±5 %) para que la serie no sea una curva perfecta. */
    private static final double[] NOISE = { 0.03, -0.04, 0.02, 0.05, -0.02, -0.05, 0.04, -0.01, 0.02, -0.03 };

    private final boolean enabled;
    private final CatchEffortHistoryRepository historyRepository;
    private final SpeciesRepository speciesRepository;
    private final RmsRunRepository runRepository;

    public RmsDemoSeeder(@Value("${sigepes.seed.enabled:true}") boolean enabled,
            CatchEffortHistoryRepository historyRepository, SpeciesRepository speciesRepository,
            RmsRunRepository runRepository) {
        this.enabled = enabled;
        this.historyRepository = historyRepository;
        this.speciesRepository = speciesRepository;
        this.runRepository = runRepository;
    }

    /** Va antes de que {@code RmsConfig} retome los cálculos pendientes (orden 10). */
    @EventListener(ApplicationReadyEvent.class)
    @Order(0)
    @Transactional
    public void seedIfMissing() {
        if (!enabled || historyRepository.count() > 0) {
            return;
        }
        int lastYear = AppClock.today().getYear() - 1;
        int series = 0;
        series += seed("Atún aleta amarilla", 0.45, 1_600_000, 0.0004,
                new int[] { 250, 320, 400, 480, 560, 640, 720, 800, 860, 900 }, lastYear);
        series += seed("Camarón blanco", 0.9, 400_000, 0.0008, new int[] { 300, 350, 400 }, lastYear);
        if (series > 0) {
            runRepository.save(new RmsRunEntity("rms-demo-inicial", null));
            log.info("Series DEMO de RMS cargadas para {} especies; el cálculo inicial queda en cola.", series);
        }
    }

    private int seed(String commonName, double r, double k, double q, int[] effort, int lastYear) {
        return speciesRepository.findByCommonNameIgnoreCase(commonName).map(species -> {
            int firstYear = lastYear - effort.length + 1;
            double biomass = k;
            for (int t = 0; t < effort.length; t++) {
                double catchKg = q * effort[t] * biomass;
                double observed = catchKg * (1 + NOISE[t % NOISE.length]);
                int year = firstYear + t;
                CatchEffortHistoryEntity row = new CatchEffortHistoryEntity("hce-demo-" + species.getId() + "-" + year,
                        species.getId(), year, BigDecimal.valueOf(observed).setScale(2, RoundingMode.HALF_UP),
                        effort[t], SOURCE);
                row.setDemo(true);
                historyRepository.save(row);
                biomass = Math.max(biomass + r * biomass * (1 - biomass / k) - catchKg, 1);
            }
            return 1;
        }).orElse(0);
    }
}
