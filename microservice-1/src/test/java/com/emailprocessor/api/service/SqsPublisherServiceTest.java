package com.emailprocessor.api.service;

import com.emailprocessor.api.dto.EmailRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsPublisherServiceTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private Counter messagesSentCounter;

    @Mock
    private Counter messagesSentFailureCounter;

    @Mock
    private Timer publishTimer;

    private SqsPublisherService sqsPublisherService;

    private final String queueUrl = "https://sqs.us-west-1.amazonaws.com/123456789/test-queue";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Mock the timer to execute the supplier directly
        when(publishTimer.record(any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });
        sqsPublisherService = new SqsPublisherService(sqsClient, queueUrl, objectMapper,
                messagesSentCounter, messagesSentFailureCounter, publishTimer);
    }

    @Test
    void testPublishEmailMessage_Success() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Happy new year!");
        emailData.setEmailSender("John doe");
        emailData.setEmailTimestream("1693561101");
        emailData.setEmailContent("Just want to say... Happy new year!!!");

        String correlationId = "test-correlation-id";

        SendMessageResponse response = SendMessageResponse.builder()
                .messageId("message-123")
                .build();

        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(response);

        // When
        String messageId = sqsPublisherService.publishEmailMessage(emailData, correlationId);

        // Then
        assertEquals("message-123", messageId);
        verify(sqsClient, times(1)).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void testPublishEmailMessage_VerifyMessageContent() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Test Subject");
        emailData.setEmailSender("Test Sender");
        emailData.setEmailTimestream("1693561101");
        emailData.setEmailContent("Test Content");

        String correlationId = "test-correlation-id";

        SendMessageResponse response = SendMessageResponse.builder()
                .messageId("message-123")
                .build();

        when(sqsClient.sendMessage(any(SendMessageRequest.class))).thenReturn(response);

        ArgumentCaptor<SendMessageRequest> requestCaptor = ArgumentCaptor.forClass(SendMessageRequest.class);

        // When
        sqsPublisherService.publishEmailMessage(emailData, correlationId);

        // Then
        verify(sqsClient).sendMessage(requestCaptor.capture());
        SendMessageRequest capturedRequest = requestCaptor.getValue();

        assertEquals(queueUrl, capturedRequest.queueUrl());
        assertTrue(capturedRequest.messageBody().contains("Test Subject"));
        assertTrue(capturedRequest.messageBody().contains("Test Sender"));
        assertTrue(capturedRequest.messageBody().contains("Test Content"));
        assertTrue(capturedRequest.messageBody().contains(correlationId));
        assertNotNull(capturedRequest.messageAttributes().get("correlationId"));
        assertNotNull(capturedRequest.messageAttributes().get("sender"));
    }

    @Test
    void testPublishEmailMessage_SqsException() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Subject");
        emailData.setEmailSender("Sender");
        emailData.setEmailTimestream("1693561101");
        emailData.setEmailContent("Content");

        String correlationId = "test-correlation-id";

        when(sqsClient.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder().message("SQS error").build());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                sqsPublisherService.publishEmailMessage(emailData, correlationId)
        );

        assertTrue(exception.getMessage().contains("Failed to publish message to SQS"));
    }
}

