package com.sigepes.backend.marketplace;

/** HU-16 · AC-16.3 · Aviso del marketplace para el vendedor o el comprador (HU-11). */
public record MarketNotice(String recipientId, String title, String message, String link, String key) {
}
