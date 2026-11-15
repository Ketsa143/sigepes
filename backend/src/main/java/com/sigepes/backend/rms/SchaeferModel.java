package com.sigepes.backend.rms;

import java.util.ArrayList;
import java.util.List;

/**
 * HU-17 · AC-17.1 · Modelo de producción excedente de Schaefer ajustado por error de observación.
 *
 * <p>La biomasa sigue {@code B(t+1) = B(t) + r·B(t)·(1 − B(t)/K) − C(t)}, empezando en {@code K}
 * (pesquería poco explotada al inicio de la serie), y el índice de abundancia observado es la CPUE:
 * {@code U(t) = q·B(t)}. Se busca el par (r, K) que minimiza el error cuadrático en escala
 * logarítmica; la capturabilidad q tiene solución cerrada para cada par. La búsqueda combina una
 * rejilla gruesa con Nelder–Mead sobre (ln r, ln K), sin librerías externas.
 *
 * <p>Resultados (AC-17.2): {@code RMS = r·K/4}, {@code f-RMS = r/(2q)}, la biomasa relativa
 * {@code B/B-RMS} del último año (con {@code B-RMS = K/2}) y de ahí el estado del recurso.
 */
public final class SchaeferModel {

    /** Por debajo de este número de años con datos no se ajusta nada. */
    public static final int MIN_YEARS = 3;
    /** AC-17.4: con menos años el resultado es indicativo. */
    public static final int RELIABLE_YEARS = 5;

    private static final double R_MIN = 0.01;
    private static final double R_MAX = 2.0;

    private SchaeferModel() {
    }

    /** Un año de la serie: captura total (kg) y esfuerzo (número de faenas). */
    public record YearPoint(int year, double catchKg, double effort) {

        public double cpue() {
            return effort > 0 ? catchKg / effort : 0;
        }
    }

    public enum StockStatus { UNDER_EXPLOITED, FULLY_EXPLOITED, OVER_EXPLOITED }

    public record Fit(double r, double k, double q, double msy, double fMsy, double biomassRatio,
            double fishingRatio, double r2, StockStatus status) {
    }

    /**
     * Ajusta el modelo a la serie, que debe venir ordenada por año. Los años sin datos dentro del
     * rango se proyectan sin captura y no cuentan en el error.
     */
    public static Fit fit(List<YearPoint> series) {
        List<YearPoint> usable = series.stream().filter(p -> p.catchKg() > 0 && p.effort() > 0).toList();
        if (usable.size() < MIN_YEARS) {
            throw new IllegalArgumentException("Se necesitan al menos " + MIN_YEARS + " años con captura y esfuerzo.");
        }
        double[] catches = dense(usable, true);
        double[] cpue = dense(usable, false);

        double maxCatch = 0;
        double sumCatch = 0;
        for (double c : catches) {
            maxCatch = Math.max(maxCatch, c);
            sumCatch += c;
        }
        double kMin = maxCatch;
        double kMax = 1000 * sumCatch;

        // 1) Rejilla gruesa para no caer en un mínimo local lejano.
        double[] best = null;
        double bestValue = Double.POSITIVE_INFINITY;
        double[] rGrid = { 0.05, 0.1, 0.2, 0.3, 0.45, 0.6, 0.8, 1.0, 1.3, 1.7 };
        for (double r : rGrid) {
            for (double factor = 1.5; factor <= 200; factor *= 1.6) {
                double k = Math.max(kMin * 1.01, sumCatch * factor / catches.length * 4);
                double[] point = { Math.log(r), Math.log(k) };
                double value = objective(point, catches, cpue, kMin, kMax);
                if (value < bestValue) {
                    bestValue = value;
                    best = point;
                }
            }
        }
        // 2) Refinamiento local; se reinicia una vez desde el óptimo para escapar de simplex degenerados.
        double[] refined = nelderMead(best, catches, cpue, kMin, kMax);
        refined = nelderMead(refined, catches, cpue, kMin, kMax);

        double r = Math.exp(refined[0]);
        double k = Math.exp(refined[1]);
        double[] biomass = project(r, k, catches);
        double lnQ = logQ(biomass, cpue);
        double q = Math.exp(lnQ);

        double sse = 0;
        double mean = 0;
        int n = 0;
        for (int t = 0; t < cpue.length; t++) {
            if (cpue[t] > 0) {
                mean += Math.log(cpue[t]);
                n++;
            }
        }
        mean /= n;
        double sst = 0;
        for (int t = 0; t < cpue.length; t++) {
            if (cpue[t] > 0) {
                double residual = Math.log(cpue[t]) - lnQ - Math.log(biomass[t]);
                sse += residual * residual;
                sst += Math.pow(Math.log(cpue[t]) - mean, 2);
            }
        }
        double r2 = sst > 0 ? Math.max(0, 1 - sse / sst) : 0;

        int last = biomass.length - 1;
        double biomassRatio = biomass[last] / (k / 2);
        double fishingRatio = (catches[last] / biomass[last]) / (r / 2);
        return new Fit(r, k, q, r * k / 4, r / (2 * q), biomassRatio, fishingRatio, r2, status(biomassRatio));
    }

    /** Biomasa relativa: &gt; 1,2 sub-explotado; 0,8–1,2 plena explotación; &lt; 0,8 sobreexplotado. */
    public static StockStatus status(double biomassRatio) {
        if (biomassRatio > 1.2) {
            return StockStatus.UNDER_EXPLOITED;
        }
        return biomassRatio >= 0.8 ? StockStatus.FULLY_EXPLOITED : StockStatus.OVER_EXPLOITED;
    }

    /** Curva de producción de equilibrio {@code Y(f) = q·K·f·(1 − q·f/r)} para dibujarla. */
    public static List<double[]> productionCurve(Fit fit, int points) {
        double fMax = fit.r() / fit.q();
        List<double[]> curve = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) {
            double f = fMax * i / points;
            curve.add(new double[] { f, Math.max(0, fit.q() * fit.k() * f * (1 - fit.q() * f / fit.r())) });
        }
        return curve;
    }

    // ------------------------------------------------------------------

    /** Años consecutivos desde el primero hasta el último; los huecos van en cero. */
    private static double[] dense(List<YearPoint> usable, boolean catches) {
        int first = usable.get(0).year();
        int span = usable.get(usable.size() - 1).year() - first + 1;
        double[] values = new double[span];
        for (YearPoint p : usable) {
            values[p.year() - first] = catches ? p.catchKg() : p.cpue();
        }
        return values;
    }

    private static double[] project(double r, double k, double[] catches) {
        double[] biomass = new double[catches.length];
        double b = k;
        for (int t = 0; t < catches.length; t++) {
            biomass[t] = b;
            b = Math.max(b + r * b * (1 - b / k) - catches[t], 1e-6 * k);
        }
        return biomass;
    }

    private static double logQ(double[] biomass, double[] cpue) {
        double sum = 0;
        int n = 0;
        for (int t = 0; t < cpue.length; t++) {
            if (cpue[t] > 0) {
                sum += Math.log(cpue[t]) - Math.log(biomass[t]);
                n++;
            }
        }
        return sum / n;
    }

    private static double objective(double[] point, double[] catches, double[] cpue, double kMin, double kMax) {
        double r = Math.exp(point[0]);
        double k = Math.exp(point[1]);
        double penalty = 0;
        if (r < R_MIN || r > R_MAX || k < kMin || k > kMax) {
            penalty = 1e6;
            r = Math.min(R_MAX, Math.max(R_MIN, r));
            k = Math.min(kMax, Math.max(kMin, k));
        }
        double[] biomass = project(r, k, catches);
        double lnQ = logQ(biomass, cpue);
        double sse = 0;
        for (int t = 0; t < cpue.length; t++) {
            if (cpue[t] > 0) {
                double residual = Math.log(cpue[t]) - lnQ - Math.log(biomass[t]);
                sse += residual * residual;
            }
            // Una biomasa que colapsa al mínimo no explica una pesquería que siguió capturando.
            if (biomass[t] <= 1e-6 * k * 1.0001) {
                penalty += 100;
            }
        }
        return sse + penalty;
    }

    /** Nelder–Mead en 2 dimensiones con los coeficientes estándar. */
    private static double[] nelderMead(double[] start, double[] catches, double[] cpue, double kMin, double kMax) {
        double[][] simplex = { start.clone(), { start[0] + 0.3, start[1] }, { start[0], start[1] + 0.3 } };
        double[] values = new double[3];
        for (int i = 0; i < 3; i++) {
            values[i] = objective(simplex[i], catches, cpue, kMin, kMax);
        }
        for (int iteration = 0; iteration < 600; iteration++) {
            // Ordenar: 0 mejor, 2 peor.
            for (int i = 0; i < 2; i++) {
                for (int j = i + 1; j < 3; j++) {
                    if (values[j] < values[i]) {
                        double[] tmpPoint = simplex[i];
                        simplex[i] = simplex[j];
                        simplex[j] = tmpPoint;
                        double tmp = values[i];
                        values[i] = values[j];
                        values[j] = tmp;
                    }
                }
            }
            if (Math.abs(values[2] - values[0]) < 1e-12 && distance(simplex[0], simplex[2]) < 1e-8) {
                break;
            }
            double[] centroid = { (simplex[0][0] + simplex[1][0]) / 2, (simplex[0][1] + simplex[1][1]) / 2 };
            double[] reflected = combine(centroid, simplex[2], -1.0);
            double reflectedValue = objective(reflected, catches, cpue, kMin, kMax);
            if (reflectedValue < values[0]) {
                double[] expanded = combine(centroid, simplex[2], -2.0);
                double expandedValue = objective(expanded, catches, cpue, kMin, kMax);
                if (expandedValue < reflectedValue) {
                    simplex[2] = expanded;
                    values[2] = expandedValue;
                } else {
                    simplex[2] = reflected;
                    values[2] = reflectedValue;
                }
            } else if (reflectedValue < values[1]) {
                simplex[2] = reflected;
                values[2] = reflectedValue;
            } else {
                double[] contracted = combine(centroid, simplex[2], 0.5);
                double contractedValue = objective(contracted, catches, cpue, kMin, kMax);
                if (contractedValue < values[2]) {
                    simplex[2] = contracted;
                    values[2] = contractedValue;
                } else {
                    for (int i = 1; i < 3; i++) {
                        simplex[i] = combine(simplex[0], simplex[i], 0.5);
                        values[i] = objective(simplex[i], catches, cpue, kMin, kMax);
                    }
                }
            }
        }
        int bestIndex = 0;
        for (int i = 1; i < 3; i++) {
            if (values[i] < values[bestIndex]) {
                bestIndex = i;
            }
        }
        return simplex[bestIndex];
    }

    /** {@code centroid + factor·(point − centroid)}. */
    private static double[] combine(double[] centroid, double[] point, double factor) {
        return new double[] { centroid[0] + factor * (point[0] - centroid[0]),
                centroid[1] + factor * (point[1] - centroid[1]) };
    }

    private static double distance(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
