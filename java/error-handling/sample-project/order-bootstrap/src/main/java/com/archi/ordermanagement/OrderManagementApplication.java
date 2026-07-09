package com.archi.ordermanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Kept at the project's root package (not a ".bootstrap" sub-package) so Spring Boot's default
 * component scan reaches the sibling adapter packages (.api, .persistence, .messaging.kafka).
 * order-core itself has no annotations to scan: it is wired manually, see CoreBeansConfig.
 */
@SpringBootApplication
public class OrderManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderManagementApplication.class, args);
    }
}
