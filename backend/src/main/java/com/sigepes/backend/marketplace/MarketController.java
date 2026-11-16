package com.sigepes.backend.marketplace;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sigepes.backend.marketplace.MarketDtos.EligibleLot;
import com.sigepes.backend.marketplace.MarketDtos.MyOffer;
import com.sigepes.backend.marketplace.MarketDtos.MyRequest;
import com.sigepes.backend.marketplace.MarketDtos.OfferCard;
import com.sigepes.backend.marketplace.MarketDtos.PublishRequest;
import com.sigepes.backend.marketplace.MarketDtos.RequestCreate;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** HU-16 · Marketplace de producto pesquero legal. El catálogo es público; lo demás exige sesión. */
@RestController
@RequestMapping("/api/v1/mercado")
@Tag(name = "Marketplace")
public class MarketController {

    private final MarketService service;

    public MarketController(MarketService service) {
        this.service = service;
    }

    @GetMapping("/ofertas")
    @Operation(summary = "Catálogo público de ofertas vigentes; filtros por especie, puerto y precio máximo por kg")
    public List<OfferCard> catalog(@RequestParam(required = false) String especieId,
            @RequestParam(required = false) String puerto, @RequestParam(required = false) BigDecimal precioMax) {
        return service.catalog(especieId, puerto, precioMax);
    }

    @GetMapping("/lotes-disponibles")
    @Operation(summary = "Lotes propios que se pueden ofertar, con sus datos para precargar el asistente")
    public List<EligibleLot> eligibleLots() {
        return service.eligibleLots();
    }

    @PostMapping("/ofertas")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publica la oferta de un lote trazable (vence a las 72 h)")
    public OfferCard publish(@Valid @RequestBody PublishRequest request) {
        return service.publish(request);
    }

    @PostMapping("/ofertas/{id}/retirar")
    @Operation(summary = "El vendedor retira su oferta")
    public OfferCard withdraw(@PathVariable String id) {
        return service.withdraw(id);
    }

    @GetMapping("/mis-ofertas")
    @Operation(summary = "Ofertas del vendedor con las solicitudes recibidas (compradores con alias)")
    public List<MyOffer> myOffers() {
        return service.myOffers();
    }

    @PostMapping("/ofertas/{id}/solicitudes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Un comprador registrado solicita la compra")
    public MyRequest request(@PathVariable String id, @Valid @RequestBody RequestCreate request) {
        return service.request(id, request);
    }

    @GetMapping("/mis-solicitudes")
    @Operation(summary = "Solicitudes enviadas por el comprador")
    public List<MyRequest> myRequests() {
        return service.myRequests();
    }

    @PostMapping("/solicitudes/{id}/aceptar")
    @Operation(summary = "Acepta la solicitud: registra la entrega en la cadena de custodia y cierra la oferta")
    public List<MyOffer> accept(@PathVariable String id) {
        return service.decide(id, true);
    }

    @PostMapping("/solicitudes/{id}/rechazar")
    @Operation(summary = "Rechaza la solicitud")
    public List<MyOffer> reject(@PathVariable String id) {
        return service.decide(id, false);
    }
}
