package com.emailprocessor.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EmailRequest {
    
    @NotNull(message = "Data field is required")
    @Valid
    private EmailData data;
    
    @NotBlank(message = "Token is required")
    private String token;
    
    @Data
    public static class EmailData {
        
        @NotBlank(message = "Email subject is required")
        @JsonProperty("email_subject")
        private String emailSubject;
        
        @NotBlank(message = "Email sender is required")
        @JsonProperty("email_sender")
        private String emailSender;
        
        @NotBlank(message = "Email timestream is required")
        @JsonProperty("email_timestream")
        private String emailTimestream;
        
        @NotBlank(message = "Email content is required")
        @JsonProperty("email_content")
        private String emailContent;
    }
}
