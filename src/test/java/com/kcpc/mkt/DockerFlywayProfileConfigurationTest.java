package com.kcpc.mkt;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker/production readiness regression guard: db/migration-demo (8 demo users + 3 demo
 * permission grants) must never become reachable under the docker profile - only ever under dev.
 * The docker profile is deliberately reused unchanged for every non-local-dev deployment target
 * (the local Mac Compose stack, and both the GCP DEV and PROD stacks under deploy/ - see
 * deploy/README.md "Spring profile strategy") rather than introducing separate docker-dev/prod
 * profiles, so this one test is the single guard for all three. Parses application.yml's raw
 * multi-document YAML directly rather than booting a Spring context, since the docker profile's
 * datasource ({@code jdbc:postgresql://postgres:5432/...}) only resolves inside a Docker Compose
 * network and is not reachable from a local Maven test run.
 */
class DockerFlywayProfileConfigurationTest {

    @Test
    void baseProfileFlywayLocationsExcludeDemoMigrations() throws Exception {
        assertThat(flywayLocations(baseDocument()))
                .as("base (default) spring.flyway.locations")
                .isEqualTo("classpath:db/migration");
    }

    @Test
    void dockerProfileNeverIncludesDemoMigrationsWhileDevProfileDoes() throws Exception {
        Map<String, Object> dockerDoc = profileDocument("docker");
        Map<String, Object> devDoc = profileDocument("dev");

        // The docker profile must not override flyway.locations to include the demo folder. Not
        // overriding it at all (inheriting the safe base value) is fine and is what happens today.
        assertThat(flywayLocations(dockerDoc))
                .as("docker profile's spring.flyway.locations override (blank = inherits base)")
                .doesNotContain("db/migration-demo");

        // The dev profile is expected to reference the demo folder - if this ever stops being
        // true, the demo-data assumption documented elsewhere in this codebase is stale.
        assertThat(flywayLocations(devDoc))
                .as("dev profile's spring.flyway.locations override")
                .contains("classpath:db/migration-demo");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> allDocuments() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(in).as("application.yml must be on the test classpath").isNotNull();
            List<Map<String, Object>> documents = new ArrayList<>();
            for (Object doc : new Yaml().loadAll(in)) {
                documents.add((Map<String, Object>) doc);
            }
            return documents;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> baseDocument() throws Exception {
        return allDocuments().stream()
                .filter(doc -> profileNameOf(doc) == null)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No un-profiled (base) document found in application.yml"));
    }

    private Map<String, Object> profileDocument(String profile) throws Exception {
        return allDocuments().stream()
                .filter(doc -> profile.equals(profileNameOf(doc)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No application.yml document found for profile: " + profile));
    }

    @SuppressWarnings("unchecked")
    private String profileNameOf(Map<String, Object> doc) {
        Object spring = doc.get("spring");
        if (!(spring instanceof Map)) {
            return null;
        }
        Object config = ((Map<String, Object>) spring).get("config");
        if (!(config instanceof Map)) {
            return null;
        }
        Object activate = ((Map<String, Object>) config).get("activate");
        if (!(activate instanceof Map)) {
            return null;
        }
        Object onProfile = ((Map<String, Object>) activate).get("on-profile");
        return onProfile == null ? null : onProfile.toString();
    }

    @SuppressWarnings("unchecked")
    private String flywayLocations(Map<String, Object> doc) {
        Object spring = doc.get("spring");
        if (!(spring instanceof Map)) {
            return "";
        }
        Object flyway = ((Map<String, Object>) spring).get("flyway");
        if (!(flyway instanceof Map)) {
            return ""; // profile doesn't override flyway.locations at all -> inherits base
        }
        Object locations = ((Map<String, Object>) flyway).get("locations");
        return locations == null ? "" : locations.toString();
    }
}
