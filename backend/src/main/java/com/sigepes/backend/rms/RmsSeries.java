package com.sigepes.backend.rms;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.trips.CatchRepository;

/**
 * HU-17 · TASK-17.1 · Serie anual de captura y esfuerzo por especie.
 *
 * <p>El esfuerzo es el número de faenas cerradas que registraron la especie; la CPUE es la captura
 * por faena. Solo entran años completos. Los años sin faenas en SIGEPES se completan con la serie
 * histórica (estadísticas anteriores al sistema), si la hay.
 */
@Component
public class RmsSeries {

    /** Se guardan como mucho los últimos 30 años: más no cambia el ajuste y alarga la caché. */
    static final int MAX_YEARS = 30;

    public static final String SOURCE_TRIPS = "FAENAS";
    public static final String SOURCE_HISTORY = "HISTORICO";

    private final CatchRepository catchRepository;
    private final CatchEffortHistoryRepository historyRepository;

    public RmsSeries(CatchRepository catchRepository, CatchEffortHistoryRepository historyRepository) {
        this.catchRepository = catchRepository;
        this.historyRepository = historyRepository;
    }

    /** Un año de la serie, tal como se guarda en la caché y se muestra en la pantalla. */
    public record Point(int year, double catchKg, double effort, String source) {

        public double cpue() {
            return effort > 0 ? catchKg / effort : 0;
        }

        SchaeferModel.YearPoint toModel() {
            return new SchaeferModel.YearPoint(year, catchKg, effort);
        }
    }

    /** Serie de cada especie con datos, ordenada por año. */
    public Map<String, List<Point>> build() {
        int currentYear = AppClock.today().getYear();
        Map<String, TreeMap<Integer, Point>> bySpecies = new LinkedHashMap<>();

        for (CatchEffortHistoryEntity row : historyRepository.findByYearLessThanOrderBySpeciesIdAscYearAsc(currentYear)) {
            bySpecies.computeIfAbsent(row.getSpeciesId(), ignored -> new TreeMap<>())
                    .put(row.getYear(), new Point(row.getYear(), row.getCatchKg().doubleValue(), row.getEffortTrips(),
                            SOURCE_HISTORY));
        }
        for (Object[] row : catchRepository.yearlyCatchAndEffort(LocalDate.of(currentYear, 1, 1))) {
            String speciesId = (String) row[0];
            int year = ((Number) row[1]).intValue();
            double catchKg = row[2] == null ? 0 : ((Number) row[2]).doubleValue();
            double effort = ((Number) row[3]).doubleValue();
            bySpecies.computeIfAbsent(speciesId, ignored -> new TreeMap<>())
                    .put(year, new Point(year, catchKg, effort, SOURCE_TRIPS));
        }

        Map<String, List<Point>> series = new LinkedHashMap<>();
        bySpecies.forEach((speciesId, years) -> {
            List<Point> points = new ArrayList<>(years.values());
            series.put(speciesId, points.size() > MAX_YEARS
                    ? List.copyOf(points.subList(points.size() - MAX_YEARS, points.size()))
                    : List.copyOf(points));
        });
        return series;
    }
}
