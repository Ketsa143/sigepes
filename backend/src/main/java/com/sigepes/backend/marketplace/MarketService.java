package com.sigepes.backend.marketplace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sigepes.backend.audit.AuditActionType;
import com.sigepes.backend.audit.AuditService;
import com.sigepes.backend.common.ApiException;
import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.common.ErrorCode;
import com.sigepes.backend.common.Ids;
import com.sigepes.backend.marketplace.MarketDtos.EligibleLot;
import com.sigepes.backend.marketplace.MarketDtos.MyOffer;
import com.sigepes.backend.marketplace.MarketDtos.MyRequest;
import com.sigepes.backend.marketplace.MarketDtos.OfferCard;
import com.sigepes.backend.marketplace.MarketDtos.PublishRequest;
import com.sigepes.backend.marketplace.MarketDtos.RequestCreate;
import com.sigepes.backend.marketplace.MarketDtos.RequestView;
import com.sigepes.backend.marketplace.MarketEntities.OfferEntity;
import com.sigepes.backend.marketplace.MarketEntities.RequestEntity;
import com.sigepes.backend.security.AuthenticatedUser;
import com.sigepes.backend.security.CurrentUser;
import com.sigepes.backend.traceability.ChainEventEntity;
import com.sigepes.backend.traceability.ChainEventRepository;
import com.sigepes.backend.traceability.LotEntity;
import com.sigepes.backend.traceability.LotRepository;
import com.sigepes.backend.traceability.TraceabilityDtos.TransferRequest;
import com.sigepes.backend.traceability.TraceabilityService;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.users.UserRepository;

/**
 * HU-16 · Marketplace de producto pesquero legal.
 *
 * <p>Solo se oferta un lote trazable (HU-09) que sigue en manos del pescador. La oferta dura 72 h. El
 * comprador envía una solicitud; si el vendedor la acepta, la venta queda en la cadena de custodia
 * del lote y la oferta se cierra. Del vendedor y del comprador solo se publican alias y municipio.
 */
@Service
public class MarketService {

    static final Duration OFFER_LIFETIME = Duration.ofHours(72);

    private final MarketOfferRepository offerRepository;
    private final MarketRequestRepository requestRepository;
    private final LotRepository lotRepository;
    private final ChainEventRepository chainRepository;
    private final TraceabilityService traceabilityService;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    public MarketService(MarketOfferRepository offerRepository, MarketRequestRepository requestRepository,
            LotRepository lotRepository, ChainEventRepository chainRepository, TraceabilityService traceabilityService,
            UserRepository userRepository, CurrentUser currentUser, AuditService auditService,
            ApplicationEventPublisher events) {
        this.offerRepository = offerRepository;
        this.requestRepository = requestRepository;
        this.lotRepository = lotRepository;
        this.chainRepository = chainRepository;
        this.traceabilityService = traceabilityService;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.events = events;
    }

    // ------------------------------------------------------------------
    // Catálogo público (AC-16.2, AC-16.5)
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OfferCard> catalog(String speciesId, String port, BigDecimal maxPrice) {
        return offerRepository.findOpen(OfferEntity.Status.ACTIVE, Instant.now()).stream()
                .filter(offer -> speciesId == null || speciesId.isBlank() || offer.getSpecies().getId().equals(speciesId))
                .filter(offer -> port == null || port.isBlank() || normalize(offer.getPort()).contains(normalize(port)))
                .filter(offer -> maxPrice == null || offer.getPricePerKg().compareTo(maxPrice) <= 0)
                .map(MarketService::card)
                .toList();
    }

    // ------------------------------------------------------------------
    // Publicación (AC-16.1, AC-16.6)
    // ------------------------------------------------------------------

    /** Lotes propios ofertables: trazables, todavía sin entregar y sin una oferta activa. */
    @Transactional
    public List<EligibleLot> eligibleLots() {
        AuthenticatedUser actor = currentUser.require();
        expireDue();
        return lotRepository.findByOwnerIdOrderByCreatedAtDesc(actor.id()).stream()
                .filter(this::stillWithFisher)
                .filter(lot -> !offerRepository.existsByLotIdAndStatus(lot.getId(), OfferEntity.Status.ACTIVE))
                .map(lot -> new EligibleLot(lot.getId(), lot.getCode(), lot.getSpecies().getCommonName(),
                        lot.getWeightKg(), portOf(lot), lot.getLandingDate(), lot.getZone()))
                .toList();
    }

    @Transactional
    public OfferCard publish(PublishRequest request) {
        AuthenticatedUser actor = currentUser.require();
        expireDue();
        LotEntity lot = lotRepository.findById(request.lotId())
                .orElseThrow(() -> ApiException.of(ErrorCode.LOT_NOT_FOUND));
        if (!lot.getOwner().getId().equals(actor.id())) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "Solo el titular del lote puede ofertarlo.");
        }
        if (!stillWithFisher(lot)) {
            throw ApiException.of(ErrorCode.MARKET_LOT_NOT_AVAILABLE,
                    "El lote " + lot.getCode() + " ya fue entregado en la cadena de custodia.");
        }
        if (offerRepository.existsByLotIdAndStatus(lot.getId(), OfferEntity.Status.ACTIVE)) {
            throw ApiException.of(ErrorCode.MARKET_LOT_NOT_AVAILABLE,
                    "El lote " + lot.getCode() + " ya tiene una oferta activa.");
        }
        Instant now = Instant.now();
        OfferEntity offer = offerRepository.save(new OfferEntity(Ids.generate("ofe"), lot,
                request.pricePerKg().setScale(2, RoundingMode.HALF_UP), portOf(lot), blankToNull(request.notes()), now,
                now.plus(OFFER_LIFETIME)));
        auditService.record(AuditActionType.CREATE, "MARKET_OFFER_PUBLISHED", AuditService.MODULE_MARKET, "Oferta",
                offer.getId(), "Lote " + lot.getCode() + ": " + offer.getWeightKg() + " kg de "
                        + lot.getSpecies().getCommonName() + " a $" + offer.getPricePerKg() + "/kg");
        return card(offer);
    }

    @Transactional
    public OfferCard withdraw(String offerId) {
        AuthenticatedUser actor = currentUser.require();
        OfferEntity offer = ownOffer(offerId, actor);
        if (offer.getStatus() != OfferEntity.Status.ACTIVE) {
            throw ApiException.of(ErrorCode.MARKET_OFFER_CLOSED);
        }
        offer.close(OfferEntity.Status.WITHDRAWN);
        rejectPending(offer, "La oferta fue retirada por el vendedor.");
        auditService.record(AuditActionType.UPDATE, "MARKET_OFFER_WITHDRAWN", AuditService.MODULE_MARKET, "Oferta",
                offer.getId(), "Oferta del lote " + offer.getLot().getCode() + " retirada");
        return card(offerRepository.save(offer));
    }

    @Transactional
    public List<MyOffer> myOffers() {
        AuthenticatedUser actor = currentUser.require();
        expireDue();
        return offerRepository.findBySellerIdOrderByPublishedAtDesc(actor.id()).stream()
                .map(offer -> new MyOffer(card(offer), offer.getStatus().name(),
                        requestRepository.findByOfferIdOrderByCreatedAtAsc(offer.getId()).stream()
                                .map(MarketService::view).toList()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Solicitudes (AC-16.3)
    // ------------------------------------------------------------------

    @Transactional
    public MyRequest request(String offerId, RequestCreate request) {
        AuthenticatedUser actor = currentUser.require();
        OfferEntity offer = offerRepository.findById(offerId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MARKET_OFFER_NOT_FOUND));
        if (!offer.isOpen(Instant.now())) {
            throw ApiException.of(ErrorCode.MARKET_OFFER_CLOSED);
        }
        if (offer.getSeller().getId().equals(actor.id())) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR, "No puedes solicitar tu propia oferta.");
        }
        if (request.quantityKg().compareTo(offer.getWeightKg()) > 0) {
            throw ApiException.of(ErrorCode.VALIDATION_ERROR,
                    "La oferta es de " + offer.getWeightKg().stripTrailingZeros().toPlainString() + " kg.");
        }
        if (requestRepository.existsByOfferIdAndBuyerIdAndStatus(offerId, actor.id(), RequestEntity.Status.PENDING)) {
            throw ApiException.of(ErrorCode.MARKET_REQUEST_DUPLICATE);
        }
        UserEntity buyer = userRepository.findById(actor.id()).orElseThrow(() -> ApiException.of(ErrorCode.UNAUTHORIZED));
        RequestEntity saved = requestRepository.save(new RequestEntity(Ids.generate("sol"), offer, buyer,
                request.quantityKg().setScale(2, RoundingMode.HALF_UP), request.message().trim()));
        auditService.record(AuditActionType.CREATE, "MARKET_REQUEST_SENT", AuditService.MODULE_MARKET, "Oferta",
                offer.getId(), "Solicitud de " + saved.getQuantityKg() + " kg del lote " + offer.getLot().getCode());
        events.publishEvent(new MarketNotice(offer.getSeller().getId(), "Nueva solicitud por tu oferta",
                alias(buyer.getFullName()) + " quiere " + plain(saved.getQuantityKg()) + " kg de "
                        + offer.getSpecies().getCommonName() + " del lote " + offer.getLot().getCode() + ".",
                "/app/marketplace?tab=mis-ofertas", "market:request:" + saved.getId()));
        return myRequest(saved);
    }

    /** Aceptar registra la venta en la cadena de custodia del lote y cierra la oferta. */
    @Transactional
    public List<MyOffer> decide(String requestId, boolean accept) {
        AuthenticatedUser actor = currentUser.require();
        RequestEntity request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MARKET_REQUEST_NOT_FOUND));
        OfferEntity offer = ownOffer(request.getOffer().getId(), actor);
        if (request.getStatus() != RequestEntity.Status.PENDING) {
            throw ApiException.of(ErrorCode.MARKET_REQUEST_DECIDED);
        }
        if (accept) {
            if (!offer.isOpen(Instant.now())) {
                throw ApiException.of(ErrorCode.MARKET_OFFER_CLOSED);
            }
            request.decide(RequestEntity.Status.ACCEPTED);
            traceabilityService.transfer(offer.getLot().getId(), new TransferRequest("BUYER",
                    request.getBuyer().getFullName(), null, offer.getPort(), AppClock.today()));
            offer.close(OfferEntity.Status.SOLD);
            rejectPending(offer, "El lote se vendió a otro comprador.");
            auditService.record(AuditActionType.UPDATE, "MARKET_SALE", AuditService.MODULE_MARKET, "Oferta",
                    offer.getId(), "Lote " + offer.getLot().getCode() + " vendido: " + request.getQuantityKg()
                            + " kg; entrega registrada en la cadena de custodia");
        } else {
            request.decide(RequestEntity.Status.REJECTED);
            auditService.record(AuditActionType.UPDATE, "MARKET_REQUEST_REJECTED", AuditService.MODULE_MARKET, "Oferta",
                    offer.getId(), "Solicitud rechazada en el lote " + offer.getLot().getCode());
        }
        requestRepository.save(request);
        offerRepository.save(offer);
        events.publishEvent(new MarketNotice(request.getBuyer().getId(),
                accept ? "Tu solicitud fue aceptada" : "Tu solicitud no fue aceptada",
                (accept ? "El vendedor aceptó tu solicitud de " : "El vendedor no aceptó tu solicitud de ")
                        + plain(request.getQuantityKg()) + " kg de " + offer.getSpecies().getCommonName() + " (lote "
                        + offer.getLot().getCode() + ")." + (accept ? " La venta quedó en la cadena de custodia." : ""),
                "/app/marketplace?tab=mis-solicitudes", "market:decision:" + request.getId()));
        return myOffers();
    }

    @Transactional
    public List<MyRequest> myRequests() {
        AuthenticatedUser actor = currentUser.require();
        expireDue();
        return requestRepository.findByBuyerIdOrderByCreatedAtDesc(actor.id()).stream().map(this::myRequest).toList();
    }

    // ------------------------------------------------------------------

    private void expireDue() {
        offerRepository.expireDue(OfferEntity.Status.ACTIVE, OfferEntity.Status.EXPIRED, Instant.now());
    }

    private void rejectPending(OfferEntity offer, String reason) {
        for (RequestEntity other : requestRepository.findByOfferIdOrderByCreatedAtAsc(offer.getId())) {
            if (other.getStatus() == RequestEntity.Status.PENDING) {
                other.decide(RequestEntity.Status.REJECTED);
                requestRepository.save(other);
                events.publishEvent(new MarketNotice(other.getBuyer().getId(), "Tu solicitud no fue aceptada",
                        reason + " Lote " + offer.getLot().getCode() + ".", "/app/marketplace?tab=mis-solicitudes",
                        "market:decision:" + other.getId()));
            }
        }
    }

    /** AC-16.1 · El lote sigue en manos del pescador: su cadena solo tiene el evento de creación. */
    private boolean stillWithFisher(LotEntity lot) {
        return chainRepository.findByLotIdOrderBySequenceNoAsc(lot.getId()).stream()
                .noneMatch(event -> event.getEventType() == ChainEventEntity.EventType.TRANSFER);
    }

    private OfferEntity ownOffer(String offerId, AuthenticatedUser actor) {
        OfferEntity offer = offerRepository.findById(offerId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MARKET_OFFER_NOT_FOUND));
        if (!offer.getSeller().getId().equals(actor.id())) {
            throw ApiException.of(ErrorCode.FORBIDDEN, "La oferta no es tuya.");
        }
        return offer;
    }

    private MyRequest myRequest(RequestEntity request) {
        OfferEntity offer = request.getOffer();
        String offerStatus = offer.getStatus() == OfferEntity.Status.ACTIVE && !offer.isOpen(Instant.now())
                ? OfferEntity.Status.EXPIRED.name() : offer.getStatus().name();
        return new MyRequest(request.getId(), card(offer), offerStatus, request.getQuantityKg(), request.getMessage(),
                request.getStatus().name(), request.getCreatedAt(), request.getDecidedAt());
    }

    static OfferCard card(OfferEntity offer) {
        LotEntity lot = offer.getLot();
        return new OfferCard(offer.getId(), lot.getCode(), offer.getSpecies().getCommonName(),
                offer.getSpecies().getScientificName(), offer.getWeightKg(), offer.getPricePerKg(),
                offer.getWeightKg().multiply(offer.getPricePerKg()).setScale(0, RoundingMode.HALF_UP),
                offer.getPort(), offer.getLandingDate(), alias(offer.getSeller().getFullName()),
                municipality(offer), offer.getNotes(), offer.getPublishedAt(), offer.getExpiresAt(),
                "/verificar/" + lot.getCode());
    }

    private static RequestView view(RequestEntity request) {
        return new RequestView(request.getId(), alias(request.getBuyer().getFullName()), request.getQuantityKg(),
                request.getMessage(), request.getStatus().name(), request.getCreatedAt(), request.getDecidedAt());
    }

    /** AC-16.5 · Nombre de pila e inicial del primer apellido: «Aníbal Rentería Mosquera» → «Aníbal R.». */
    static String alias(String fullName) {
        String[] words = fullName == null ? new String[0] : fullName.trim().split("\\s+");
        if (words.length == 0 || words[0].isBlank()) {
            return "Usuario";
        }
        return words.length == 1 ? words[0] : words[0] + " " + words[1].charAt(0) + ".";
    }

    /** El municipio del vendedor es el puerto base de la embarcación que capturó el lote. */
    private static String municipality(OfferEntity offer) {
        String port = offer.getLot().getVessel().getPort();
        return port == null || port.isBlank() ? offer.getPort() : port;
    }

    private static String portOf(LotEntity lot) {
        String origin = lot.getCatchEntry() == null || lot.getCatchEntry().getTrip() == null ? null
                : lot.getCatchEntry().getTrip().getOriginPort();
        return origin == null || origin.isBlank() ? lot.getVessel().getPort() : origin;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
