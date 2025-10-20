package com.emailprocessor.processor.service;

import com.emailprocessor.processor.dto.EmailMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MessageProcessor {
    
    private final S3UploaderService s3UploaderService;
    private final ObjectMapper objectMapper;
    
    public MessageProcessor(S3UploaderService s3UploaderService, ObjectMapper objectMapper) {
        this.s3UploaderService = s3UploaderService;
        this.objectMapper = objectMapper;
    }
    
    public boolean processMessage(String messageBody, String correlationId) {
        try {
            log.info("Processing message. CorrelationId: {}", correlationId);
            
            // Parse the message
            EmailMessage emailMessage = objectMapper.readValue(messageBody, EmailMessage.class);
            
            // Validate the message
            if (!isValidEmailMessage(emailMessage)) {
                log.warn("Invalid email message received. CorrelationId: {}", correlationId);
                return false;
            }
            
            // Upload to S3
            String s3Key = s3UploaderService.uploadToS3(emailMessage, correlationId);
            
            log.info("Message processed successfully. S3Key: {}, CorrelationId: {}", s3Key, correlationId);
            return true;
            
        } catch (Exception e) {
            log.error("Error processing message. CorrelationId: {}", correlationId, e);
            return false;
        }
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
