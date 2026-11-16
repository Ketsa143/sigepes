package com.sigepes.backend.sanctions;

import java.time.LocalDate;

/** HU-23 · AC-23.3 · Cambio en el proceso que se notifica al investigado (HU-11). */
public record SanctionStageChanged(String processId, String number, String investigatedId, String stageLabel,
        LocalDate deadline, String message) {
}
