package com.sigepes.backend.geo;

/** HU-13 · Zona de manejo dentro de un AMP; define qué se puede hacer en ella. */
public enum MpaZoneKind {
    /** Zona intangible: no se permite ninguna actividad extractiva. */
    INTANGIBLE,
    /** Uso sostenible: se permite pescar solo con las artes y especies autorizadas. */
    SUSTAINABLE_USE,
    /** Amortiguación: zona de transición, sin restricción de arte ni especie. */
    BUFFER
}
