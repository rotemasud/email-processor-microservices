package com.emailprocessor.api.controller;

import com.emailprocessor.api.dto.EmailRequest;
import com.emailprocessor.api.service.SqsPublisherService;
import com.emailprocessor.api.service.ValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmailController.class)
class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ValidationService validationService;

    @MockBean
    private SqsPublisherService sqsPublisherService;

    private EmailRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new EmailRequest();
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Happy new year!");
        emailData.setEmailSender("John doe");
        emailData.setEmailTimestream("1693561101");
        emailData.setEmailContent("Just want to say... Happy new year!!!");
        validRequest.setData(emailData);
        validRequest.setToken("$DJISA<$#45ex3RtYr");
    }

    @Test
    void testProcessEmail_Success() throws Exception {
        // Given
        when(validationService.validateToken(anyString())).thenReturn(true);
        when(validationService.validateEmailData(any())).thenReturn(true);
        when(sqsPublisherService.publishEmailMessage(any(), anyString())).thenReturn("message-id-123");

        // When & Then
        mockMvc.perform(post("/api/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email processed successfully and queued for storage"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void testProcessEmail_InvalidToken() throws Exception {
        // Given
        when(validationService.validateToken(anyString())).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid token"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void testProcessEmail_InvalidEmailData() throws Exception {
        // Given
        when(validationService.validateToken(anyString())).thenReturn(true);
        when(validationService.validateEmailData(any())).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email data - all fields are required and timestamp must be valid"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void testProcessEmail_MissingToken() throws Exception {
        // Given
        validRequest.setToken(null);

        // When & Then
        mockMvc.perform(post("/api/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testProcessEmail_MissingData() throws Exception {
        // Given
        validRequest.setData(null);

        // When & Then
        mockMvc.perform(post("/api/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testProcessEmail_SqsPublishFailure() throws Exception {
        // Given
        when(validationService.validateToken(anyString())).thenReturn(true);
        when(validationService.validateEmailData(any())).thenReturn(true);
        when(sqsPublisherService.publishEmailMessage(any(), anyString()))
                .thenThrow(new RuntimeException("SQS connection failed"));

        // When & Then
        mockMvc.perform(post("/api/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(result -> result.getResponse().getContentAsString().equals("Service is healthy"));
    }
}

