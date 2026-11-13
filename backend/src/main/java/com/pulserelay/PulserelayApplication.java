package com.pulserelay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PulseRelay Spring Boot Application Entry Point
 * 
 * @EnableScheduling: Activates the Spring scheduler for @Scheduled tasks
 * Required for OutboxPublisher.publishPendingEvents() to execute periodically
 */
@SpringBootApplication
@EnableScheduling
public class PulserelayApplication {

    public static void main(String[] args) {
        SpringApplication.run(PulserelayApplication.class, args);
    }
}