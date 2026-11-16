package com.sigepes.backend.inspections;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * HU-22 · AC-22.5 · Sello SHA-256 del acta. Se calcula sobre una representación canónica de todo
 * lo que el acta afirma (embarcación, lugar, fecha, lo medido, los hallazgos) y sobre el hash de cada
 * firma y foto. Cualquier cambio en la base o en los archivos hace que el sello deje de coincidir.
 */
final class InspectionSeal {

    private InspectionSeal() {
    }

    record Content(String code, String clientId, String vesselId, String ownerId, String inspectorId,
            String permitNumber, BigDecimal latitude, BigDecimal longitude, String locationText, Instant inspectedAt,
            String result, String observations, String responsibleName, String responsibleIdNumber,
            List<String> items, List<String> findings, String inspectorSignatureSha, String responsibleSignatureSha,
            List<String> photoShas) {
    }

    static String seal(Content c) {
        StringBuilder canonical = new StringBuilder("SIGEPES-ACTA-v1");
        append(canonical, c.code());
        append(canonical, c.clientId());
        append(canonical, c.vesselId());
        append(canonical, c.ownerId());
        append(canonical, c.inspectorId());
        append(canonical, c.permitNumber());
        append(canonical, c.latitude() == null ? null : c.latitude().stripTrailingZeros().toPlainString());
        append(canonical, c.longitude() == null ? null : c.longitude().stripTrailingZeros().toPlainString());
        append(canonical, c.locationText());
        append(canonical, c.inspectedAt().toString());
        append(canonical, c.result());
        append(canonical, c.observations());
        append(canonical, c.responsibleName());
        append(canonical, c.responsibleIdNumber());
        c.items().forEach(item -> append(canonical, "item:" + item));
        c.findings().forEach(finding -> append(canonical, "hallazgo:" + finding));
        append(canonical, c.inspectorSignatureSha());
        append(canonical, c.responsibleSignatureSha());
        c.photoShas().forEach(sha -> append(canonical, "foto:" + sha));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String item(String kind, String speciesId, String gearId, String label, BigDecimal sizeCm,
            BigDecimal weightKg, Integer quantity) {
        return String.join(";", kind, String.valueOf(speciesId), String.valueOf(gearId), label, plain(sizeCm),
                plain(weightKg), String.valueOf(quantity));
    }

    static String finding(String code, String description) {
        return code + ";" + description;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String plain(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    /** Cada campo va con su longitud para que «ab|c» y «a|bc» no den el mismo texto. */
    private static void append(StringBuilder canonical, String value) {
        String text = value == null ? "" : value;
        canonical.append('|').append(value == null ? -1 : text.length()).append(':').append(text);
    }
}
