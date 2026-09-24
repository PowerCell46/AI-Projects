package com.peter_gerdzhikov.signal_flow_mail_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Adds a Mailpit container next to the inherited Kafka and Redis, started once per JVM. Mailpit has no
 * Spring Boot service-connection support, so {@code spring.mail.*} is wired manually via
 * {@link DynamicPropertySource} instead of {@code @ServiceConnection}.
 *
 * <p>Started with {@code --smtp-allowed-recipients} restricted to {@code @example.com} - every test
 * identity in this suite is a {@code <uuid>@example.com} address (see the reliability rules), so this
 * also doubles as the fixture for the permanent-SMTP-rejection scenario: a recipient outside that
 * domain gets a real {@code 550} from Mailpit itself, verified against the live container rather than
 * assumed.
 */
public abstract class AbstractMailpitIntegrationTest extends AbstractKafkaE2ETestSupport {

    private static final int SMTP_PORT = 1025;

    private static final int HTTP_PORT = 8025;

    private static final GenericContainer<?> MAILPIT = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.31.2"))
            .withExposedPorts(SMTP_PORT, HTTP_PORT)
            .withCommand("--smtp-allowed-recipients", ".*@example\\.com$");

    protected static final MailpitClient MAILPIT_CLIENT;

    static {
        MAILPIT.start();
        MAILPIT_CLIENT = new MailpitClient("http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(HTTP_PORT));
    }

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(SMTP_PORT));
        registry.add("spring.mail.properties.mail.smtp.auth", () -> false);
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> false);
    }
}
