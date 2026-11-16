package com.sigepes.backend.marketplace;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sigepes.backend.marketplace.MarketEntities.RequestEntity;

/** HU-16 · Solicitudes de compra del marketplace. */
public interface MarketRequestRepository extends JpaRepository<RequestEntity, String> {

    List<RequestEntity> findByOfferIdOrderByCreatedAtAsc(String offerId);

    List<RequestEntity> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    boolean existsByOfferIdAndBuyerIdAndStatus(String offerId, String buyerId, RequestEntity.Status status);
}
