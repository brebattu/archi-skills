package com.example.partners.client;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.partners.config.PartnersProperties;
import com.example.partners.support.StubHttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractPartnerClientLoggingTest {

    private StubHttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void logs_method_uri_status_and_duration_for_each_call() {
        server = new StubHttpServer();
        server.enqueue(200, "\"ok\"");

        PartnersProperties.ClientConfig cfg = new PartnersProperties.ClientConfig(
                server.baseUrl(), Duration.ofMillis(300), Duration.ofMillis(300), Set.of(), null);
        RetryRegistry retryRegistry = RetryRegistry.of(RetryConfig.custom()
                .maxAttempts(3).waitDuration(Duration.ofMillis(10)).build());
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(100).minimumNumberOfCalls(100).build());

        // Le logger est nommé d'après AbstractPartnerClient + le nom du partenaire ("partnerTest" ici).
        ch.qos.logback.classic.Logger callLog = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(
                AbstractPartnerClient.class.getName() + ".partnerTest");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        callLog.addAppender(appender);

        try {
            new TestPartnerClient(cfg, retryRegistry, cbRegistry).callWithRetry();
        } finally {
            callLog.detachAppender(appender);
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("GET")
                .contains("200")
                .contains("ms");
    }
}
