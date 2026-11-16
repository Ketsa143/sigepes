package com.sigepes.backend.marketplace;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sigepes.backend.marketplace.MarketEntities.OfferEntity;

/** HU-16 · Ofertas del marketplace. */
public interface MarketOfferRepository extends JpaRepository<OfferEntity, String> {

    /** AC-16.2 · Catálogo: ofertas activas y vigentes, de la más reciente a la más antigua. */
    @Query("""
            SELECT o FROM MarketOffer o
            WHERE o.status = :active AND o.expiresAt > :now
            ORDER BY o.publishedAt DESC
            """)
    List<OfferEntity> findOpen(@Param("active") OfferEntity.Status active, @Param("now") Instant now);

    List<OfferEntity> findBySellerIdOrderByPublishedAtDesc(String sellerId);

    boolean existsByLotIdAndStatus(String lotId, OfferEntity.Status status);

    /** AC-16.4 · Marca vencidas las ofertas activas que pasaron sus 72 h. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE MarketOffer o
            SET o.status = :expired, o.closedAt = :now
            WHERE o.status = :active AND o.expiresAt <= :now
            """)
    int expireDue(@Param("active") OfferEntity.Status active, @Param("expired") OfferEntity.Status expired,
            @Param("now") Instant now);
}
