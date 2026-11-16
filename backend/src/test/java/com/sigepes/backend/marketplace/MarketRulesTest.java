package com.sigepes.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.sigepes.backend.alerts.AlertRepository;
import com.sigepes.backend.common.AppClock;
import com.sigepes.backend.permits.PermitStatus;
import com.sigepes.backend.species.SpeciesEntity;
import com.sigepes.backend.support.ApiTestSupport;
import com.sigepes.backend.users.Role;
import com.sigepes.backend.users.UserEntity;
import com.sigepes.backend.vessels.VesselEntity;
import com.sigepes.backend.vessels.VesselStatus;

import jakarta.persistence.EntityManager;

/** HU-16 · Marketplace de producto legal: publicación, catálogo sin datos personales, venta y vencimiento. */
class MarketRulesTest extends ApiTestSupport {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private UserEntity fisher;
    private UserEntity buyer;
    private SpeciesEntity snapper;
    private String fisherToken;
    private String buyerToken;
    private String otherBuyerToken;
    private String lotId;
    private String lotCode;

    @BeforeEach
    void setUp() throws Exception {
        fisher = givenUser("4040404040", "pescador.mer@sigepes.gov.co", Role.FISHER);
        buyer = givenUser("4141414141", "comprador.mer@sigepes.gov.co", Role.FISHER);
        givenUser("4242424242", "otro.mer@sigepes.gov.co", Role.SHIPOWNER);
        VesselEntity vessel = givenVessel(fisher, "DIMAR-MER-001", VesselStatus.ACTIVE, givenGear("Línea mer", false));
        snapper = givenSpecies("Pargo mer", new BigDecimal("35.00"));
        givenPermit(fisher, "PES-MER-1", PermitStatus.APPROVED, AppClock.today().minusMonths(1),
                AppClock.today().plusMonths(6));
        fisherToken = bearerFor("pescador.mer@sigepes.gov.co");
        buyerToken = bearerFor("comprador.mer@sigepes.gov.co");
        otherBuyerToken = bearerFor("otro.mer@sigepes.gov.co");

        String tripId = apiStartedTripId(fisherToken, vessel.getId(), "Pacífico Norte");
        apiAddCatch(fisherToken, tripId, snapper.getId(), "40", "120").andExpect(status().isOk());
        apiCloseTrip(fisherToken, tripId).andExpect(status().isOk());
        String catchId = extract(mockMvc.perform(get("/api/v1/faenas/" + tripId + "/capturas-para-lote")
                .header("Authorization", fisherToken)).andReturn().getResponse().getContentAsString(), "catchId");
        String lot = mockMvc.perform(post("/api/v1/lotes").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"catchId\":\"" + catchId + "\",\"weightKg\":80}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        lotId = extract(lot, "id");
        lotCode = extract(lot, "code");
    }

    @Test
    @DisplayName("TC-HU16-01 · AC-16.1 / AC-16.6 · El asistente precarga el lote y la oferta sale en el catálogo público")
    void publishesVerifiedLot() throws Exception {
        mockMvc.perform(get("/api/v1/mercado/lotes-disponibles").header("Authorization", fisherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lotCode").value(lotCode))
                .andExpect(jsonPath("$[0].weightKg").value(80.0))
                .andExpect(jsonPath("$[0].species").value("Pargo mer"));

        publish(fisherToken, "18000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(1440000))
                .andExpect(jsonPath("$.verificationPath").value("/verificar/" + lotCode));

        // Catálogo sin sesión, con filtros.
        mockMvc.perform(get("/api/v1/mercado/ofertas").param("especieId", snapper.getId()).param("puerto", "bahia solano"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lotCode").value(lotCode));
        mockMvc.perform(get("/api/v1/mercado/ofertas").param("precioMax", "15000"))
                .andExpect(jsonPath("$.length()").value(0));
        // Un lote ya ofertado no se vuelve a ofertar.
        publish(fisherToken, "17000").andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("TC-HU16-02 · AC-16.5 · S5 · El catálogo y las solicitudes no exponen cédula, correo ni teléfono")
    void neverExposesPersonalData() throws Exception {
        String offerId = extract(publish(fisherToken, "18000").andReturn().getResponse().getContentAsString(), "id");
        request(buyerToken, offerId, "20").andExpect(status().isCreated());

        String catalog = mockMvc.perform(get("/api/v1/mercado/ofertas"))
                .andExpect(jsonPath("$[0].sellerAlias").exists())
                .andExpect(jsonPath("$[0].municipality").value("Bahía Solano"))
                .andReturn().getResponse().getContentAsString();
        String seller = mockMvc.perform(get("/api/v1/mercado/mis-ofertas").header("Authorization", fisherToken))
                .andExpect(jsonPath("$[0].requests[0].buyerAlias").exists())
                .andReturn().getResponse().getContentAsString();
        for (String json : new String[] { catalog, seller }) {
            assertThat(json).doesNotContain(fisher.getNationalId(), fisher.getEmail(), buyer.getNationalId(),
                    buyer.getEmail(), "nationalId", "email", "phone");
        }
    }

    @Test
    @DisplayName("TC-HU16-03 · AC-16.3 · Aceptar la solicitud registra la venta en la cadena de custodia")
    void acceptingRegistersCustody() throws Exception {
        String offerId = extract(publish(fisherToken, "18000").andReturn().getResponse().getContentAsString(), "id");
        String accepted = extract(request(buyerToken, offerId, "80").andReturn().getResponse().getContentAsString(), "id");
        String other = extract(request(otherBuyerToken, offerId, "40").andReturn().getResponse().getContentAsString(), "id");
        request(buyerToken, offerId, "10").andExpect(status().isConflict());
        assertThat(alertRepository.existsByRecipientIdAndSourceKey(fisher.getId(), "market:request:" + accepted)).isTrue();

        mockMvc.perform(post("/api/v1/mercado/solicitudes/" + accepted + "/aceptar").header("Authorization", buyerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/mercado/solicitudes/" + accepted + "/aceptar").header("Authorization", fisherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SOLD"));

        mockMvc.perform(get("/api/v1/trazabilidad/" + lotCode))
                .andExpect(jsonPath("$.chain.length()").value(2))
                .andExpect(jsonPath("$.chain[1].partyType").value("BUYER"));
        mockMvc.perform(get("/api/v1/mercado/mis-solicitudes").header("Authorization", otherBuyerToken))
                .andExpect(jsonPath("$[0].id").value(other))
                .andExpect(jsonPath("$[0].status").value("REJECTED"));
        mockMvc.perform(get("/api/v1/mercado/ofertas")).andExpect(jsonPath("$.length()").value(0));
        // Un lote entregado ya no se puede volver a ofertar.
        publish(fisherToken, "18000").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MARKET_LOT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("TC-HU16-04 · AC-16.4 · La oferta vence a las 72 h y deja de recibir solicitudes")
    void offerExpires() throws Exception {
        String offerId = extract(publish(fisherToken, "18000").andReturn().getResponse().getContentAsString(), "id");
        // La oferta se escribe en la base antes de vencerla a mano (si no, clear() la descartaría).
        entityManager.flush();
        jdbc.update("UPDATE market_offers SET expires_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), offerId);
        entityManager.clear();

        mockMvc.perform(get("/api/v1/mercado/ofertas")).andExpect(jsonPath("$.length()").value(0));
        request(buyerToken, offerId, "10").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MARKET_OFFER_CLOSED"));
        mockMvc.perform(get("/api/v1/mercado/mis-ofertas").header("Authorization", fisherToken))
                .andExpect(jsonPath("$[0].status").value("EXPIRED"));
        // Vencida la oferta, el lote vuelve a estar disponible para ofertarlo otra vez.
        publish(fisherToken, "16000").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TC-HU16-05 · AC-16.1 · Solo el titular oferta su lote; sin trazabilidad se rechaza")
    void onlyOwnerAndTraceableLots() throws Exception {
        mockMvc.perform(post("/api/v1/mercado/ofertas").header("Authorization", buyerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"lotId\":\"" + lotId + "\",\"pricePerKg\":18000}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/mercado/ofertas").header("Authorization", fisherToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"lotId\":\"no-existe\",\"pricePerKg\":18000}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/mercado/lotes-disponibles")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/mercado/ofertas")).andExpect(content().string(not(containsString("comprador"))));
    }

    @Test
    @DisplayName("Alias: nombre de pila e inicial del primer apellido")
    void aliasKeepsOnlyFirstNameAndInitial() {
        assertThat(MarketService.alias("Aníbal Rentería Mosquera")).isEqualTo("Aníbal R.");
        assertThat(MarketService.alias("Carolina")).isEqualTo("Carolina");
        assertThat(MarketService.alias(null)).isEqualTo("Usuario");
    }

    private ResultActions publish(String token, String price) throws Exception {
        return mockMvc.perform(post("/api/v1/mercado/ofertas").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lotId\":\"" + lotId + "\",\"pricePerKg\":" + price + "}"));
    }

    private ResultActions request(String token, String offerId, String kg) throws Exception {
        return mockMvc.perform(post("/api/v1/mercado/ofertas/" + offerId + "/solicitudes").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantityKg\":" + kg + ",\"message\":\"Me interesa para mi restaurante\"}"));
    }
}
