package com.pulserelay.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for PulseRelay.
 * Configures the consumer to receive notification events from Kafka topics.
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:pulserelay-consumer-group}")
    private String groupId;

    /**
     * Configure the Kafka consumer factory.
     * 
     * Key settings:
     * - group-id: Consumer group for this application instance
     * - auto-offset-reset: Start from earliest message if no offset found
     * - value-deserializer: JSON deserializer for event payloads wrapped with error handling
     * - max-poll-records: Batch size for processing
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Consumer behavior
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit

        // ErrorHandlingDeserializer wraps the actual deserializer
        configProps.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        
        // JSON deserializer config
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        log.info("Kafka consumer configured with group: {}, bootstrap servers: {}", groupId, bootstrapServers);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    /**
     * Configure the Kafka listener container factory.
     * 
     * Key settings:
     * - AckMode.MANUAL: Acknowledge messages after successful processing
     * - Prevents message loss if consumer crashes
     * - Allows implementing idempotent processing
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // Skip messages that fail to deserialize (corrupted/old messages)
        factory.setCommonErrorHandler(
            new org.springframework.kafka.listener.DefaultErrorHandler((record, ex) -> {
                log.warn("Skipping message from partition {} at offset {} due to deserialization error: {}",
                    record.partition(), record.offset(), ex.getMessage());
            })
        );
        
        factory.setConcurrency(1); // Single threaded for predictable ordering
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
