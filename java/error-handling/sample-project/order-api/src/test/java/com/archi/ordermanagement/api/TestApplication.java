package com.archi.ordermanagement.api;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * order-api is a library module with no @SpringBootApplication of its own (that lives in
 * order-bootstrap). @WebMvcTest requires one findable by searching packages upward from the test,
 * so this test-only class (never shipped in the production jar) provides it.
 */
@SpringBootApplication
class TestApplication {
}
