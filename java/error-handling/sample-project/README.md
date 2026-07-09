# order-management sample project

Squelette Maven multi-module en architecture hexagonale (ports & adapters), servant de projet
support pour construire et illustrer une gestion d'erreur/exception Java/Spring réutilisable
(dossier `java/error-handling`).

**Ce projet n'est volontairement pas une application complète.** Les interfaces, classes et le
câblage Spring sont posés au minimum nécessaire pour compiler et verrouiller la structure ;
d'autres approfondissements (i18n des messages, retry/DLQ Kafka, tests d'intégration) restent une
itération future.

## Modules

| Module                  | Rôle                                                             |
|--------------------------|-------------------------------------------------------------------|
| `error-kernel`            | Socle de gestion d'erreur générique et réutilisable : `ErrorCode`, `ApplicationException`, `FunctionalException`, `TechnicalException`. Zéro dépendance Spring, zéro référence au domaine "order". |
| `order-core`             | Cœur applicatif : domaine, ports (in/out), use cases, `OrderErrorCode`. Zéro dépendance Spring. |
| `order-api`               | Adapter "in" : API REST (Spring MVC), DTOs, `@RestControllerAdvice`. |
| `order-persistence`       | Adapter "out" : persistance JPA/PostgreSQL + migrations Flyway.    |
| `order-messaging-kafka`   | Adapter "out" : publication d'événements Kafka (notification de création de commande). |
| `order-bootstrap`         | Application Spring Boot exécutable : câblage des beans, configuration. |

`order-core` ne dépend d'aucune brique Spring : le bean `OrderService` est instancié
explicitement dans `order-bootstrap` (`config/CoreBeansConfig`) plutôt que découvert par
component-scan, pour garder le métier framework-agnostic. `error-kernel` suit le même principe et
n'a même pas de référence au domaine "order" : il est pensé pour être réutilisé tel quel dans
d'autres projets.

### Gestion d'erreur

Pas d'exception par cas d'usage : deux classes concrètes seulement, `FunctionalException` (erreur
métier attendue) et `TechnicalException` (panne infra connue, potentiellement retryable), chacune
portant un `ErrorCode`. Un enum par module Maven, pas un enum géant partagé : `OrderErrorCode`
(fonctionnel, dans `order-core`) ne contient que ce qu'`order-core` lance lui-même ;
`order-persistence` et `order-messaging-kafka` ont chacun leur propre enum technique
(`OrderPersistenceErrorCode`, `OrderMessagingErrorCode`) — chaque adapter possède son propre
vocabulaire d'incident, `order-core` reste 100% métier. Un vrai bug de code (NPE...) ne passe
jamais par cette hiérarchie — il est capté par un handler générique séparé dans
`GlobalExceptionHandler`, pour ne pas fausser une éventuelle politique de retry/alerting branchée
sur `TechnicalException`. Chaque adapter technique traduit ses propres exceptions d'infra
(`DataAccessException`, `KafkaException`) en `TechnicalException` à sa frontière.

## Build / vérification

```bash
mvn -q -pl error-kernel,order-core,order-api,order-persistence,order-messaging-kafka,order-bootstrap -am compile
mvn -q -pl error-kernel,order-core test
```

Aucune base de données, broker Kafka ou Docker n'est nécessaire pour compiler et faire passer les
tests de ce squelette (JUnit 5 + Mockito, sans contexte Spring).

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
