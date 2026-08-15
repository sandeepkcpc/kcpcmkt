package com.kcpc.mkt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * KCPC Marketing Content Production Lifecycle MVP — Development Baseline R3.5.
 * Modular monolith: Spring MVC (JSP) and REST controllers share one application/service layer.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class KcpcMktApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(KcpcMktApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(KcpcMktApplication.class, args);
    }
}
