package com.pulserelay.provider;

import com.pulserelay.provider.model.DeliveryRequest;
import com.pulserelay.provider.model.DeliveryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * SMTP email provider for production use - Phase 09
 * Sends real emails using Spring Mail (SMTP configuration in application properties)
 */
@Component
@ConditionalOnProperty(name = "pulserelay.provider.email.type", havingValue = "smtp")
@Slf4j
public class SmtpEmailProvider implements NotificationProvider {

    private static final String CHANNEL = "EMAIL";
    private static final String PROVIDER_NAME = "Smtp";

    private final JavaMailSender mailSender;
    private final String fromAddress;

    @Autowired(required = false)
    public SmtpEmailProvider(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        this.fromAddress = System.getenv("PULSERELAY_SMTP_FROM");
        log.info("SmtpEmailProvider initialized: mailSender={}, fromAddress={}", 
                mailSender != null ? "present" : "null", 
                fromAddress != null ? "configured" : "not set");
    }

    @Override
    public String getChannel() {
        return CHANNEL;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public DeliveryResult send(DeliveryRequest request) {
        try {
            if (!isAvailable()) {
                return DeliveryResult.builder()
                        .status(DeliveryResult.DeliveryStatus.FAILED)
                        .errorCode("PROVIDER_NOT_CONFIGURED")
                        .errorMessage("SMTP provider is not configured")
                        .isRetryable(false)
                        .build();
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(request.getRecipient());
            message.setSubject(request.getSubject());
            message.setText(request.getBody());

            mailSender.send(message);

            log.info("Email sent successfully to {}", request.getRecipient());

            return DeliveryResult.builder()
                    .status(DeliveryResult.DeliveryStatus.ACCEPTED)  // SMTP acceptance, not delivery confirmation
                    .providerMessageId("smtp-" + UUID.randomUUID())
                    .isRetryable(false)
                    .build();

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", request.getRecipient(), e.getMessage(), e);

            // Determine if error is retryable
            boolean isRetryable = isRetryableError(e);

            return DeliveryResult.builder()
                    .status(isRetryable ? DeliveryResult.DeliveryStatus.FAILED : DeliveryResult.DeliveryStatus.PERMANENT_FAILURE)
                    .errorCode(e.getClass().getSimpleName())
                    .errorMessage(e.getMessage())
                    .isRetryable(isRetryable)
                    .build();
        }
    }

    @Override
    public boolean isAvailable() {
        boolean available = mailSender != null && fromAddress != null && !fromAddress.isBlank();
        log.debug("SmtpEmailProvider.isAvailable() = {} (mailSender={}, fromAddress={})", 
                available, mailSender != null, fromAddress != null ? "set" : "null");
        return available;
    }

    private boolean isRetryableError(Exception e) {
        String message = e.getMessage();
        String className = e.getClass().getSimpleName();
        
        if (message == null) message = "";

        // These errors are PERMANENT (non-retryable)
        return !message.contains("Invalid email address") &&
                !message.contains("User unknown") &&
                !message.contains("Recipient rejected") &&
                !message.contains("Authentication failed") &&
                !message.contains("Invalid credentials") &&
                !className.contains("AuthenticationException");
    }
}
