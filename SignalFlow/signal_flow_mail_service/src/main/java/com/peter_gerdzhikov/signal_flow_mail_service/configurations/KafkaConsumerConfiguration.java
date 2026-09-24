package com.peter_gerdzhikov.signal_flow_mail_service.configurations;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;

/**
 * Concretely-typed factory for {@code topic-news.notification-requested}: the producer sends no type
 * header, so a properties-only {@code spring.kafka.consumer.*} config can't infer the target DTO type.
 */
@Configuration
public class KafkaConsumerConfiguration {

    /**
     * {@code buildConsumerProperties()} only carries the static bootstrap-servers value; a test's
     * {@code @ServiceConnection} container overrides it via {@link KafkaConnectionDetails} instead.
     */
    @Bean
    public ConsumerFactory<String, TopicNewsNotificationEventDTO> notificationRequestedConsumerFactory(
            KafkaProperties kafkaProperties,
            KafkaConnectionDetails connectionDetails
    ) {
        Map<String, Object> consumerProperties = kafkaProperties.buildConsumerProperties();
        consumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getConsumer().getBootstrapServers());

        return new DefaultKafkaConsumerFactory<>(
                consumerProperties,
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(new JacksonJsonDeserializer<>(TopicNewsNotificationEventDTO.class).ignoreTypeHeaders())
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TopicNewsNotificationEventDTO> notificationRequestedListenerContainerFactory(
            @Value("${spring.kafka.listener.concurrency}") int concurrency,
            ConsumerFactory<String, TopicNewsNotificationEventDTO> notificationRequestedConsumerFactory,
            CommonErrorHandler notificationRequestedErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, TopicNewsNotificationEventDTO> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(notificationRequestedConsumerFactory);
        factory.setCommonErrorHandler(notificationRequestedErrorHandler);
        factory.setConcurrency(concurrency);
        return factory;
    }
}
