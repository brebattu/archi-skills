package com.archi.ordermanagement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns conventions documented in sample-project/README.md into rules the build actually fails
 * on, instead of relying on reviewers remembering them. Runs here because order-bootstrap is the
 * one module with every other module (and error-kernel) on its classpath.
 */
class ArchitectureRulesTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages("com.archi");

    @Test
    void orderCoreMustNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.archi.ordermanagement.core..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");

        rule.check(CLASSES);
    }

    @Test
    void dataAccessExceptionMustStayConfinedToPersistenceAdapter() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.archi.ordermanagement.persistence..")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.dao.DataAccessException");

        rule.check(CLASSES);
    }

    @Test
    void kafkaExceptionMustStayConfinedToMessagingAdapter() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.archi.ordermanagement.messaging..")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.apache.kafka.common.KafkaException");

        rule.check(CLASSES);
    }

    @Test
    void onlyOneCentralizedExceptionHandlerMustExist() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(RestControllerAdvice.class)
                .should().haveSimpleName("GlobalExceptionHandler");

        rule.check(CLASSES);
    }

    /**
     * The "no exception per use case" rule (README §Gestion d'erreur) was, until this test
     * existed, only a convention: nothing stopped anyone from writing
     * {@code class OrderNotFoundException extends RuntimeException} again anywhere in the
     * codebase — exactly the pattern this project moved away from. This rule turns that from a
     * silent regression into a failing build.
     */
    @Test
    void onlyErrorKernelMayDefineNewExceptionTypes() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackage("com.archi.errorhandling..")
                .should().beAssignableTo(RuntimeException.class);

        rule.check(CLASSES);
    }

    /**
     * Nothing in a Maven pom stops order-api from reaching into order-persistence's OrderEntity
     * or order-messaging-kafka's producer classes directly ("n'importe qui peut utiliser telle ou
     * telle classe") once those become compile dependencies of order-api for some unrelated
     * reason. These three rules keep every adapter talking to order-core only, never to a sibling
     * adapter, independently of what the poms happen to declare today.
     */
    @Test
    void apiMustNotDependOnPersistenceOrMessaging() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.archi.ordermanagement.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.archi.ordermanagement.persistence..",
                        "com.archi.ordermanagement.messaging..");

        rule.check(CLASSES);
    }

    /**
     * The reverse of {@link #apiMustNotDependOnPersistenceOrMessaging}: order-core is allowed to
     * be depended on by every adapter (that's the point of the hexagon), but never the other way
     * round, even though an adapter is allowed to throw a {@code FunctionalException} carrying an
     * order-core {@code OrderErrorCode} directly (README point 6 — e.g.
     * {@code OrderRepositoryAdapter} throwing {@code OrderErrorCode.ORDER_ALREADY_EXISTS}). That
     * pattern only works one-way: order-core must still never import an adapter's own types.
     */
    @Test
    void orderCoreMustNotDependOnPersistenceOrMessaging() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.archi.ordermanagement.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.archi.ordermanagement.persistence..",
                        "com.archi.ordermanagement.messaging..");

        rule.check(CLASSES);
    }

    @Test
    void persistenceMustNotDependOnMessaging() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.archi.ordermanagement.persistence..")
                .should().dependOnClassesThat().resideInAPackage("com.archi.ordermanagement.messaging..");

        rule.check(CLASSES);
    }

    @Test
    void messagingMustNotDependOnPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.archi.ordermanagement.messaging..")
                .should().dependOnClassesThat().resideInAPackage("com.archi.ordermanagement.persistence..");

        rule.check(CLASSES);
    }
}
