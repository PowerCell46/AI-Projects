package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.InvalidNotificationEventException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.DltReplayService;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code topic-news.notification-requested-dlt} under its own consumer group, bounded by an
 * offset snapshot taken right after partition assignment - records that dead-letter again while the run
 * is going land after the snapshot and are left for the next run. Permanent failures (headers, not a
 * re-deserialize) are skipped by default; committing to the snapshot happens once at the end, so a
 * crash mid-run simply re-processes the same window on the next invocation rather than losing it.
 */
@Slf4j
@Service
public class DltReplayServiceImpl implements DltReplayService {

    private static final Set<String> PERMANENT_FAILURE_EXCEPTION_FQCNS = Set.of(
            InvalidNotificationEventException.class.getName(),
            PermanentMailDeliveryException.class.getName(),
            DeserializationException.class.getName());

    private static final String DLT_HEADER_PREFIX = KafkaHeaders.PREFIX + "dlt-";

    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);

    private final String groupId;

    private final String dltTopicName;

    private final boolean includePermanent;

    private final KafkaProperties kafkaProperties;

    private final String notificationRequestedTopicName;

    private final KafkaConnectionDetails connectionDetails;

    public DltReplayServiceImpl(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails,
            @Value("${app.kafka.notification-requested.dlt-name}") String dltTopicName,
            @Value("${app.kafka.notification-requested.name}") String notificationRequestedTopicName,
            @Value("${app.dlt-replay.group-id}") String groupId,
            @Value("${app.dlt-replay.include-permanent}") boolean includePermanent
    ) {
        this.kafkaProperties = kafkaProperties;
        this.connectionDetails = connectionDetails;
        this.dltTopicName = dltTopicName;
        this.notificationRequestedTopicName = notificationRequestedTopicName;
        this.groupId = groupId;
        this.includePermanent = includePermanent;
    }

    @Override
    public void replay() {
        try (Consumer<String, byte[]> consumer = dltConsumer();
             Producer<String, byte[]> producer = replayProducer()) {

            replay(consumer, producer);
        }
    }

    /**
     * The actual replay logic, taking an already-built consumer and producer - split out from
     * {@link #replay()} so the offset-bounding and header-classification rules are directly unit-testable
     * against a mocked {@link Consumer}/{@link Producer}, without a real broker.
     */
    void replay(Consumer<String, byte[]> consumer, Producer<String, byte[]> producer) {
        List<TopicPartition> partitions = assignPartitions(consumer);
        Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
        Set<TopicPartition> exhaustedPartitions = pauseCaughtUpPartitions(consumer, partitions, endOffsets);

        ReplayCounts counts = drain(consumer, producer, endOffsets, exhaustedPartitions);

        consumer.commitSync(snapshotCommitOffsets(endOffsets));
        log.info("DLT replay finished: {} replayed, {} skipped, {} total.",
                counts.replayed, counts.skipped, counts.total());
    }

    private List<TopicPartition> assignPartitions(Consumer<String, byte[]> consumer) {
        List<TopicPartition> partitions = consumer.partitionsFor(dltTopicName).stream()
                .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
                .toList();
        consumer.assign(partitions);

        return partitions;
    }

    private Set<TopicPartition> pauseCaughtUpPartitions(
            Consumer<String, byte[]> consumer, List<TopicPartition> partitions, Map<TopicPartition, Long> endOffsets
    ) {
        Set<TopicPartition> exhaustedPartitions = new HashSet<>();
        for (TopicPartition partition : partitions) {
            if (consumer.position(partition) >= endOffsets.get(partition)) {
                consumer.pause(List.of(partition));
                exhaustedPartitions.add(partition);
            }
        }

        return exhaustedPartitions;
    }

    private ReplayCounts drain(
            Consumer<String, byte[]> consumer, Producer<String, byte[]> producer,
            Map<TopicPartition, Long> endOffsets, Set<TopicPartition> exhaustedPartitions
    ) {
        ReplayCounts counts = new ReplayCounts();

        while (exhaustedPartitions.size() < endOffsets.size()) {
            for (ConsumerRecord<String, byte[]> record : consumer.poll(POLL_TIMEOUT)) {
                processIfWithinSnapshot(consumer, producer, endOffsets, exhaustedPartitions, counts, record);
            }
        }

        return counts;
    }

    private void processIfWithinSnapshot(
            Consumer<String, byte[]> consumer, Producer<String, byte[]> producer,
            Map<TopicPartition, Long> endOffsets, Set<TopicPartition> exhaustedPartitions,
            ReplayCounts counts, ConsumerRecord<String, byte[]> record
    ) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());
        long endOffset = endOffsets.get(partition);
        if (record.offset() >= endOffset) {
            return;
        }

        if (shouldSkip(record)) {
            counts.skipped++;

        } else {
            republish(producer, record);
            counts.replayed++;
        }

        if (record.offset() == endOffset - 1) {
            consumer.pause(List.of(partition));
            exhaustedPartitions.add(partition);
        }
    }

    private boolean shouldSkip(ConsumerRecord<String, byte[]> record) {
        if (includePermanent) {
            return false;
        }

        Header exceptionCauseHeader = record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN);
        if (exceptionCauseHeader == null) {
            return false;
        }

        String exceptionCauseFqcn = new String(exceptionCauseHeader.value(), StandardCharsets.UTF_8);
        return PERMANENT_FAILURE_EXCEPTION_FQCNS.contains(exceptionCauseFqcn);
    }

    private void republish(Producer<String, byte[]> producer, ConsumerRecord<String, byte[]> record) {
        List<Header> headers = StreamSupport.stream(record.headers().spliterator(), false)
                .filter(header -> !header.key().startsWith(DLT_HEADER_PREFIX))
                .toList();
        ProducerRecord<String, byte[]> outgoing = new ProducerRecord<>(
                notificationRequestedTopicName, null, record.key(), record.value(), headers);

        try {
            producer.send(outgoing).get(10, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);

        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<TopicPartition, OffsetAndMetadata> snapshotCommitOffsets(Map<TopicPartition, Long> endOffsets) {
        return endOffsets.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> new OffsetAndMetadata(entry.getValue())));
    }

    private Consumer<String, byte[]> dltConsumer() {
        Map<String, Object> consumerProperties = kafkaProperties.buildConsumerProperties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumer().getBootstrapServers());
        consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return new KafkaConsumer<>(consumerProperties, new StringDeserializer(), new ByteArrayDeserializer());
    }

    private Producer<String, byte[]> replayProducer() {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducer().getBootstrapServers());

        return new KafkaProducer<>(producerProperties, new StringSerializer(), new ByteArraySerializer());
    }

    private static final class ReplayCounts {

        private int replayed;

        private int skipped;

        private int total() {
            return replayed + skipped;
        }
    }
}
