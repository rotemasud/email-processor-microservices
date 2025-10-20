package com.emailprocessor.api.service;

import com.emailprocessor.api.dto.EmailRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock
    private SsmClient ssmClient;

    private ValidationService validationService;

    private final String parameterName = "/email-processor/api-token";
    private final String validToken = "$DJISA<$#45ex3RtYr";

    @BeforeEach
    void setUp() {
        // Mock SSM response
        Parameter parameter = Parameter.builder()
                .name(parameterName)
                .value(validToken)
                .build();

        GetParameterResponse response = GetParameterResponse.builder()
                .parameter(parameter)
                .build();

        when(ssmClient.getParameter(any(GetParameterRequest.class))).thenReturn(response);

        validationService = new ValidationService(ssmClient, parameterName);
    }

    @Test
    void testValidateToken_ValidToken() {
        // When
        boolean result = validationService.validateToken(validToken);

        // Then
        assertTrue(result);
    }

    @Test
    void testValidateToken_InvalidToken() {
        // When
        boolean result = validationService.validateToken("wrong-token");

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateToken_NullToken() {
        // When
        boolean result = validationService.validateToken(null);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateEmailData_ValidData() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Happy new year!");
        emailData.setEmailSender("John doe");
        emailData.setEmailTimestream("1693561101");
        emailData.setEmailContent("Just want to say... Happy new year!!!");

        // When
        boolean result = validationService.validateEmailData(emailData);

        // Then
        assertTrue(result);
    }

    @Test
    void testValidateEmailData_MissingSubject() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject(null);
        emailData.setEmailSender("John doe");
        emailData.setEmailTimestream("1693561101");
        emailData.setEmailContent("Content");

        // When
        boolean result = validationService.validateEmailData(emailData);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateEmailData_MissingSender() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Subject");
        emailData.setEmailSender("");
        emailData.setEmailTimestream("1693561101");
        emailData.setEmailContent("Content");

        // When
        boolean result = validationService.validateEmailData(emailData);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateEmailData_MissingTimestream() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Subject");
        emailData.setEmailSender("Sender");
        emailData.setEmailTimestream(null);
        emailData.setEmailContent("Content");

        // When
        boolean result = validationService.validateEmailData(emailData);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateEmailData_MissingContent() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Subject");
        emailData.setEmailSender("Sender");
        emailData.setEmailTimestream("1693561101");
        emailData.setEmailContent("   ");

        // When
        boolean result = validationService.validateEmailData(emailData);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateEmailData_InvalidTimestampFormat() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Subject");
        emailData.setEmailSender("Sender");
        emailData.setEmailTimestream("not-a-number");
        emailData.setEmailContent("Content");

        // When
        boolean result = validationService.validateEmailData(emailData);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateEmailData_NegativeTimestamp() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Subject");
        emailData.setEmailSender("Sender");
        emailData.setEmailTimestream("-1");
        emailData.setEmailContent("Content");

        // When
        boolean result = validationService.validateEmailData(emailData);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateEmailData_ZeroTimestamp() {
        // Given
        EmailRequest.EmailData emailData = new EmailRequest.EmailData();
        emailData.setEmailSubject("Subject");
        emailData.setEmailSender("Sender");
        emailData.setEmailTimestream("0");
        emailData.setEmailContent("Content");

        // When
        boolean result = validationService.validateEmailData(emailData);

        // Then
        assertFalse(result);
    }

    @Test
    void testValidateEmailData_NullData() {
        // When
        boolean result = validationService.validateEmailData(null);

        // Then
        assertFalse(result);
    }
}

