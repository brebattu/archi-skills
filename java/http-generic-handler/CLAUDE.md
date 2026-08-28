# Socle technique d'appel partenaires (HTTP + Kafka)

Contexte : plusieurs partenaires API. La logique **fonctionnelle** diffère par
partenaire, mais toute la mécanique **technique** (timeouts, retry, circuit
breaker, mapping des erreurs) est commune. Ce document est la spec de référence
du socle à implémenter.

> Pour une explication pédagogique complète du retry et du circuit breaker
> (modes, seuils, exemples), voir [README.md](README.md). Pour la vue
> d'ensemble de l'architecture et les diagrammes UML, voir
> [ARCHITECTURE.md](ARCHITECTURE.md).

Stack cible : **Spring Boot 3.4+** (API `ClientHttpRequestFactoryBuilder`),
Resilience4j (`resilience4j-spring-boot3`), Spring Kafka. Java 17+.
> Si le projet est en Boot 3.2/3.3, remplacer `ClientHttpRequestFactoryBuilder`
> par `ClientHttpRequestFactories` + `ClientHttpRequestFactorySettings`.

---

## Principes directeurs

1. **Séparer socle technique et logique fonctionnelle.** Une classe abstraite
   `AbstractPartnerClient` porte toute la mécanique. Chaque partenaire n'écrit
   que ses endpoints et son mapping de données.
2. **Le mapping `exception Spring → exception métier` est écrit une seule fois**
   dans le socle. Aucun client fonctionnel ne voit jamais une exception Spring
   ni un `try/catch`.
3. **Deux exceptions métier seulement**, porteuses d'un `ErrorCode` :
   - `TechnicalException` → panne technique (peut être retryable selon le code).
   - `FunctionalException` → erreur métier (jamais de retry, n'alimente pas le breaker).
   Les exceptions ne portent PAS de flag `retryable` : la décision vit ailleurs.
4. **La liste des codes retryables appartient au partenaire** (dépend de lui),
   pilotée dans ses properties.
5. **Générique + surcharge ponctuelle** : `handleError` est une vraie méthode
   template, overridable directement (pas un hook à `Optional`). Un partenaire
   avec un code maison override, traite son cas, puis appelle
   `super.handleError(...)` pour retomber sur le mapping générique. Ne rien
   lever laisse la réponse être traitée comme un succès — utile pour renvoyer
   tel quel un statut d'erreur porteur d'un corps exploitable.
6. **Retry sur opérations idempotentes uniquement.** Prévoir une variante sans
   retry (CB seul) pour les POST non idempotents si besoin.

---

## Composants

### Exceptions + ErrorCode

```java
public enum ErrorCode {
    PARTNER_TIMEOUT,
    PARTNER_5XX,
    PARTNER_RATE_LIMITED,
    PARTNER_4XX,
    PARTNER_UNAUTHORIZED,
    PARTNER_TECHNICAL,
    PARTNER_CIRCUIT_OPEN  // breaker OPEN : l'appel n'a pas atteint le réseau, détectable par l'appelant
    // + codes maison par partenaire, ex. PARTNER_B_QUOTA
}

public class TechnicalException extends RuntimeException {
    private final ErrorCode code;
    public TechnicalException(ErrorCode code, String msg, Throwable cause) {
        super(msg, cause);
        this.code = code;
    }
    public ErrorCode getCode() { return code; }
}

public class FunctionalException extends RuntimeException {
    private final ErrorCode code;
    public FunctionalException(ErrorCode code, String msg) {
        super(msg);
        this.code = code;
    }
    public ErrorCode getCode() { return code; }
}
```

### Properties partenaires (retryable-codes au plus près du partenaire)

```java
@ConfigurationProperties(prefix = "partners")
public record PartnersProperties(Map<String, ClientConfig> clients) {
    public record ClientConfig(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Set<ErrorCode> retryableCodes,
        Set<ErrorCode> circuitBreakerCodes  // optionnel : null -> toute TechnicalException compte
    ) {}
}
```

```yaml
partners:
  clients:
    partnerA:
      base-url: https://api.partner-a.com
      connect-timeout: 3s
      read-timeout: 5s
      retryable-codes: [PARTNER_TIMEOUT, PARTNER_5XX, PARTNER_RATE_LIMITED]
    partnerB:
      base-url: https://api.partner-b.com
      connect-timeout: 2s
      read-timeout: 8s
      retryable-codes: [PARTNER_TIMEOUT, PARTNER_5XX, PARTNER_B_QUOTA]  # B retry sur son quota
      # circuit-breaker-codes: optionnel, non renseigné ici -> défaut (toute TechnicalException compte)

resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
    instances:
      partnerA: { base-config: default }
      partnerB: { base-config: default, max-attempts: 5 }
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
    instances:
      partnerA: { base-config: default }
      partnerB: { base-config: default }
```

> Les properties Resilience4j ne pilotent QUE les valeurs numériques.
> Le critère retryable passe par un predicate Java construit avec le
> `Set<ErrorCode>` du partenaire (voir socle). Ne PAS utiliser `retry-exceptions`
> / `ignore-exceptions` dans le yaml pour ce socle.

### AbstractPartnerClient (socle)

Le montage : le `RestClient` porte les timeouts + un intercepteur de log (une
ligne par appel HTTP, y compris à chaque tentative de retry) + un
`ResponseErrorHandler` interne qui délègue à `handleError`, une vraie méthode
template overridable. Le retry utilise un predicate construit sur le
`Set<ErrorCode>` du partenaire. Le breaker enregistre toute `TechnicalException`.

Ordre des décorateurs : `Retry(CircuitBreaker(call))` → le breaker s'exécute à
l'intérieur, chaque tentative alimente ses stats.

```java
public abstract class AbstractPartnerClient {

    protected final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    protected AbstractPartnerClient(String partnerName,
                                    PartnersProperties.ClientConfig cfg,
                                    RetryRegistry rr,
                                    CircuitBreakerRegistry cbr) {
        Logger callLog = LoggerFactory.getLogger(AbstractPartnerClient.class.getName() + "." + partnerName);

        this.restClient = RestClient.builder()
            .baseUrl(cfg.baseUrl())
            .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                    .withConnectTimeout(cfg.connectTimeout())
                    .withReadTimeout(cfg.readTimeout())))
            .requestInterceptor(loggingInterceptor(callLog))
            .defaultStatusHandler(new StatusHandlerAdapter())
            .build();

        Set<ErrorCode> retryable = cfg.retryableCodes();
        this.retry = rr.retry(partnerName + "-managed",
            RetryConfig.from(rr.getConfiguration(partnerName).orElse(rr.getDefaultConfig()))
                .retryOnException(t -> t instanceof TechnicalException e
                        && retryable.contains(e.getCode()))
                .build());

        Set<ErrorCode> cbCodes = cfg.circuitBreakerCodes();  // null = toute TechnicalException compte
        this.circuitBreaker = cbr.circuitBreaker(partnerName + "-managed",
            CircuitBreakerConfig.from(cbr.getConfiguration(partnerName).orElse(cbr.getDefaultConfig()))
                .recordException(t -> t instanceof TechnicalException e
                        && (cbCodes == null || cbCodes.contains(e.getCode())))
                .build());
    }

    /** Signale un échec au breaker sans lever d'exception — pour un handleError
     *  "passthrough" qui veut quand même faire compter certains codes techniques. */
    protected void recordCircuitBreakerFailure(ErrorCode code) {
        circuitBreaker.onError(0, TimeUnit.NANOSECONDS,
            new TechnicalException(code, "signalé manuellement (passthrough)"));
    }

    /** Mapping HTTP -> exception métier. Un partenaire avec un code maison override,
     *  traite son cas, puis appelle super.handleError(...) pour retomber sur ce
     *  mapping générique. Ne rien lever = la réponse est traitée comme un succès
     *  (le corps est désérialisé normalement par retrieve().body(...)). */
    protected void handleError(HttpStatusCode status, ClientHttpResponse response) throws IOException {
        if (status.is5xxServerError()) throw new TechnicalException(ErrorCode.PARTNER_5XX, "5xx");
        if (status.value() == 429)     throw new TechnicalException(ErrorCode.PARTNER_RATE_LIMITED, "429");
        if (status.value() == 401 || status.value() == 403)
                                        throw new FunctionalException(ErrorCode.PARTNER_UNAUTHORIZED, "auth");
        throw new FunctionalException(ErrorCode.PARTNER_4XX, "4xx " + status.value());
    }

    protected <T> T execute(Supplier<T> call) {
        return runProtected(call, true);
    }

    /** CB seul, sans retry — pour les opérations non idempotentes (POST). */
    protected <T> T executeNoRetry(Supplier<T> call) {
        return runProtected(call, false);
    }

    private <T> T runProtected(Supplier<T> call, boolean withRetry) {
        Supplier<T> withNetworkMapping = () -> {
            try {
                return call.get();  // erreurs HTTP déjà mappées par handleError
            } catch (ResourceAccessException e) {  // timeout/connexion : levé avant réponse
                throw new TechnicalException(ErrorCode.PARTNER_TIMEOUT, "réseau", e);
            }
        };
        Supplier<T> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, withNetworkMapping);
        Supplier<T> decorated = withRetry ? Retry.decorateSupplier(retry, withCb) : withCb;

        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            throw new TechnicalException(ErrorCode.PARTNER_CIRCUIT_OPEN, "circuit breaker ouvert", e);
        }
    }

    private static ClientHttpRequestInterceptor loggingInterceptor(Logger callLog) {
        return (request, body, execution) -> {
            long start = System.nanoTime();
            try {
                ClientHttpResponse response = execution.execute(request, body);
                callLog.info("{} {} -> {} ({} ms)", request.getMethod(), request.getURI(),
                        response.getStatusCode().value(), (System.nanoTime() - start) / 1_000_000);
                return response;
            } catch (IOException e) {
                callLog.warn("{} {} -> échec réseau ({} ms) : {}", request.getMethod(), request.getURI(),
                        (System.nanoTime() - start) / 1_000_000, e.getMessage());
                throw e;
            }
        };
    }

    private class StatusHandlerAdapter implements ResponseErrorHandler {
        @Override public boolean hasError(ClientHttpResponse res) throws IOException {
            return res.getStatusCode().isError();
        }
        @Override public void handleError(ClientHttpResponse res) throws IOException {
            AbstractPartnerClient.this.handleError(res.getStatusCode(), res);
        }
    }
}
```

> **Logs** : l'intercepteur logge chaque appel HTTP (méthode, URI, statut, durée),
> y compris à chaque tentative de retry — sur un logger nommé
> `AbstractPartnerClient.<nomPartenaire>`, configurable finement côté logback.
> Volontairement, il ne lit jamais le corps (pas de buffering, pas de risque de
> fuite de données sensibles).

### Clients fonctionnels

Standard — n'override rien :

```java
@Component
class PartnerAClient extends AbstractPartnerClient {
    PartnerAClient(PartnersProperties p, RetryRegistry rr, CircuitBreakerRegistry cbr) {
        super("partnerA", p.clients().get("partnerA"), rr, cbr);
    }
    public OrderDto getOrder(String id) {
        return execute(() -> restClient.get().uri("/orders/{id}", id).retrieve().body(OrderDto.class));
    }
}
```

Avec code d'erreur maison — override `handleError` uniquement, et appelle
`super.handleError(...)` pour le reste. Cet exemple illustre aussi le cas
« renvoyer tel quel » : sur un 404 métier, on ne lève rien et le corps est
désérialisé normalement.

```java
@Component
class PartnerBClient extends AbstractPartnerClient {

    PartnerBClient(PartnersProperties p, RetryRegistry rr, CircuitBreakerRegistry cbr) {
        super("partnerB", p.clients().get("partnerB"), rr, cbr);
    }

    @Override
    protected void handleError(HttpStatusCode status, ClientHttpResponse res) throws IOException {
        if (status.value() == 422) {
            String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("QUOTA_EXCEEDED")) {
                throw new TechnicalException(ErrorCode.PARTNER_B_QUOTA, "quota");
            }
        }
        if (status.value() == 404) {
            return;  // facture archivée : corps InvoiceDto exploitable, pas d'exception
        }
        super.handleError(status, res);  // le reste → mapping générique
    }

    public InvoiceDto getInvoice(String ref) {
        return execute(() -> restClient.get().uri("/invoices/{ref}", ref).retrieve().body(InvoiceDto.class));
    }
}
```

---

## Ajouter un partenaire = 3 gestes

1. Un bloc dans `partners.clients.<nom>` (base-url, timeouts, retryable-codes).
2. Deux instances `resilience4j` (`retry` + `circuitbreaker`) avec `base-config: default`.
3. Une classe `@Component` qui `extends AbstractPartnerClient`. Override
   `handleError` seulement si le partenaire a un code maison (traiter le cas,
   puis `super.handleError(...)` pour le reste) ; ajouter alors le nouvel
   `ErrorCode` à l'enum (+ dans `retryable-codes` s'il doit être retryable).

---

## Kafka (Spring Kafka)

La **classification d'erreurs** (`TechnicalException`/`FunctionalException` +
`ErrorCode`) est 100 % réutilisable. Le mécanisme d'application diffère.

### Producer

`execute(...)` réutilisable en rendant l'envoi synchrone :

```java
public void publish(String topic, Object payload) {
    execute(() -> kafkaTemplate.send(topic, payload).get());
}
```

⚠️ Le producer Kafka a DÉJÀ ses retries natifs (`retries`, `delivery.timeout.ms`,
`acks`). Ne PAS empiler un retry Resilience4j par-dessus (effet multiplicatif).
Règle : laisser le producer gérer ses retries de livraison ; réserver le circuit
breaker Resilience4j pour couper quand le broker est durablement down.

### Consumer

Un `@KafkaListener` n'est pas un appel qu'on déclenche : Spring pousse les
messages. `execute` ne s'applique PAS. Utiliser `DefaultErrorHandler` + backoff +
DLT. La distinction Technical/Functional se branche via
`addNotRetryableExceptions`.

```java
@Bean
DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
    var recoverer = new DeadLetterPublishingRecoverer(template);
    var backoff = new ExponentialBackOffWithMaxRetries(3);
    backoff.setInitialInterval(500);
    backoff.setMultiplier(2);
    var handler = new DefaultErrorHandler(recoverer, backoff);
    handler.addNotRetryableExceptions(FunctionalException.class);  // métier → DLT direct
    return handler;
}
```

Équivalences : `addNotRetryableExceptions`/`addRetryableExceptions` ↔ le
`Set<ErrorCode>` ; la DLT ↔ le fallback.

### Circuit breaker sur consumer

Resilience4j `@CircuitBreaker` ne sait pas mettre la consommation en pause. Pour
arrêter de consommer quand l'aval est down : `MessageListenerContainer.pause()` /
`resume()`, ou `ContainerStoppingErrorHandler`. Le breaker peut piloter la
décision, mais l'action (pauser le container) est spécifique Kafka.

---

## Points de vigilance (à respecter à l'implémentation)

- Retry uniquement sur opérations idempotentes. Pour un POST non idempotent,
  exposer une variante `executeNoRetry()` (CB seul) dans le socle.
- Ne pas dupliquer le mapping d'erreurs : un seul point, le `ResponseErrorHandler`.
- Ne pas utiliser `retry-exceptions`/`ignore-exceptions` en yaml ; predicate Java.
- `ResourceAccessException` (timeout/réseau) ne passe pas par le status handler
  (levée avant réponse) → catchée dans `execute`.
- Si le nombre de partenaires explose, envisager l'enregistrement dynamique des
  beans (`BeanDefinitionRegistryPostProcessor` itérant sur `props.clients()`),
  mais seulement si la duplication des `@Bean` devient réellement pénible.
