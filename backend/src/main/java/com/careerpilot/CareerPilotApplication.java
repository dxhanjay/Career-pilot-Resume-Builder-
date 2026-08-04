package com.careerpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Application entry point for the CareerPilot AI backend.
 *
 * <p>{@code @SpringBootApplication} is three annotations in one:
 * <ul>
 *   <li>{@code @Configuration} — this class may declare beans</li>
 *   <li>{@code @EnableAutoConfiguration} — Spring Boot configures what it finds
 *       on the classpath (Tomcat, Hibernate, Flyway, …)</li>
 *   <li>{@code @ComponentScan} — components are discovered from this package
 *       downward, which is why this class sits at the package root. Moving it
 *       into a subpackage silently stops half the application being scanned.</li>
 * </ul>
 *
 * <p>{@code @ConfigurationPropertiesScan} finds every
 * {@code @ConfigurationProperties} type without each one needing an explicit
 * {@code @EnableConfigurationProperties} registration. Typed configuration is
 * preferred over scattered {@code @Value} strings: a typo in a property name
 * fails at startup with a clear message rather than injecting {@code null} into
 * something that breaks under load three days later.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CareerPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerPilotApplication.class, args);
    }
}
