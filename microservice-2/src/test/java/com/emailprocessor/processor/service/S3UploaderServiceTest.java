package com.emailprocessor.processor.service;

import com.emailprocessor.processor.dto.EmailMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class S3UploaderServiceTest {

    @Mock(lenient = true)
    private S3Client s3Client;

    @Mock(lenient = true)
    private Counter s3UploadsSuccessCounter;

    @Mock(lenient = true)
    private Counter s3UploadsFailureCounter;

    @Mock(lenient = true)
    private Timer s3UploadTimer;

    @Mock(lenient = true)
    private DistributionSummary s3FileSizeSummary;

    private S3UploaderService s3UploaderService;

    private final String bucketName = "test-email-bucket";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Mock the timer to execute the supplier directly (lenient to avoid stubbing conflicts)
        lenient().when(s3UploadTimer.record(any(java.util.function.Supplier.class)))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(0);
                    return supplier.get();
                });
        s3UploaderService = new S3UploaderService(s3Client, bucketName, objectMapper,
                s3UploadsSuccessCounter, s3UploadsFailureCounter, s3UploadTimer, s3FileSizeSummary);
    }

    @Test
    void testUploadToS3_Success() {
        // Given
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject("Happy new year!");
        emailMessage.setEmailSender("John doe");
        emailMessage.setEmailTimestream("1693561101");
        emailMessage.setEmailContent("Just want to say... Happy new year!!!");
        emailMessage.setCorrelationId("test-correlation-id");
        emailMessage.setTimestamp(System.currentTimeMillis());

        String correlationId = "test-correlation-id";

        PutObjectResponse response = PutObjectResponse.builder()
                .eTag("test-etag")
                .build();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(response);

        // When
        String s3Key = s3UploaderService.uploadToS3(emailMessage, correlationId);

        // Then
        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("emails/"));
        assertTrue(s3Key.endsWith(".json"));
        assertTrue(s3Key.contains("john_doe"));
        verify(s3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void testUploadToS3_VerifyS3Request() {
        // Given
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject("Test Subject");
        emailMessage.setEmailSender("Test Sender");
        emailMessage.setEmailTimestream("1693561101");
        emailMessage.setEmailContent("Test Content");
        emailMessage.setCorrelationId("test-correlation-id");

        String correlationId = "test-correlation-id";

        PutObjectResponse response = PutObjectResponse.builder()
                .eTag("test-etag")
                .build();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(response);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);

        // When
        s3UploaderService.uploadToS3(emailMessage, correlationId);

        // Then
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest capturedRequest = requestCaptor.getValue();

        assertEquals(bucketName, capturedRequest.bucket());
        assertTrue(capturedRequest.key().contains("emails/"));
        assertEquals("application/json", capturedRequest.contentType());
        assertNotNull(capturedRequest.metadata());
        assertTrue(capturedRequest.metadata().containsKey("correlation-id"));
        assertTrue(capturedRequest.metadata().containsKey("email-sender"));
    }

    @Test
    void testUploadToS3_GenerateCorrectPath() {
        // Given - Unix timestamp: 1693561101 = 2023-09-01 07:05:01 UTC
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject("Subject");
        emailMessage.setEmailSender("John Doe");
        emailMessage.setEmailTimestream("1693561101");
        emailMessage.setEmailContent("Content");

        String correlationId = "test-correlation-id";

        PutObjectResponse response = PutObjectResponse.builder()
                .eTag("test-etag")
                .build();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(response);

        // When
        String s3Key = s3UploaderService.uploadToS3(emailMessage, correlationId);

        // Then
        assertTrue(s3Key.startsWith("emails/2023/09/01/"));
        assertTrue(s3Key.contains("john_doe"));
        assertTrue(s3Key.endsWith(".json"));
    }

    @Test
    void testUploadToS3_SanitizeSenderName() {
        // Given
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject("Subject");
        emailMessage.setEmailSender("John Doe <john@example.com>");
        emailMessage.setEmailTimestream("1693561101");
        emailMessage.setEmailContent("Content");

        String correlationId = "test-correlation-id";

        PutObjectResponse response = PutObjectResponse.builder()
                .eTag("test-etag")
                .build();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(response);

        // When
        String s3Key = s3UploaderService.uploadToS3(emailMessage, correlationId);

        // Then
        assertFalse(s3Key.contains("<"));
        assertFalse(s3Key.contains(">"));
        assertFalse(s3Key.contains("@"));
        assertTrue(s3Key.contains("_"));
    }

    @Test
    void testUploadToS3_S3Exception() {
        // Given
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject("Subject");
        emailMessage.setEmailSender("Sender");
        emailMessage.setEmailTimestream("1693561101");
        emailMessage.setEmailContent("Content");

        String correlationId = "test-correlation-id";

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("S3 error").build());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                s3UploaderService.uploadToS3(emailMessage, correlationId)
        );

        assertTrue(exception.getMessage().contains("Failed to upload email to S3"));
    }

    @Test
    void testUploadToS3_InvalidTimestampFallback() {
        // Given
        EmailMessage emailMessage = new EmailMessage();
        emailMessage.setEmailSubject("Subject");
        emailMessage.setEmailSender("Test User");
        emailMessage.setEmailTimestream("invalid-timestamp");
        emailMessage.setEmailContent("Content");

        String correlationId = "test-correlation-id";

        PutObjectResponse response = PutObjectResponse.builder()
                .eTag("test-etag")
                .build();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(response);

        // When
        String s3Key = s3UploaderService.uploadToS3(emailMessage, correlationId);

        // Then
        assertNotNull(s3Key);
        assertTrue(s3Key.startsWith("emails/"));
        assertTrue(s3Key.contains("test_user"));
        // Should still create a valid path even with invalid timestamp
    }
}

