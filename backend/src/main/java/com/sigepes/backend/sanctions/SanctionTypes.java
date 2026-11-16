package com.sigepes.backend.sanctions;

import java.util.Collection;
import java.util.Comparator;

import com.sigepes.backend.inspections.FindingCode;
import com.sigepes.backend.reports.ReportType;

/** HU-23 · Catálogos del proceso sancionatorio. */
public final class SanctionTypes {

    private SanctionTypes() {
    }

    /**
     * AC-23.1 · Infracción tipificada (conductas prohibidas del Estatuto General de Pesca, Ley 13 de
     * 1990, con el régimen sancionatorio de la Ley 1851 de 2017). El orden es la gravedad: al abrir
     * desde un acta con varios hallazgos se propone la más grave.
     */
    public enum Infraction {
        PROHIBITED_GEAR("Uso de artes o métodos de pesca prohibidos"),
        FISHING_WITHOUT_PERMIT("Pesca sin permiso vigente"),
        PROTECTED_AREA("Pesca en área o periodo prohibido (veda o AMP)"),
        UNDERSIZED_CATCH("Captura de ejemplares por debajo de la talla mínima"),
        UNAUTHORIZED_GEAR("Uso de artes no autorizadas en el permiso"),
        UNREGISTERED_VESSEL("Embarcación no habilitada para la pesca"),
        OTHER("Otra infracción a la normatividad pesquera");

        private final String label;

        Infraction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        static Infraction of(FindingCode code) {
            return switch (code) {
                case PROHIBITED_GEAR -> PROHIBITED_GEAR;
                case NO_VALID_PERMIT -> FISHING_WITHOUT_PERMIT;
                case SIZE_BELOW_MIN -> UNDERSIZED_CATCH;
                case UNREGISTERED_GEAR -> UNAUTHORIZED_GEAR;
                case VESSEL_NOT_ACTIVE -> UNREGISTERED_VESSEL;
                case OTHER -> OTHER;
            };
        }

        static Infraction of(ReportType type) {
            return switch (type) {
                case PROHIBITED_GEAR -> PROHIBITED_GEAR;
                case UNDERSIZED_CATCH -> UNDERSIZED_CATCH;
                case CLOSURE_FISHING, MPA_INTRUSION -> PROTECTED_AREA;
                case OTHER -> OTHER;
            };
        }

        /** La más grave de las que sugieren los hallazgos del acta. */
        static Infraction mostSevere(Collection<FindingCode> codes) {
            return codes.stream().map(Infraction::of).min(Comparator.naturalOrder()).orElse(OTHER);
        }
    }

    /** AC-23.2 · Etapas del proceso. */
    public enum Stage {
        OPENED("Apertura"),
        DEFENSE("Descargos"),
        EVIDENCE("Pruebas"),
        DECISION("Decisión"),
        APPEAL("Recurso"),
        CLOSED("Cerrado");

        private final String label;

        Stage(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** AC-23.4 · Sanción (o archivo) que fija la decisión. */
    public enum Sanction {
        WARNING("Amonestación"),
        FINE("Multa"),
        CONFISCATION("Decomiso"),
        PERMIT_SUSPENSION("Suspensión del permiso"),
        DISMISSED("Archivo sin sanción");

        private final String label;

        Sanction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Tipo de actuación registrada en el expediente. */
    public enum ActionKind { OPENED, ADVANCED, DEFENSE_FILED, DECIDED, APPEAL_FILED, CLOSED }

    /** Plazos en días hábiles. */
    static final int DEFENSE_DAYS = 15;
    static final int EVIDENCE_DAYS = 30;
    static final int APPEAL_DAYS = 10;
}
