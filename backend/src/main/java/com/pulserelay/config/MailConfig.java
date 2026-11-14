package com.pulserelay.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Mail Configuration - Phase 09
 * Configures JavaMailSender for SMTP provider
 */
@Configuration
@ConditionalOnProperty(name = "pulserelay.provider.email.type", havingValue = "smtp")
@Slf4j
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        log.info("Configuring JavaMailSender for SMTP");
        
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        
        // Get SMTP configuration from environment variables
        String host = System.getenv("PULSERELAY_SMTP_HOST");
        String port = System.getenv("PULSERELAY_SMTP_PORT");
        String username = System.getenv("PULSERELAY_SMTP_USERNAME");
        String password = System.getenv("PULSERELAY_SMTP_PASSWORD");
        String auth = System.getenv("PULSERELAY_SMTP_AUTH");
        String starttls = System.getenv("PULSERELAY_SMTP_STARTTLS");
        
        log.info("SMTP Configuration: host={}, port={}, username={}, auth={}, starttls={}", 
                host, port, username, auth, starttls);
        
        // Set basic properties
        mailSender.setHost(host != null ? host : "localhost");
        mailSender.setPort(port != null ? Integer.parseInt(port) : 1025);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        
        // Set SMTP properties
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", auth != null ? auth : "false");
        props.put("mail.smtp.starttls.enable", starttls != null ? starttls : "false");
        props.put("mail.smtp.starttls.required", starttls != null ? starttls : "false");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        
        log.info("JavaMailSender configured successfully");
        return mailSender;
    }
}
