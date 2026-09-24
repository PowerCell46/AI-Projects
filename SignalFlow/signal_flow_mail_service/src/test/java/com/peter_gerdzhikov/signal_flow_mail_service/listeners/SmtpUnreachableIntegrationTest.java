package com.peter_gerdzhikov.signal_flow_mail_service.listeners;

import java.time.Duration;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;

import org.awaitility.Awaitility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.support.AbstractKafkaE2ETestSupport;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Its own Spring context - a different topic, group and (broken) SMTP port from every other e2e test, so
 * it never shares a consumer group with the main suite (reliability rule 5) and its own
 * {@code spring.mail.port} override isn't shadowed by the shared base's Mailpit
 * {@code @DynamicPropertySource} (which takes precedence over {@code @TestPropertySource} - avoided here
 * by not extending that base at all).
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "app.kafka.notification-requested.name=smtp-unreachable-test.topic-news.notification-requested",
        "app.kafka.notification-requested.dlt-name=smtp-unreachable-test.topic-news.notification-requested-dlt",
        "spring.kafka.consumer.group-id=smtp-unreachable-test-group",
        "spring.mail.host=localhost",
        "spring.mail.port=1",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false"
})
class SmtpUnreachableIntegrationTest extends AbstractKafkaE2ETestSupport {

    private static final String TOPIC = "smtp-unreachable-test.topic-news.notification-requested";

    private static final String DLT_TOPIC = "smtp-unreachable-test.topic-news.notification-requested-dlt";

    @MockitoSpyBean
    private JavaMailSender javaMailSender;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void should_dead_letter_after_exhausting_retries_when_smtp_is_unreachable() {
        TopicNewsNotificationEventDTO event = aValidEvent(uniqueRecipient());
        String key = event.getUserId().toString();

        publish(TOPIC, key, toJson(event));

        awaitDltRecordForKey(DLT_TOPIC, key);
        // Test backoff is 3 retries (application-test.properties): 1 initial attempt + 3 retries = 4.
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> verify(javaMailSender, times(4)).send(any(MimeMessage.class)));
        assertNull(redisTemplate.opsForValue().get(redisKey(event.getNewsId(), event.getUserId())));
    }
}
