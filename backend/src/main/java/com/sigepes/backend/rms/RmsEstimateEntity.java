package com.sigepes.backend.rms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * HU-17 · Resultado precalculado del RMS de una especie (una fila por especie, la del último
 * cálculo). Es la caché que leen las consultas (AC-17.5): nunca se ajusta el modelo en una petición.
 */
@Entity
@Table(name = "rms_estimates")
public class RmsEstimateEntity {

    @Id
    @Column(name = "species_id", length = 40, nullable = false)
    private String speciesId;

    @Column(name = "run_id", length = 40, nullable = false)
    private String runId;

    @Column(name = "years_count", nullable = false)
    private int yearsCount;

    @Column(name = "first_year", nullable = false)
    private int firstYear;

    @Column(name = "last_year", nullable = false)
    private int lastYear;

    @Column(name = "fitted", nullable = false)
    private boolean fitted;

    /** AC-17.4: con menos de 5 años el cálculo es indicativo. */
    @Column(name = "indicative", nullable = false)
    private boolean indicative;

    @Column(name = "r", precision = 12, scale = 6)
    private BigDecimal r;

    @Column(name = "k_kg", precision = 18, scale = 2)
    private BigDecimal kKg;

    @Column(name = "q", precision = 24, scale = 16)
    private BigDecimal q;

    @Column(name = "msy_kg", precision = 16, scale = 2)
    private BigDecimal msyKg;

    @Column(name = "f_msy", precision = 16, scale = 2)
    private BigDecimal fMsy;

    @Column(name = "biomass_ratio", precision = 10, scale = 4)
    private BigDecimal biomassRatio;

    @Column(name = "fishing_ratio", precision = 10, scale = 4)
    private BigDecimal fishingRatio;

    @Column(name = "r2", precision = 6, scale = 4)
    private BigDecimal r2;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", length = 20)
    private SchaeferModel.StockStatus stockStatus;

    /** Serie usada en el ajuste: [{year, catchKg, effort, source}]. */
    @Column(name = "series_json", length = 4000, nullable = false)
    private String seriesJson;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected RmsEstimateEntity() {
    }

    public RmsEstimateEntity(String speciesId) {
        this.speciesId = speciesId;
    }

    /** Reemplaza el resultado anterior por el de este cálculo. */
    public void update(String runId, int yearsCount, int firstYear, int lastYear, String seriesJson,
            SchaeferModel.Fit fit, String note) {
        this.runId = runId;
        this.yearsCount = yearsCount;
        this.firstYear = firstYear;
        this.lastYear = lastYear;
        this.seriesJson = seriesJson;
        this.fitted = fit != null;
        this.indicative = yearsCount < SchaeferModel.RELIABLE_YEARS;
        this.r = fit == null ? null : scale(fit.r(), 6);
        this.kKg = fit == null ? null : scale(fit.k(), 2);
        this.q = fit == null ? null : scale(fit.q(), 16);
        this.msyKg = fit == null ? null : scale(fit.msy(), 2);
        this.fMsy = fit == null ? null : scale(fit.fMsy(), 2);
        this.biomassRatio = fit == null ? null : scale(fit.biomassRatio(), 4);
        this.fishingRatio = fit == null ? null : scale(fit.fishingRatio(), 4);
        this.r2 = fit == null ? null : scale(fit.r2(), 4);
        this.stockStatus = fit == null ? null : fit.status();
        this.note = note;
        this.computedAt = Instant.now();
    }

    /** Los parámetros del ajuste como el modelo los entiende (para la curva de producción). */
    public SchaeferModel.Fit fit() {
        if (!fitted || r == null || kKg == null || q == null || msyKg == null || fMsy == null
                || biomassRatio == null || fishingRatio == null || r2 == null) {
            return null;
        }
        return new SchaeferModel.Fit(r.doubleValue(), kKg.doubleValue(), q.doubleValue(), msyKg.doubleValue(),
                fMsy.doubleValue(), biomassRatio.doubleValue(), fishingRatio.doubleValue(), r2.doubleValue(),
                stockStatus);
    }

    private static BigDecimal scale(double value, int digits) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP);
    }

    public String getSpeciesId() {
        return speciesId;
    }

    public String getRunId() {
        return runId;
    }

    public int getYearsCount() {
        return yearsCount;
    }

    public int getFirstYear() {
        return firstYear;
    }

    public int getLastYear() {
        return lastYear;
    }

    public boolean isFitted() {
        return fitted;
    }

    public boolean isIndicative() {
        return indicative;
    }

    public BigDecimal getR() {
        return r;
    }

    public BigDecimal getKKg() {
        return kKg;
    }

    public BigDecimal getQ() {
        return q;
    }

    public BigDecimal getMsyKg() {
        return msyKg;
    }

    public BigDecimal getFMsy() {
        return fMsy;
    }

    public BigDecimal getBiomassRatio() {
        return biomassRatio;
    }

    public BigDecimal getFishingRatio() {
        return fishingRatio;
    }

    public BigDecimal getR2() {
        return r2;
    }

    public SchaeferModel.StockStatus getStockStatus() {
        return stockStatus;
    }

    public String getSeriesJson() {
        return seriesJson;
    }

    public String getNote() {
        return note;
    }

    public Instant getComputedAt() {
        return computedAt;
    }
}
