package com.emailprocessor.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class SqsPublisherService {
    
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;
    
    public SqsPublisherService(SqsClient sqsClient, 
                              @Value("${sqs.queue-url}") String queueUrl,
                              ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
    }
    
    public String publishEmailMessage(com.emailprocessor.api.dto.EmailRequest.EmailData emailData, String correlationId) {
        try {
            // Create message payload
            Map<String, Object> messagePayload = new HashMap<>();
            messagePayload.put("emailSubject", emailData.getEmailSubject());
            messagePayload.put("emailSender", emailData.getEmailSender());
            messagePayload.put("emailTimestream", emailData.getEmailTimestream());
            messagePayload.put("emailContent", emailData.getEmailContent());
            messagePayload.put("correlationId", correlationId);
            messagePayload.put("timestamp", System.currentTimeMillis());
            
            String messageBody = objectMapper.writeValueAsString(messagePayload);
            
            // Create message attributes
            Map<String, software.amazon.awssdk.services.sqs.model.MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("correlationId", 
                software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                    .stringValue(correlationId)
                    .dataType("String")
                    .build());
            messageAttributes.put("sender", 
                software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                    .stringValue(emailData.getEmailSender())
                    .dataType("String")
                    .build());
            
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageAttributes(messageAttributes)
                    .build();
            
            SendMessageResponse response = sqsClient.sendMessage(request);
            
            log.info("Successfully published message to SQS. MessageId: {}, CorrelationId: {}", 
                    response.messageId(), correlationId);
            
            return response.messageId();
            
        } catch (SqsException e) {
            log.error("Failed to publish message to SQS. CorrelationId: {}", correlationId, e);
            throw new RuntimeException("Failed to publish message to SQS", e);
        } catch (Exception e) {
            log.error("Unexpected error publishing message to SQS. CorrelationId: {}", correlationId, e);
            throw new RuntimeException("Unexpected error publishing message to SQS", e);
        }
    }
}
