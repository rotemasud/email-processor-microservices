package com.emailprocessor.api.controller;

import com.emailprocessor.api.dto.EmailRequest;
import com.emailprocessor.api.dto.EmailResponse;
import com.emailprocessor.api.service.SqsPublisherService;
import com.emailprocessor.api.service.ValidationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
public class EmailController {
    
    private final ValidationService validationService;
    private final SqsPublisherService sqsPublisherService;
    
    public EmailController(ValidationService validationService, SqsPublisherService sqsPublisherService) {
        this.validationService = validationService;
        this.sqsPublisherService = sqsPublisherService;
    }
    
    @PostMapping("/email")
    public ResponseEntity<EmailResponse> processEmail(@Valid @RequestBody EmailRequest request) {
        String correlationId = UUID.randomUUID().toString();
        
        log.info("Received email processing request. CorrelationId: {}", correlationId);
        
        try {
            // Validate token
            if (!validationService.validateToken(request.getToken())) {
                log.warn("Token validation failed. CorrelationId: {}", correlationId);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(EmailResponse.error("Invalid token", correlationId));
            }
            
            // Validate email data
            if (!validationService.validateEmailData(request.getData())) {
                log.warn("Email data validation failed. CorrelationId: {}", correlationId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(EmailResponse.error("Invalid email data - all fields are required and timestamp must be valid", correlationId));
            }
            
            // Publish to SQS
            String messageId = sqsPublisherService.publishEmailMessage(request.getData(), correlationId);
            
            log.info("Email processing request completed successfully. MessageId: {}, CorrelationId: {}", 
                    messageId, correlationId);
            
            return ResponseEntity.ok(EmailResponse.success(
                    "Email processed successfully and queued for storage", correlationId));
            
        } catch (Exception e) {
            log.error("Unexpected error processing email request. CorrelationId: {}", correlationId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(EmailResponse.error("Internal server error", correlationId));
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Service is healthy");
    }
}
