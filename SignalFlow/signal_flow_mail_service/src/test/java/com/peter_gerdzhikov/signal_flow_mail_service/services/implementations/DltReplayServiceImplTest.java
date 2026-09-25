package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.support.KafkaHeaders;

import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.InvalidNotificationEventException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.TransientMailDeliveryException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DltReplayServiceImplTest {

    private static final String DLT_TOPIC = "topic-news.notification-requested-dlt";

    private static final String TARGET_TOPIC = "topic-news.notification-requested";

    private static final String GROUP_ID = "signal-flow-mail-service-dlt-replay";

    private static final TopicPartition PARTITION = new TopicPartition(DLT_TOPIC, 0);

    @Mock
    private KafkaProperties kafkaProperties;

    @Mock
    private KafkaConnectionDetails connectionDetails;

    @Mock
    private Consumer<String, byte[]> consumer;

    @Mock
    private Producer<String, byte[]> producer;

    @Test
    void should_republish_a_non_permanent_failure_record_by_default() {
        DltReplayServiceImpl service = service(false);
        stubSinglePartition(0L, 1L, oneRecordBatch(0, headers(TransientMailDeliveryException.class.getName())));
        stubProducerSend();

        service.replay(consumer, producer);

        verify(producer).send(any());
    }

    @Test
    void should_skip_an_invalid_event_record_by_default() {
        DltReplayServiceImpl service = service(false);
        stubSinglePartition(0L, 1L, oneRecordBatch(0, headers(InvalidNotificationEventException.class.getName())));

        service.replay(consumer, producer);

        verify(producer, never()).send(any());
    }

    @Test
    void should_skip_a_permanent_mail_delivery_failure_record_by_default() {
        DltReplayServiceImpl service = service(false);
        stubSinglePartition(0L, 1L, oneRecordBatch(0, headers(PermanentMailDeliveryException.class.getName())));

        service.replay(consumer, producer);

        verify(producer, never()).send(any());
    }

    @Test
    void should_skip_a_deserialization_failure_record_by_default() {
        DltReplayServiceImpl service = service(false);
        stubSinglePartition(0L, 1L, oneRecordBatch(0, headers("org.springframework.kafka.support.serializer.DeserializationException")));

        service.replay(consumer, producer);

        verify(producer, never()).send(any());
    }

    @Test
    void should_replay_a_permanent_failure_record_when_include_permanent_is_true() {
        DltReplayServiceImpl service = service(true);
        stubSinglePartition(0L, 1L, oneRecordBatch(0, headers(InvalidNotificationEventException.class.getName())));
        stubProducerSend();

        service.replay(consumer, producer);

        verify(producer).send(any());
    }

    @Test
    void should_not_process_a_record_at_or_beyond_the_snapshot_end_offset() {
        DltReplayServiceImpl service = service(false);
        ConsumerRecord<String, byte[]> withinSnapshot = record(0, new RecordHeaders());
        ConsumerRecord<String, byte[]> afterSnapshot = record(1, new RecordHeaders());
        stubSinglePartition(0L, 1L, new ConsumerRecords<>(Map.of(PARTITION, List.of(withinSnapshot, afterSnapshot))));
        stubProducerSend();

        service.replay(consumer, producer);

        verify(producer, times(1)).send(any());
        verify(consumer).commitSync(Map.of(PARTITION, new OffsetAndMetadata(1L)));
    }

    @Test
    void should_do_nothing_when_the_partition_is_already_caught_up() {
        DltReplayServiceImpl service = service(false);
        when(consumer.partitionsFor(DLT_TOPIC)).thenReturn(List.of(partitionInfo()));
        when(consumer.endOffsets(List.of(PARTITION))).thenReturn(Map.of(PARTITION, 0L));
        when(consumer.position(PARTITION)).thenReturn(0L);

        service.replay(consumer, producer);

        verify(consumer, never()).poll(any());
        verify(producer, never()).send(any());
    }

    @Test
    void should_strip_kafka_dlt_headers_but_keep_other_headers_when_republishing() {
        DltReplayServiceImpl service = service(false);
        Headers originalHeaders = new RecordHeaders();
        originalHeaders.add(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, TransientMailDeliveryException.class.getName().getBytes(StandardCharsets.UTF_8));
        originalHeaders.add(KafkaHeaders.DLT_EXCEPTION_FQCN, "org.springframework.kafka.listener.ListenerExecutionFailedException".getBytes(StandardCharsets.UTF_8));
        originalHeaders.add(KafkaHeaders.DLT_ORIGINAL_TOPIC, TARGET_TOPIC.getBytes(StandardCharsets.UTF_8));
        originalHeaders.add("__TypeId__", "com.example.SomeDto".getBytes(StandardCharsets.UTF_8));
        stubSinglePartition(0L, 1L, oneRecordBatch(0, originalHeaders));
        stubProducerSend();

        service.replay(consumer, producer);

        ArgumentCaptor<ProducerRecord<String, byte[]>> captor = ArgumentCaptor.captor();
        verify(producer).send(captor.capture());
        ProducerRecord<String, byte[]> published = captor.getValue();
        assertEquals(TARGET_TOPIC, published.topic());
        assertEquals("key-1", published.key());
        assertArrayEquals("value-1".getBytes(StandardCharsets.UTF_8), published.value());
        List<String> publishedHeaderNames = headerNames(published.headers());
        assertTrue(publishedHeaderNames.contains("__TypeId__"));
        assertFalse(publishedHeaderNames.stream().anyMatch(name -> name.startsWith(KafkaHeaders.PREFIX + "dlt-")));
    }

    private DltReplayServiceImpl service(boolean includePermanent) {
        return new DltReplayServiceImpl(kafkaProperties, connectionDetails, DLT_TOPIC, TARGET_TOPIC, GROUP_ID, includePermanent);
    }

    private void stubSinglePartition(long committedOffset, long endOffset, ConsumerRecords<String, byte[]> firstPoll) {
        when(consumer.partitionsFor(DLT_TOPIC)).thenReturn(List.of(partitionInfo()));
        when(consumer.endOffsets(List.of(PARTITION))).thenReturn(Map.of(PARTITION, endOffset));
        when(consumer.position(PARTITION)).thenReturn(committedOffset);
        when(consumer.poll(any())).thenReturn(firstPoll, ConsumerRecords.empty());
    }

    private void stubProducerSend() {
        when(producer.send(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    private PartitionInfo partitionInfo() {
        Node leader = new Node(0, "localhost", 9092);
        return new PartitionInfo(DLT_TOPIC, 0, leader, new Node[] {leader}, new Node[] {leader});
    }

    private ConsumerRecords<String, byte[]> oneRecordBatch(long offset, Headers headers) {
        return new ConsumerRecords<>(Map.of(PARTITION, List.of(record(offset, headers))));
    }

    private ConsumerRecord<String, byte[]> record(long offset, Headers headers) {
        return new ConsumerRecord<>(DLT_TOPIC, 0, offset, ConsumerRecord.NO_TIMESTAMP, TimestampType.NO_TIMESTAMP_TYPE,
                ConsumerRecord.NULL_SIZE, ConsumerRecord.NULL_SIZE, "key-1", "value-1".getBytes(StandardCharsets.UTF_8),
                headers, Optional.empty());
    }

    private Headers headers(String exceptionCauseFqcn) {
        Headers headers = new RecordHeaders();
        headers.add(new RecordHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, exceptionCauseFqcn.getBytes(StandardCharsets.UTF_8)));

        return headers;
    }

    private List<String> headerNames(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false)
                .map(Header::key)
                .toList();
    }
}
