package com.emailprocessor.processor.service;

import com.emailprocessor.processor.dto.EmailMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageProcessor {
    
    private final S3UploaderService s3UploaderService;
    private final ObjectMapper objectMapper;
    private final Counter messagesProcessedSuccessCounter;
    private final Counter messagesProcessedFailureCounter;
    private final Timer messageProcessingTimer;
    
    public MessageProcessor(S3UploaderService s3UploaderService, 
                           ObjectMapper objectMapper,
                           Counter sqsMessagesProcessedSuccessCounter,
                           Counter sqsMessagesProcessedFailureCounter,
                           Timer messageProcessingTimer) {
        this.s3UploaderService = s3UploaderService;
        this.objectMapper = objectMapper;
        this.messagesProcessedSuccessCounter = sqsMessagesProcessedSuccessCounter;
        this.messagesProcessedFailureCounter = sqsMessagesProcessedFailureCounter;
        this.messageProcessingTimer = messageProcessingTimer;
    }
    
    public boolean processMessage(String messageBody, String correlationId) {
        return messageProcessingTimer.record(() -> {
            try {
                log.info("Processing message. CorrelationId: {}", correlationId);
                
                // Parse the message
                EmailMessage emailMessage = objectMapper.readValue(messageBody, EmailMessage.class);
                
                // Validate the message
                if (!isValidEmailMessage(emailMessage)) {
                    log.warn("Invalid email message received. CorrelationId: {}", correlationId);
                    messagesProcessedFailureCounter.increment();
                    return false;
                }
                
                // Upload to S3
                String s3Key = s3UploaderService.uploadToS3(emailMessage, correlationId);
                
                messagesProcessedSuccessCounter.increment();
                log.info("Message processed successfully. S3Key: {}, CorrelationId: {}", s3Key, correlationId);
                return true;
                
            } catch (Exception e) {
                log.error("Error processing message. CorrelationId: {}", correlationId, e);
                messagesProcessedFailureCounter.increment();
                return false;
            }
        });
    }
    
    private boolean isValidEmailMessage(EmailMessage emailMessage) {
        if (emailMessage == null) {
            return false;
        }
        
        return emailMessage.getEmailSubject() != null && !emailMessage.getEmailSubject().trim().isEmpty() &&
               emailMessage.getEmailSender() != null && !emailMessage.getEmailSender().trim().isEmpty() &&
               emailMessage.getEmailTimestream() != null && !emailMessage.getEmailTimestream().trim().isEmpty() &&
               emailMessage.getEmailContent() != null && !emailMessage.getEmailContent().trim().isEmpty();
    }
}
