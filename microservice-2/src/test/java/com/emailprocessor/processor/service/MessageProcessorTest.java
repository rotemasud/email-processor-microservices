package com.emailprocessor.processor.service;

import com.emailprocessor.processor.dto.EmailMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MessageProcessorTest {

    @Mock(lenient = true)
    private S3UploaderService s3UploaderService;

    @Mock(lenient = true)
    private Counter messagesProcessedSuccessCounter;

    @Mock(lenient = true)
    private Counter messagesProcessedFailureCounter;

    @Mock(lenient = true)
    private Timer messageProcessingTimer;

    private MessageProcessor messageProcessor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        // Mock the timer to execute the supplier directly
        doAnswer(invocation -> {
            java.util.function.Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        }).when(messageProcessingTimer).record(any());
        
        messageProcessor = new MessageProcessor(s3UploaderService, objectMapper, 
                messagesProcessedSuccessCounter, messagesProcessedFailureCounter, messageProcessingTimer);
    }

    @Test
    void testProcessMessage_Success() throws Exception {
        // Given
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject("Happy new year!");
        emailMessage.setEmailSender("John doe");
        emailMessage.setEmailTimestream("1693561101");
        emailMessage.setEmailContent("Just want to say... Happy new year!!!");
        emailMessage.setCorrelationId("test-correlation-id");
        emailMessage.setTimestamp(System.currentTimeMillis());

        String messageBody = objectMapper.writeValueAsString(emailMessage);
        String correlationId = "test-correlation-id";

        // Explicitly stub for this test - use when().thenReturn() with lenient class mock
        when(s3UploaderService.uploadToS3(any(EmailMessage.class), anyString()))
                .thenReturn("emails/2023/09/01/1693561101-john_doe.json");

        // When
        boolean result = messageProcessor.processMessage(messageBody, correlationId);

        // Then
        assertTrue(result, "Message processing should return true when S3 upload succeeds");
        verify(s3UploaderService, times(1)).uploadToS3(any(EmailMessage.class), eq(correlationId));
    }

    @Test
    void testProcessMessage_InvalidJson() {
        // Given
        String invalidMessageBody = "{ invalid json }";
        String correlationId = "test-correlation-id";

        // When
        boolean result = messageProcessor.processMessage(invalidMessageBody, correlationId);

        // Then
        assertFalse(result);
        verify(s3UploaderService, never()).uploadToS3(any(), anyString());
    }

    @Test
    void testProcessMessage_MissingSubject() throws Exception {
        // Given
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject(null);
        emailMessage.setEmailSender("John doe");
        emailMessage.setEmailTimestream("1693561101");
        emailMessage.setEmailContent("Content");

        String messageBody = objectMapper.writeValueAsString(emailMessage);
        String correlationId = "test-correlation-id";

        // When
        boolean result = messageProcessor.processMessage(messageBody, correlationId);

        // Then
        assertFalse(result);
        verify(s3UploaderService, never()).uploadToS3(any(), anyString());
    }

    @Test
    void testProcessMessage_MissingSender() throws Exception {
        // Given
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject("Subject");
        emailMessage.setEmailSender("");
        emailMessage.setEmailTimestream("1693561101");
        emailMessage.setEmailContent("Content");

        String messageBody = objectMapper.writeValueAsString(emailMessage);
        String correlationId = "test-correlation-id";

        // When
        boolean result = messageProcessor.processMessage(messageBody, correlationId);

        // Then
        assertFalse(result);
        verify(s3UploaderService, never()).uploadToS3(any(), anyString());
    }

    @Test
    void testProcessMessage_S3UploadFailure() throws Exception {
        // Given
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject("Subject");
        emailMessage.setEmailSender("Sender");
        emailMessage.setEmailTimestream("1693561101");
        emailMessage.setEmailContent("Content");

        String messageBody = objectMapper.writeValueAsString(emailMessage);
        String correlationId = "test-correlation-id";

        // Stub to throw exception - use when().thenThrow() with lenient class mock
        when(s3UploaderService.uploadToS3(any(EmailMessage.class), anyString()))
                .thenThrow(new RuntimeException("S3 connection failed"));

        // When
        boolean result = messageProcessor.processMessage(messageBody, correlationId);

        // Then
        assertFalse(result);
    }
}

