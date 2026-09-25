package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import tools.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.test.context.TestPropertySource;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.InvalidNotificationEventException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.TransientMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.DltReplayService;
import com.peter_gerdzhikov.signal_flow_mail_service.support.AbstractNotificationE2ETest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Its own topic names and consumer groups - a shared DLT topic would let this test's replay runs sweep up
 * unrelated dead-lettered records from {@code TopicNewsNotificationRequestedListenerIntegrationTest}
 * (which shares the same cached context otherwise). {@code publishToDlt} crafts a record directly on the
 * DLT topic with the classification header a real failure would have left, so each scenario is
 * deterministic without re-triggering that failure through the whole pipeline. See {@code TESTING.md} for
 * the scenario catalog.
 */
@TestPropertySource(properties = {
        "app.kafka.notification-requested.name=dlt-replay-test.topic-news.notification-requested",
        "app.kafka.notification-requested.dlt-name=dlt-replay-test.topic-news.notification-requested-dlt",
        "spring.kafka.consumer.group-id=dlt-replay-test-group",
        "app.dlt-replay.group-id=dlt-replay-test-replay-group"
})
class DltReplayIntegrationTest extends AbstractNotificationE2ETest {

    private static final String TOPIC = "dlt-replay-test.topic-news.notification-requested";

    private static final String DLT_TOPIC = "dlt-replay-test.topic-news.notification-requested-dlt";

    @Autowired
    private DltReplayService dltReplayService;

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private KafkaConnectionDetails connectionDetails;

    @Test
    void should_send_an_email_after_replaying_a_transient_failure_record() {
        String recipient = uniqueRecipient();
        TopicNewsNotificationEventDTO event = aValidEvent(recipient);
        publishToDlt(DLT_TOPIC, event.getUserId().toString(), toJson(event), TransientMailDeliveryException.class.getName());

        dltReplayService.replay();

        awaitEmail(recipient);
    }

    @Test
    void should_not_send_an_email_when_replaying_an_invalid_event_record_with_default_settings() {
        String recipient = uniqueRecipient();
        TopicNewsNotificationEventDTO event = aValidEvent(recipient);
        publishToDlt(DLT_TOPIC, event.getUserId().toString(), toJson(event), InvalidNotificationEventException.class.getName());

        dltReplayService.replay();

        assertNoEmail(recipient);
    }

    @Test
    void should_send_an_email_when_replaying_a_permanent_failure_record_with_include_permanent_true() {
        String recipient = uniqueRecipient();
        TopicNewsNotificationEventDTO event = aValidEvent(recipient);
        publishToDlt(DLT_TOPIC, event.getUserId().toString(), toJson(event), PermanentMailDeliveryException.class.getName());
        DltReplayService includePermanentReplay = new DltReplayServiceImpl(
                kafkaProperties, connectionDetails, DLT_TOPIC, TOPIC, "dlt-replay-test-replay-group", true);

        includePermanentReplay.replay();

        awaitEmail(recipient);
    }

    @Test
    void should_not_send_a_second_email_for_a_record_whose_key_is_already_sent() {
        String recipient = uniqueRecipient();
        TopicNewsNotificationEventDTO event = aValidEvent(recipient);
        String key = event.getUserId().toString();
        publish(TOPIC, key, toJson(event));
        awaitEmail(recipient);

        publishToDlt(DLT_TOPIC, key, toJson(event), TransientMailDeliveryException.class.getName());
        dltReplayService.replay();

        String sentinelRecipient = uniqueRecipient();
        publish(TOPIC, key, toJson(aValidEvent(sentinelRecipient)));
        awaitEmail(sentinelRecipient);
        JsonNode messages = MAILPIT_CLIENT.searchByRecipient(recipient).get("messages");
        assertEquals(1, messages.size(), "Expected exactly one email to " + recipient);
    }
}
