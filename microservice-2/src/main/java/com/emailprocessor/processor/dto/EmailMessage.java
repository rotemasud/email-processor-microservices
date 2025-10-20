package com.emailprocessor.processor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EmailMessage {
    
    @JsonProperty("emailSubject")
    private String emailSubject;
    
    @JsonProperty("emailSender")
    private String emailSender;
    
    @JsonProperty("emailTimestream")
    private String emailTimestream;
    
    @JsonProperty("emailContent")
    private String emailContent;
    
    @JsonProperty("correlationId")
    private String correlationId;
    
    @JsonProperty("timestamp")
    private Long timestamp;
}
