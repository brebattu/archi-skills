package com.example.partners.client.partnerb;

import com.example.partners.client.AbstractPartnerClient;
import com.example.partners.config.PartnersProperties;
import com.example.partners.error.ErrorCode;
import com.example.partners.error.TechnicalException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Client avec code d'erreur maison : override {@link #handleError} uniquement,
 * le reste du socle (timeouts, logs, retry, circuit breaker) est inchangé.
 */
@Component
public class PartnerBClient extends AbstractPartnerClient {

    public PartnerBClient(PartnersProperties properties, RetryRegistry retryRegistry,
                           CircuitBreakerRegistry circuitBreakerRegistry) {
        super("partnerB", properties.clients().get("partnerB"), retryRegistry, circuitBreakerRegistry);
    }

    @Override
    protected void handleError(HttpStatusCode status, ClientHttpResponse response) throws IOException {
        if (status.value() == 422) {
            String body = readBody(response);
            if (body.contains("QUOTA_EXCEEDED")) {
                throw new TechnicalException(ErrorCode.PARTNER_B_QUOTA, "quota");
            }
        }
        if (status.value() == 404) {
            // Le partenaire B renvoie parfois un 404 "métier" (facture archivée) avec un
            // corps InvoiceDto exploitable : on ne lève rien, la réponse est désérialisée
            // normalement par retrieve().body(...), comme pour un succès.
            return;
        }
        super.handleError(status, response); // le reste -> mapping générique
    }

    private String readBody(ClientHttpResponse response) throws IOException {
        return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    public InvoiceDto getInvoice(String ref) {
        return execute(() -> restClient.get().uri("/invoices/{ref}", ref).retrieve().body(InvoiceDto.class));
    }
}
