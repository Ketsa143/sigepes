package com.sigepes.backend.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HU-23 · Días hábiles en Colombia para los plazos de los procesos sancionatorios: excluye sábados,
 * domingos y festivos. Los festivos se calculan para cualquier año (Ley 51 de 1983, "Ley Emiliani"):
 * unos son fijos, otros se trasladan al lunes siguiente y los religiosos dependen de la Pascua.
 */
public final class BusinessDays {

    private static final Map<Integer, Set<LocalDate>> HOLIDAYS = new ConcurrentHashMap<>();

    private BusinessDays() {
    }

    /** La fecha que resulta de sumar {@code days} días hábiles a {@code from} (sin contar {@code from}). */
    public static LocalDate add(LocalDate from, int days) {
        LocalDate date = from;
        int added = 0;
        while (added < days) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                added++;
            }
        }
        return date;
    }

    public static boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !isHoliday(date);
    }

    public static boolean isHoliday(LocalDate date) {
        return HOLIDAYS.computeIfAbsent(date.getYear(), BusinessDays::holidays).contains(date);
    }

    static Set<LocalDate> holidays(int year) {
        Set<LocalDate> days = new HashSet<>();
        // Fijos.
        days.add(LocalDate.of(year, Month.JANUARY, 1));
        days.add(LocalDate.of(year, Month.MAY, 1));
        days.add(LocalDate.of(year, Month.JULY, 20));
        days.add(LocalDate.of(year, Month.AUGUST, 7));
        days.add(LocalDate.of(year, Month.DECEMBER, 8));
        days.add(LocalDate.of(year, Month.DECEMBER, 25));
        // Ley Emiliani: se trasladan al lunes siguiente si no caen en lunes.
        days.add(nextMonday(LocalDate.of(year, Month.JANUARY, 6)));
        days.add(nextMonday(LocalDate.of(year, Month.MARCH, 19)));
        days.add(nextMonday(LocalDate.of(year, Month.JUNE, 29)));
        days.add(nextMonday(LocalDate.of(year, Month.AUGUST, 15)));
        days.add(nextMonday(LocalDate.of(year, Month.OCTOBER, 12)));
        days.add(nextMonday(LocalDate.of(year, Month.NOVEMBER, 1)));
        days.add(nextMonday(LocalDate.of(year, Month.NOVEMBER, 11)));
        // Según la Pascua.
        LocalDate easter = easter(year);
        days.add(easter.minusDays(3));                 // Jueves Santo
        days.add(easter.minusDays(2));                 // Viernes Santo
        days.add(nextMonday(easter.plusDays(39)));     // Ascensión
        days.add(nextMonday(easter.plusDays(60)));     // Corpus Christi
        days.add(nextMonday(easter.plusDays(68)));     // Sagrado Corazón
        return Set.copyOf(days);
    }

    private static LocalDate nextMonday(LocalDate date) {
        return date.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Domingo de Pascua (algoritmo anónimo gregoriano, Meeus/Jones/Butcher). */
    public static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
