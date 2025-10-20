package com.emailprocessor.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class SqsPollerService {
    
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final MessageProcessor messageProcessor;
    private final ObjectMapper objectMapper;
    
    public SqsPollerService(SqsClient sqsClient,
                           @Value("${sqs.queue-url}") String queueUrl,
                           MessageProcessor messageProcessor,
                           ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.messageProcessor = messageProcessor;
        this.objectMapper = objectMapper;
    }
    
    @Scheduled(fixedRate = 30000) // Poll every 30 seconds
    public void pollMessages() {
        try {
            log.debug("Starting SQS message polling...");
            
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(20) // Long polling
                    .messageAttributeNames("All")
                    .build();
            
            ReceiveMessageResponse response = sqsClient.receiveMessage(receiveRequest);
            List<Message> messages = response.messages();
            
            if (messages.isEmpty()) {
                log.debug("No messages found in queue");
                return;
            }
            
            log.info("Received {} messages from SQS", messages.size());
            
            for (Message message : messages) {
                try {
                    processMessage(message);
                } catch (Exception e) {
                    log.error("Error processing message: {}", message.messageId(), e);
                    // Message will remain in queue and be retried
                }
            }
            
        } catch (Exception e) {
            log.error("Error polling SQS messages", e);
        }
    }
    
    private void processMessage(Message message) {
        String messageId = message.messageId();
        String correlationId = getCorrelationId(message);
        
        log.info("Processing message. MessageId: {}, CorrelationId: {}", messageId, correlationId);
        
        try {
            // Process the message
            boolean success = messageProcessor.processMessage(message.body(), correlationId);
            
            if (success) {
                // Delete message from queue after successful processing
                deleteMessage(message);
                log.info("Message processed and deleted successfully. MessageId: {}, CorrelationId: {}", 
                        messageId, correlationId);
            } else {
                log.warn("Message processing failed, keeping in queue for retry. MessageId: {}, CorrelationId: {}", 
                        messageId, correlationId);
            }
            
        } catch (Exception e) {
            log.error("Error processing message. MessageId: {}, CorrelationId: {}", messageId, correlationId, e);
            // Message will remain in queue and be retried
        }
    }
    
    private void deleteMessage(Message message) {
        try {
            DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build();
            
            sqsClient.deleteMessage(deleteRequest);
            log.debug("Message deleted from queue: {}", message.messageId());
            
        } catch (Exception e) {
            log.error("Error deleting message from queue: {}", message.messageId(), e);
            throw e;
        }
    }
    
    private String getCorrelationId(Message message) {
        try {
            return message.messageAttributes().get("correlationId").stringValue();
        } catch (Exception e) {
            log.warn("Could not extract correlation ID from message: {}", message.messageId());
            return "unknown";
        }
    }
}
