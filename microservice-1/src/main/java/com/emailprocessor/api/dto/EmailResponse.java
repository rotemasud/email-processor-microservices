package com.emailprocessor.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailResponse {
    
    private boolean success;
    private String message;
    private String correlationId;
    
    public static EmailResponse success(String message, String correlationId) {
        return new EmailResponse(true, message, correlationId);
    }
    
    public static EmailResponse error(String message, String correlationId) {
        return new EmailResponse(false, message, correlationId);
    }
}
