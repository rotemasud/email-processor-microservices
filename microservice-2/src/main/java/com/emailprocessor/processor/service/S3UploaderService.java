package com.emailprocessor.processor.service;

import com.emailprocessor.processor.dto.EmailMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class S3UploaderService {
    
    private final S3Client s3Client;
    private final String bucketName;
    private final ObjectMapper objectMapper;
    private final Counter s3UploadsSuccessCounter;
    private final Counter s3UploadsFailureCounter;
    private final Timer s3UploadTimer;
    private final DistributionSummary s3FileSizeSummary;
    
    public S3UploaderService(S3Client s3Client,
                            @Value("${s3.bucket-name}") String bucketName,
                            ObjectMapper objectMapper,
                            Counter s3UploadsSuccessCounter,
                            Counter s3UploadsFailureCounter,
                            Timer s3UploadTimer,
                            DistributionSummary s3FileSizeSummary) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.objectMapper = objectMapper;
        this.s3UploadsSuccessCounter = s3UploadsSuccessCounter;
        this.s3UploadsFailureCounter = s3UploadsFailureCounter;
        this.s3UploadTimer = s3UploadTimer;
        this.s3FileSizeSummary = s3FileSizeSummary;
    }
    
    public String uploadToS3(EmailMessage emailMessage, String correlationId) {
        return s3UploadTimer.record(() -> {
            try {
                // Generate S3 key with path structure: emails/{year}/{month}/{day}/{timestamp}-{sender}.json
                String s3Key = generateS3Key(emailMessage);
                
                // Create enhanced message with metadata
                Map<String, Object> enhancedMessage = new HashMap<>();
                enhancedMessage.put("emailSubject", emailMessage.getEmailSubject());
                enhancedMessage.put("emailSender", emailMessage.getEmailSender());
                enhancedMessage.put("emailTimestream", emailMessage.getEmailTimestream());
                enhancedMessage.put("emailContent", emailMessage.getEmailContent());
                enhancedMessage.put("correlationId", correlationId);
                enhancedMessage.put("originalTimestamp", emailMessage.getTimestamp());
                enhancedMessage.put("processedAt", System.currentTimeMillis());
                enhancedMessage.put("s3Key", s3Key);
                
                String jsonContent = objectMapper.writeValueAsString(enhancedMessage);
                
                // Track file size
                s3FileSizeSummary.record(jsonContent.getBytes().length);
                
                // Upload to S3
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType("application/json")
                        .metadata(createMetadata(emailMessage, correlationId))
                        .build();
                
                PutObjectResponse response = s3Client.putObject(putObjectRequest, 
                        software.amazon.awssdk.core.sync.RequestBody.fromString(jsonContent));
                
                s3UploadsSuccessCounter.increment();
                
                log.info("Successfully uploaded email to S3. Key: {}, ETag: {}, CorrelationId: {}", 
                        s3Key, response.eTag(), correlationId);
                
                return s3Key;
                
            } catch (Exception e) {
                s3UploadsFailureCounter.increment();
                log.error("Error uploading email to S3. CorrelationId: {}", correlationId, e);
                throw new RuntimeException("Failed to upload email to S3", e);
            }
        });
    }
    
    private String generateS3Key(EmailMessage emailMessage) {
        try {
            // Parse timestamp to get date components
            long timestamp = Long.parseLong(emailMessage.getEmailTimestream());
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamp), ZoneOffset.UTC);
            
            String year = String.valueOf(dateTime.getYear());
            String month = String.format("%02d", dateTime.getMonthValue());
            String day = String.format("%02d", dateTime.getDayOfMonth());
            
            // Sanitize sender name for filename
            String sanitizedSender = emailMessage.getEmailSender()
                    .replaceAll("[^a-zA-Z0-9]", "_")
                    .toLowerCase();
            
            // Create filename: {timestamp}-{sender}.json
            String filename = String.format("%s-%s.json", 
                    emailMessage.getEmailTimestream(), sanitizedSender);
            
            return String.format("emails/%s/%s/%s/%s", year, month, day, filename);
            
        } catch (Exception e) {
            log.warn("Error parsing timestamp, using current date. Timestamp: {}", 
                    emailMessage.getEmailTimestream());
            
            LocalDateTime now = LocalDateTime.now();
            String year = String.valueOf(now.getYear());
            String month = String.format("%02d", now.getMonthValue());
            String day = String.format("%02d", now.getDayOfMonth());
            
            String sanitizedSender = emailMessage.getEmailSender()
                    .replaceAll("[^a-zA-Z0-9]", "_")
                    .toLowerCase();
            
            String filename = String.format("%s-%s.json", 
                    System.currentTimeMillis() / 1000, sanitizedSender);
            
            return String.format("emails/%s/%s/%s/%s", year, month, day, filename);
        }
    }
    
    private Map<String, String> createMetadata(EmailMessage emailMessage, String correlationId) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("correlation-id", correlationId);
        metadata.put("email-sender", emailMessage.getEmailSender());
        metadata.put("email-subject", emailMessage.getEmailSubject());
        metadata.put("email-timestream", emailMessage.getEmailTimestream());
        metadata.put("processed-at", String.valueOf(System.currentTimeMillis()));
        return metadata;
    }
}
