package com.pulserelay.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for PulseRelay.
 * Configures the producer to send notification events to Kafka topics.
 * 
 * Jackson Dependency Note:
 * Spring Kafka's JsonSerializer requires Jackson Databind. This project uses
 * com.fasterxml.jackson.core:jackson-databind for JSON serialization of Kafka messages.
 * The explicit dependency ensures compatibility between Kafka and Jackson even when
 * Spring Boot's dependency management uses different Jackson packages.
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.acks:all}")
    private String acks;

    @Value("${spring.kafka.producer.retries:3}")
    private Integer retries;

    @Value("${spring.kafka.producer.linger-ms:10}")
    private Integer lingerMs;

    /**
     * Configure the Kafka producer factory.
     * 
     * Key settings:
     * - acks=all: Wait for all in-sync replicas to acknowledge (safer but slower)
     * - retries: Automatically retry failed sends
     * - linger.ms: Wait before sending batch (improves throughput)
     * - compression.type: Compress messages for bandwidth efficiency
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability settings
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);

        // Performance settings
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Idempotent producer (prevents duplicate messages)
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        log.info("Kafka producer configured with bootstrap servers: {}, acks: {}, retries: {}", 
            bootstrapServers, acks, retries);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka template for sending messages.
     * Provides a high-level API for sending messages to topics.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
