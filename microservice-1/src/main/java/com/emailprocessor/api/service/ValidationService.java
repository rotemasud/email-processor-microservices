package com.emailprocessor.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ValidationService {
    
    private final SsmClient ssmClient;
    private final String parameterName;
    private final ConcurrentHashMap<String, String> tokenCache = new ConcurrentHashMap<>();
    private static final String CACHE_KEY = "api_token";
    
    public ValidationService(SsmClient ssmClient, @Value("${ssm.parameter-name}") String parameterName) {
        this.ssmClient = ssmClient;
        this.parameterName = parameterName;
        loadTokenFromSSM();
    }
    
    public boolean validateToken(String providedToken) {
        try {
            String cachedToken = tokenCache.get(CACHE_KEY);
            if (cachedToken == null) {
                log.warn("Token not found in cache, reloading from SSM");
                loadTokenFromSSM();
                cachedToken = tokenCache.get(CACHE_KEY);
            }
            
            if (cachedToken == null) {
                log.error("Failed to retrieve token from SSM");
                return false;
            }
            
            boolean isValid = cachedToken.equals(providedToken);
            if (!isValid) {
                log.warn("Token validation failed for provided token");
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("Error validating token", e);
            return false;
        }
    }
    
    public boolean validateEmailData(com.emailprocessor.api.dto.EmailRequest.EmailData data) {
        if (data == null) {
            log.warn("Email data is null");
            return false;
        }
        
        // Check all 4 required fields are present and not empty
        boolean hasSubject = data.getEmailSubject() != null && !data.getEmailSubject().trim().isEmpty();
        boolean hasSender = data.getEmailSender() != null && !data.getEmailSender().trim().isEmpty();
        boolean hasTimestream = data.getEmailTimestream() != null && !data.getEmailTimestream().trim().isEmpty();
        boolean hasContent = data.getEmailContent() != null && !data.getEmailContent().trim().isEmpty();
        
        if (!hasSubject || !hasSender || !hasTimestream || !hasContent) {
            log.warn("Missing required email fields - subject: {}, sender: {}, timestream: {}, content: {}", 
                    hasSubject, hasSender, hasTimestream, hasContent);
            return false;
        }
        
        // Validate timestamp format (Unix epoch)
        try {
            long timestamp = Long.parseLong(data.getEmailTimestream());
            if (timestamp <= 0) {
                log.warn("Invalid timestamp: {}", timestamp);
                return false;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp format: {}", data.getEmailTimestream());
            return false;
        }
        
        return true;
    }
    
    private void loadTokenFromSSM() {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            
            GetParameterResponse response = ssmClient.getParameter(request);
            String token = response.parameter().value();
            
            tokenCache.put(CACHE_KEY, token);
            log.info("Successfully loaded token from SSM parameter: {}", parameterName);
            
        } catch (SsmException e) {
            log.error("Failed to retrieve token from SSM parameter: {}", parameterName, e);
            throw new RuntimeException("Failed to retrieve API token from SSM", e);
        }
    }
    
    public void refreshToken() {
        log.info("Refreshing token from SSM");
        loadTokenFromSSM();
    }
}
