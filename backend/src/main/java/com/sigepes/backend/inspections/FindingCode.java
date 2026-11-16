package com.sigepes.backend.inspections;

/** HU-22 · AC-22.2 · Hallazgos tipificados de un acta (HU-23 tipifica la infracción a partir de ellos). */
public enum FindingCode {
    /** Talla medida inferior a la mínima de la especie (HU-06). */
    SIZE_BELOW_MIN("Talla inferior a la mínima"),
    /** Arte destructivo o prohibido a bordo. */
    PROHIBITED_GEAR("Arte prohibido"),
    /** Arte que no está registrado en el perfil de la embarcación (HU-02). */
    UNREGISTERED_GEAR("Arte no registrado"),
    /** El armador no tiene un permiso aprobado y vigente en la fecha de la inspección (HU-08). */
    NO_VALID_PERMIT("Sin permiso vigente"),
    /** La embarcación no está activa (suspendida, dada de baja...). */
    VESSEL_NOT_ACTIVE("Embarcación no activa"),
    /** Hallazgo anotado por el inspector. */
    OTHER("Otro hallazgo");

    private final String label;

    FindingCode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
