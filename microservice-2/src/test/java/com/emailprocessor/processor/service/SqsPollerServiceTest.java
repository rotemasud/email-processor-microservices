package com.emailprocessor.processor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsPollerServiceTest {

    @Mock
    private SqsClient sqsClient;

    @Mock
    private MessageProcessor messageProcessor;

    private SqsPollerService sqsPollerService;

    private final String queueUrl = "https://sqs.us-west-1.amazonaws.com/123456789/test-queue";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sqsPollerService = new SqsPollerService(sqsClient, queueUrl, messageProcessor, objectMapper);
    }

    @Test
    void testPollMessages_NoMessages() {
        // Given
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(Collections.emptyList())
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);

        // When
        sqsPollerService.pollMessages();

        // Then
        verify(sqsClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        verify(messageProcessor, never()).processMessage(anyString(), anyString());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void testPollMessages_SuccessfulProcessing() {
        // Given
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("correlationId", MessageAttributeValue.builder()
                .stringValue("test-correlation-id")
                .dataType("String")
                .build());

        Message message = Message.builder()
                .messageId("message-123")
                .body("{\"emailSubject\":\"Test\"}")
                .receiptHandle("receipt-handle-123")
                .messageAttributes(messageAttributes)
                .build();

        ReceiveMessageResponse receiveResponse = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResponse);
        when(messageProcessor.processMessage(anyString(), anyString())).thenReturn(true);

        // When
        sqsPollerService.pollMessages();

        // Then
        verify(sqsClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        verify(messageProcessor, times(1)).processMessage(anyString(), eq("test-correlation-id"));
        verify(sqsClient, times(1)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void testPollMessages_FailedProcessing() {
        // Given
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("correlationId", MessageAttributeValue.builder()
                .stringValue("test-correlation-id")
                .dataType("String")
                .build());

        Message message = Message.builder()
                .messageId("message-123")
                .body("{\"emailSubject\":\"Test\"}")
                .receiptHandle("receipt-handle-123")
                .messageAttributes(messageAttributes)
                .build();

        ReceiveMessageResponse receiveResponse = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResponse);
        when(messageProcessor.processMessage(anyString(), anyString())).thenReturn(false);

        // When
        sqsPollerService.pollMessages();

        // Then
        verify(sqsClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        verify(messageProcessor, times(1)).processMessage(anyString(), anyString());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void testPollMessages_MultipleMessages() {
        // Given
        Map<String, MessageAttributeValue> messageAttributes1 = new HashMap<>();
        messageAttributes1.put("correlationId", MessageAttributeValue.builder()
                .stringValue("correlation-1")
                .dataType("String")
                .build());

        Map<String, MessageAttributeValue> messageAttributes2 = new HashMap<>();
        messageAttributes2.put("correlationId", MessageAttributeValue.builder()
                .stringValue("correlation-2")
                .dataType("String")
                .build());

        Message message1 = Message.builder()
                .messageId("message-1")
                .body("{\"emailSubject\":\"Test1\"}")
                .receiptHandle("receipt-1")
                .messageAttributes(messageAttributes1)
                .build();

        Message message2 = Message.builder()
                .messageId("message-2")
                .body("{\"emailSubject\":\"Test2\"}")
                .receiptHandle("receipt-2")
                .messageAttributes(messageAttributes2)
                .build();

        ReceiveMessageResponse receiveResponse = ReceiveMessageResponse.builder()
                .messages(List.of(message1, message2))
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResponse);
        when(messageProcessor.processMessage(anyString(), anyString())).thenReturn(true);

        // When
        sqsPollerService.pollMessages();

        // Then
        verify(sqsClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        verify(messageProcessor, times(2)).processMessage(anyString(), anyString());
        verify(sqsClient, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void testPollMessages_ProcessingException() {
        // Given
        Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("correlationId", MessageAttributeValue.builder()
                .stringValue("test-correlation-id")
                .dataType("String")
                .build());

        Message message = Message.builder()
                .messageId("message-123")
                .body("{\"emailSubject\":\"Test\"}")
                .receiptHandle("receipt-handle-123")
                .messageAttributes(messageAttributes)
                .build();

        ReceiveMessageResponse receiveResponse = ReceiveMessageResponse.builder()
                .messages(List.of(message))
                .build();

        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(receiveResponse);
        when(messageProcessor.processMessage(anyString(), anyString()))
                .thenThrow(new RuntimeException("Processing error"));

        // When
        sqsPollerService.pollMessages();

        // Then
        verify(sqsClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        verify(messageProcessor, times(1)).processMessage(anyString(), anyString());
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void testPollMessages_SqsException() {
        // Given
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(SqsException.builder().message("SQS error").build());

        // When
        sqsPollerService.pollMessages();

        // Then
        verify(sqsClient, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
        verify(messageProcessor, never()).processMessage(anyString(), anyString());
    }
}

