package com.example.partners.client.partnerb;

import com.example.partners.config.PartnersProperties;
import com.example.partners.error.ErrorCode;
import com.example.partners.error.FunctionalException;
import com.example.partners.error.TechnicalException;
import com.example.partners.support.StubHttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PartnerBClientTest {

    private StubHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    private PartnerBClient client(Set<ErrorCode> retryableCodes) {
        server = new StubHttpServer();
        PartnersProperties.ClientConfig cfg = new PartnersProperties.ClientConfig(
                server.baseUrl(), Duration.ofMillis(300), Duration.ofMillis(300), retryableCodes, null);
        PartnersProperties properties = new PartnersProperties(Map.of("partnerB", cfg));

        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(10)).build());
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(100).minimumNumberOfCalls(100).build());

        return new PartnerBClient(properties, retryRegistry, cbRegistry);
    }

    @Test
    void quota_exceeded_body_maps_to_house_error_code_and_is_retried() {
        PartnerBClient client = client(Set.of(ErrorCode.PARTNER_B_QUOTA));
        server.setDefaultResponse(422, "{\"reason\":\"QUOTA_EXCEEDED\"}");

        assertThatThrownBy(() -> client.getInvoice("REF-1"))
                .isInstanceOf(TechnicalException.class)
                .satisfies(e -> assertThat(((TechnicalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_B_QUOTA));
        assertThat(server.requestCount()).isEqualTo(3); // retryable -> max-attempts consommées
    }

    @Test
    void other_422_falls_back_to_generic_mapping_and_is_not_retried() {
        PartnerBClient client = client(Set.of(ErrorCode.PARTNER_B_QUOTA));
        server.enqueue(422, "{\"reason\":\"VALIDATION_ERROR\"}");

        assertThatThrownBy(() -> client.getInvoice("REF-1"))
                .isInstanceOf(FunctionalException.class)
                .satisfies(e -> assertThat(((FunctionalException) e).getCode())
                        .isEqualTo(ErrorCode.PARTNER_4XX));
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void passthrough_404_is_deserialized_normally_without_throwing() {
        // Cas "renvoyer tel quel" : handleError override le 404 et ne lève rien ->
        // le corps est désérialisé comme pour un succès.
        PartnerBClient client = client(Set.of());
        server.enqueue(404, "{\"ref\":\"REF-1\",\"amount\":123.45}");

        InvoiceDto invoice = client.getInvoice("REF-1");

        assertThat(invoice.ref()).isEqualTo("REF-1");
        assertThat(invoice.amount()).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(server.requestCount()).isEqualTo(1);
    }
}
