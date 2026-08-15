package com.kcpc.mkt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class KcpcMktApplicationTests {

    @Test
    void contextLoads() {
        // Fails the build if the Spring context (security config, JPA mappings, Flyway
        // migrations) cannot start - the cheapest possible regression guard.
    }
}
