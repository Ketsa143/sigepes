package com.sigepes.backend.inspections;

/** HU-22 · Se registró un acta firmada; el módulo de alertas avisa al armador (HU-11). */
public record InspectionSigned(String inspectionId, String code, String ownerId, String vesselName, int findings) {
}
