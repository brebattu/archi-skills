# order-management sample project

Squelette Maven multi-module en architecture hexagonale (ports & adapters), servant de projet
support pour construire et illustrer une gestion d'erreur/exception Java/Spring réutilisable
(dossier `java/error-handling`).

**Ce projet n'est volontairement pas une application complète.** Les interfaces, classes et le
câblage Spring sont posés au minimum nécessaire pour compiler et verrouiller la structure ;
d'autres approfondissements (i18n des messages, retry/DLQ Kafka, tests d'intégration) restent une
itération future. La gestion d'erreur, elle, est l'objet même de ce projet et est documentée de
façon exhaustive ci-dessous.

## Modules

| Module                  | Rôle                                                             |
|--------------------------|-------------------------------------------------------------------|
| `error-kernel`            | Socle de gestion d'erreur générique et réutilisable : `ErrorCode`, `ApplicationException`, `FunctionalException`, `TechnicalException`. Zéro dépendance Spring, zéro référence au domaine "order". |
| `order-core`             | Cœur applicatif : domaine, ports (in/out), use cases, `OrderErrorCode`. Zéro dépendance Spring. |
| `order-api`               | Adapter "in" : API REST (Spring MVC), DTOs, `@RestControllerAdvice`. |
| `order-persistence`       | Adapter "out" : persistance JPA/PostgreSQL + migrations Flyway, `OrderPersistenceErrorCode`. |
| `order-messaging-kafka`   | Adapter "out" : publication d'événements Kafka, `OrderMessagingErrorCode`. |
| `order-bootstrap`         | Application Spring Boot exécutable : câblage des beans, configuration. |

`order-core` ne dépend d'aucune brique Spring : le bean `OrderService` est instancié
explicitement dans `order-bootstrap` (`config/CoreBeansConfig`) plutôt que découvert par
component-scan, pour garder le métier framework-agnostic. `error-kernel` suit le même principe et
n'a même pas de référence au domaine "order" : il est pensé pour être réutilisé tel quel dans
d'autres projets.

## Gestion d'erreur

### Principes directeurs

1. **Unchecked partout.** `ApplicationException extends RuntimeException` — en Java, "unchecked"
   *veut dire* `RuntimeException`, ce n'est pas un choix distinct.
2. **Pas d'exception par cas d'usage.** Aucune classe du style `OrderNotFoundException`,
   `InvalidOrderStateException`, etc. Le cas précis est porté par un **code incident** (`ErrorCode`,
   une valeur d'enum), pas par le type de la classe Java. Ça évite la multiplication de petites
   classes qui ne font qu'hériter, dures à maintenir et à documenter.
3. **Deux classes concrètes seulement**, définies une fois pour toutes dans `error-kernel` :
   - `FunctionalException` : erreur métier **attendue** (commande introuvable, transition d'état
     invalide, saisie invalide...). Le client peut agir dessus.
   - `TechnicalException` : panne d'infrastructure **connue et potentiellement retryable** (base
     de données indisponible, broker Kafka injoignable, échec de sérialisation...).
4. **Un bug de code n'est ni l'un ni l'autre.** Une `NullPointerException` ou toute exception non
   volontairement levée par le code applicatif ne passe **jamais** par `ApplicationException`. Elle
   remonte telle quelle et termine dans un handler générique séparé. Raison concrète : si une
   politique de retry ou d'alerting est un jour branchée sur `TechnicalException` (ex. un consumer
   Kafka qui retry automatiquement sur panne "connue"), confondre un bug déterministe avec une
   panne transitoire ferait retry indéfiniment un message empoisonné au lieu de le mettre en
   dead-letter et d'alerter.
5. **Un enum de code par module Maven, pas un enum géant partagé.** Chaque module qui lance des
   erreurs possède son propre enum `ErrorCode` : `order-core` ne connaît que ses erreurs
   fonctionnelles, chaque adapter technique (`order-persistence`, `order-messaging-kafka`) ne
   connaît que ses propres pannes. `order-core` ne doit jamais contenir de valeur qu'il ne lance
   pas lui-même (piège identifié et corrigé une fois pendant la construction de ce squelette : les
   codes techniques avaient d'abord été mis à tort dans l'enum du domaine).
6. **Le mapping HTTP reste hors du domaine.** `ErrorCode` ne porte pas de `HttpStatus` : ce type
   vient de Spring, et `order-core` doit rester zéro-dépendance Spring. Le mapping
   `ErrorCode → HttpStatus` vit uniquement dans `order-api`, sous forme de **switch expression
   exhaustif sans `default`** : si on ajoute une valeur à l'enum sans la mapper, le compilateur
   refuse de compiler. C'est une garantie gratuite, sans avoir besoin de sealed interfaces.
7. **Chaque adapter traduit ses propres pannes techniques à sa frontière.** `order-persistence`
   attrape les `DataAccessException` de Spring Data et les retraduit en `TechnicalException`
   avant qu'elles ne remontent ; `order-messaging-kafka` fait de même avec `KafkaException`. Une
   exception technique connue ne doit jamais remonter brute jusqu'à l'API, sinon elle tomberait à
   tort dans le bucket "bug" du handler générique.
8. **La validation de requête est hors hiérarchie.** `MethodArgumentNotValidException` (levée par
   Spring avant même d'atteindre un use case, sur un `@Valid` qui échoue) n'est pas une violation
   de règle métier : c'est une requête HTTP malformée. Elle a son propre handler dédié, distinct de
   `FunctionalException`.

### Le socle (`error-kernel`)

```java
public interface ErrorCode {
    String code();
}

public abstract sealed class ApplicationException extends RuntimeException
        permits FunctionalException, TechnicalException {

    private final ErrorCode errorCode;

    protected ApplicationException(ErrorCode errorCode, String message) { ... }
    protected ApplicationException(ErrorCode errorCode, String message, Throwable cause) { ... }

    public ErrorCode errorCode() { return errorCode; }
}

public final class FunctionalException extends ApplicationException { ... }
public final class TechnicalException extends ApplicationException { ... }
```

`ApplicationException` est `sealed` : seules `FunctionalException` et `TechnicalException` peuvent
en hériter, ce qui rend explicite qu'il n'y aura jamais de troisième sous-classe créée à la volée
dans un module métier.

### Convention : un enum de code par module

| Module                  | Enum                       | Exemples de valeurs                                              | Lancé par |
|--------------------------|-----------------------------|--------------------------------------------------------------------|-----------|
| `order-core`             | `OrderErrorCode`            | `ORDER_NOT_FOUND`, `ORDER_ALREADY_CANCELLED`, `INVALID_CUSTOMER_ID`, `INVALID_ORDER_AMOUNT` | `Order.java`, `OrderService.java` |
| `order-persistence`       | `OrderPersistenceErrorCode` | `ORDER_PERSISTENCE_FAILURE`                                        | `OrderRepositoryAdapter.java` |
| `order-messaging-kafka`   | `OrderMessagingErrorCode`   | `ORDER_EVENT_PUBLISHING_FAILURE`                                    | `OrderEventPublisherAdapter.java` |

Chaque enum implémente simplement `ErrorCode` :

```java
public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND, INVALID_CUSTOMER_ID, INVALID_ORDER_AMOUNT, ORDER_ALREADY_CANCELLED;

    @Override
    public String code() { return name(); }
}
```

### Cycle de vie d'une erreur fonctionnelle (exemple : commande introuvable)

1. `OrderService.getOrder(id)` appelle `orderRepository.findById(id)` → `Optional.empty()`.
2. `.orElseThrow(() -> new FunctionalException(OrderErrorCode.ORDER_NOT_FOUND, "Order not found: " + id))`.
3. L'exception remonte telle quelle à travers `OrderController` — aucun `catch` intermédiaire.
4. `GlobalExceptionHandler.handleFunctional` l'intercepte, cast `ex.errorCode()` vers
   `OrderErrorCode` (légitime : `order-api` ne gère que le contexte "order" pour l'instant), et le
   switch exhaustif renvoie `HttpStatus.NOT_FOUND`.
5. Le client reçoit un `ProblemDetail` (RFC 7807) 404 avec le message.

### Cycle de vie d'une panne technique (exemple : base de données indisponible)

1. `OrderJpaRepository.save(...)` lève une `DataAccessException` (hiérarchie Spring Data).
2. `OrderRepositoryAdapter` l'attrape via un helper privé partagé par ses 4 méthodes et relance
   `new TechnicalException(OrderPersistenceErrorCode.ORDER_PERSISTENCE_FAILURE, "...", cause)`.
3. `GlobalExceptionHandler.handleTechnical` l'intercepte : log `ERROR` avec le code incident et la
   cause complète (pour le support/l'observabilité), renvoie un `ProblemDetail` 500 **générique**
   au client — jamais le détail brut de l'exception technique.

### Un vrai bug (ex. NullPointerException accidentelle)

Ne passe par aucune des étapes ci-dessus : il n'est jamais volontairement levé, donc jamais
enveloppé. Il tombe directement dans `@ExceptionHandler(Exception.class)`, qui logue "Unexpected
bug" et renvoie un 500 générique — chemin totalement distinct de `TechnicalException`, pour ne
jamais polluer une logique de retry/alerting qui se fierait à ce type.

### Comment ajouter un nouveau cas d'erreur

1. Identifier la catégorie : une règle métier violée (fonctionnel) ou une panne d'infra connue
   (technique) ?
2. Ajouter la valeur dans l'enum du **module qui la lance** (créer un nouvel enum si c'est un
   nouveau module d'adapter qui n'en a pas encore). Ne jamais ajouter une valeur "au cas où" dans
   l'enum d'un autre module.
3. Lancer `new FunctionalException(MonErrorCode.MA_VALEUR, message[, cause])` ou l'équivalent
   `TechnicalException`.
4. Si le cas est fonctionnel et exposé via l'API : ajouter le nouveau cas dans le switch exhaustif
   de `GlobalExceptionHandler` — le compilateur refusera de compiler tant que ce n'est pas fait, il
   n'y a rien à retenir manuellement.
5. Ne jamais créer de nouvelle classe d'exception : le point 2 est toujours la bonne réponse.

### Alternatives envisagées et écartées

Discutées avant d'écrire la moindre ligne de code, pour garder trace du "pourquoi" :

- **Checked exceptions** — rejeté : boilerplate, incompatible avec les lambdas/streams, fragile en
  versioning (ajouter un `throws` casse tous les appelants). Spring lui-même évite ce choix.
- **Une exception par cas d'usage** (`OrderNotFoundException`, etc.) — rejeté : explosion de
  petites classes difficiles à naviguer et à documenter à l'échelle d'une vraie application.
- **Result/Either fonctionnel** ou **sealed interfaces + pattern matching exhaustif** — solutions
  valides et plus "type-safe" à la capture, mais écartées pour privilégier un mécanisme unchecked
  classique, plus proche des habitudes Spring/Java d'entreprise ; le switch exhaustif sur
  `ErrorCode` dans `GlobalExceptionHandler` récupère une partie de la garantie d'exhaustivité sans
  ce changement de paradigme.
- **`Optional<T>` comme porteur d'erreur** — rejeté : `Optional` documente une absence de valeur,
  pas une erreur ; les deux ne doivent pas être confondus.
- **Un enum de code applicatif unique et partagé** — rejeté : "god enum" modifié par toutes les
  équipes/modules en parallèle, conflits Git permanents, couplage entre modules qui n'ont rien à
  voir.
- **Un enum partagé par bounded context** (ex. un seul `OrderErrorCode` couvrant à la fois le
  fonctionnel d'`order-core` et le technique des adapters) — première version de ce squelette,
  corrigée : elle faisait fuiter des préoccupations d'infrastructure (panne DB, panne Kafka) dans
  l'enum du domaine, qui ne les lance pourtant jamais lui-même.
- **Envelopper les bugs dans `TechnicalException(UNKNOWN)`** — rejeté : rendrait un bug de code
  indiscernable d'une vraie panne transitoire pour toute logique de retry/alerting branchée sur
  `TechnicalException`.
- **Le `HttpStatus` porté directement par l'enum `ErrorCode`** — rejeté : `HttpStatus` est un type
  Spring, l'embarquer dans l'enum ferait fuiter Spring dans `order-core`, qui doit rester
  framework-agnostic.

## Tester la gestion d'erreur

Deux mécanismes de test complémentaires, chacun avec un rôle précis : l'un prouve le comportement
HTTP réel, l'autre empêche mécaniquement de contourner les règles ci-dessus.

### Tests WebMvc (`OrderControllerTest`) — le comportement HTTP réel

Le switch exhaustif de `GlobalExceptionHandler` garantit que tous les cas sont couverts, mais pas
qu'ils le sont *correctement* (rien n'empêche une faute de frappe du style
`ORDER_ALREADY_CANCELLED -> HttpStatus.BAD_REQUEST` au lieu de `CONFLICT`). `@WebMvcTest` comble ce
trou en démarrant une vraie tranche web Spring.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetOrderUseCase getOrderUseCase;
    // ... les 4 autres use cases, aussi en @MockitoBean

    @Test
    void should_return404WithErrorCode_when_orderNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(getOrderUseCase.getOrder(orderId))
                .thenThrow(new FunctionalException(OrderErrorCode.ORDER_NOT_FOUND, "Order not found: " + orderId));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
```

`@WebMvcTest(OrderController.class)` charge la tranche web réelle : le contrôleur cité, plus tout ce
qui est annoté `@RestController`/`@RestControllerAdvice` — donc `GlobalExceptionHandler` est chargé
**pour de vrai, pas mocké** (sinon le test ne prouverait rien sur le vrai mapping). Seules les
dépendances hors couche web — les 5 use cases d'`order-core`, injectés dans le contrôleur — sont
remplacées par des `@MockitoBean`, parce que `@WebMvcTest` ne les instancie jamais.

Point d'attention pratique : `order-api` est un module bibliothèque, sans `@SpringBootApplication` à
lui (celle-ci vit dans `order-bootstrap`). `@WebMvcTest` a besoin d'en trouver une en remontant les
packages depuis le test — d'où une petite classe `TestApplication` dédiée, présente uniquement dans
`src/test` d'`order-api` (jamais livrée dans le jar de production).

### Tests d'architecture (`ArchitectureRulesTest`, ArchUnit) — les règles jamais contournées

Les conventions décrites plus haut ("un enum par module", "chaque adapter traduit ses propres
erreurs techniques", "pas d'exception par cas d'usage"...) ne sont que du texte tant que rien ne les
fait respecter. [ArchUnit](https://www.archunit.org/) lit les `.class` compilés et vérifie des
règles de dépendance entre packages/classes, comme un test JUnit normal — si la règle est violée, le
test échoue avec le détail de la dépendance fautive.

```java
class ArchitectureRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.archi");

    @Test
    void orderCoreMustNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.archi.ordermanagement.core..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");

        rule.check(CLASSES);
    }
}
```

Ce test vit dans `order-bootstrap` : c'est le seul module qui a tous les autres (et `error-kernel`)
sur son classpath de test, puisque c'est son rôle d'assembler l'application entière.

| Règle | Ce qu'elle empêche |
|---|---|
| `orderCoreMustNotDependOnSpring` | Un import Spring qui se glisse dans `order-core`. |
| `dataAccessExceptionMustStayConfinedToPersistenceAdapter` | `DataAccessException` capturée ailleurs qu'à sa frontière (`order-persistence`). |
| `kafkaExceptionMustStayConfinedToMessagingAdapter` | Idem pour `KafkaException` et `order-messaging-kafka`. |
| `onlyOneCentralizedExceptionHandlerMustExist` | Un second `@RestControllerAdvice` qui disperserait la gestion d'erreur. |
| `onlyErrorKernelMayDefineNewExceptionTypes` | Un retour en arrière vers "une exception par cas d'usage" (`class XyzException extends RuntimeException` en dehors d'`error-kernel`). |
| `apiMustNotDependOnPersistenceOrMessaging` | `order-api` qui utiliserait directement `OrderEntity`/`OrderJpaRepository` au lieu de passer par un port. |
| `persistenceMustNotDependOnMessaging` | Un adapter qui dépendrait directement d'un autre adapter. |
| `messagingMustNotDependOnPersistence` | Idem, sens inverse. |

Les 4 dernières règles répondent directement à une faiblesse identifiée en construisant ce projet :
avant elles, rien n'empêchait mécaniquement "n'importe qui" de réutiliser une classe d'un autre
module (ex. `order-api` import direct d'`OrderEntity`) ou de recréer le pattern "exception par cas"
qu'on a explicitement écarté — seule la documentation le disait.

## Build / vérification

```bash
mvn -q -pl error-kernel,order-core,order-api,order-persistence,order-messaging-kafka,order-bootstrap -am compile
mvn -q -pl error-kernel,order-core,order-api,order-bootstrap test
```

Aucune base de données ni broker Kafka réel n'est nécessaire pour compiler et faire passer les tests
de ce squelette : JUnit 5 + Mockito (`error-kernel`, `order-core`), `@WebMvcTest`/MockMvc sans
serveur réel (`order-api`), ArchUnit sur les `.class` compilés (`order-bootstrap`). Pas de Docker.

## Notes de version

Basé sur Spring Boot 4.1.0 (Spring Framework 7.0) / Java 21. À vérifier au moment du build :

- Le patch exact `4.1.x` disponible sur Maven Central / start.spring.io.
- `spring-boot-starter-web` est déprécié au profit de `spring-boot-starter-webmvc` (utilisé ici).
- Flyway nécessite désormais le starter `spring-boot-starter-flyway` en plus de
  `flyway-database-postgresql` (le seul `flyway-core` ne suffit plus à l'auto-configuration).
- Le starter Kafka a été renommé `spring-boot-starter-kafka`.

## Prochaine étape

I18n des messages d'erreur, politique de retry/DLQ côté consumer Kafka (aucun consumer n'existe
encore ici), logging structuré (MDC) corrélé au code incident, tests d'intégration (`@WebMvcTest`,
Testcontainers).
