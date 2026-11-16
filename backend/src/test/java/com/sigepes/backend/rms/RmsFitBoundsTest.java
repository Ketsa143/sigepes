package com.sigepes.backend.rms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Revisión HU-17 · Un ajuste que no cabe en rms_estimates no debe tumbar el cálculo de todas las especies. */
class RmsFitBoundsTest {

    @Test
    @DisplayName("Un ajuste normal cabe en las columnas de rms_estimates")
    void normalFitFits() {
        SchaeferModel.Fit fit = new SchaeferModel.Fit(0.45, 1_600_000, 0.0004, 180_000, 562.5, 1.1, 0.9, 0.93,
                SchaeferModel.StockStatus.FULLY_EXPLOITED);
        assertThat(RmsJobRunner.fitsColumns(fit)).isTrue();
    }

    @Test
    @DisplayName("Una biomasa colapsada (razón de pesca de millones) se descarta en lugar de desbordar NUMERIC(10,4)")
    void collapsedBiomassIsDiscarded() {
        SchaeferModel.Fit fit = new SchaeferModel.Fit(0.05, 1_000_000, 0.0004, 12_500, 62.5, 0.000002, 2.0e7, 0.4,
                SchaeferModel.StockStatus.OVER_EXPLOITED);
        assertThat(RmsJobRunner.fitsColumns(fit)).isFalse();
    }
}
