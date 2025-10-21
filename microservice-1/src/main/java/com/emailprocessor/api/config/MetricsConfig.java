package com.emailprocessor.api.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter sqsMessagesSentCounter(MeterRegistry registry) {
        return Counter.builder("sqs.messages.sent")
                .description("Total number of messages sent to SQS")
                .tag("service", "microservice-1")
                .register(registry);
    }

    @Bean
    public Counter sqsMessagesSentFailureCounter(MeterRegistry registry) {
        return Counter.builder("sqs.messages.sent.failure")
                .description("Total number of failed message sends to SQS")
                .tag("service", "microservice-1")
                .register(registry);
    }

    @Bean
    public Counter validationSuccessCounter(MeterRegistry registry) {
        return Counter.builder("validation.success")
                .description("Total number of successful validations")
                .tag("service", "microservice-1")
                .register(registry);
    }

    @Bean
    public Counter validationFailureCounter(MeterRegistry registry) {
        return Counter.builder("validation.failure")
                .description("Total number of failed validations")
                .tag("service", "microservice-1")
                .tag("type", "all")
                .register(registry);
    }

    @Bean
    public Counter tokenValidationFailureCounter(MeterRegistry registry) {
        return Counter.builder("validation.failure")
                .description("Total number of failed token validations")
                .tag("service", "microservice-1")
                .tag("type", "token")
                .register(registry);
    }

    @Bean
    public Counter emailDataValidationFailureCounter(MeterRegistry registry) {
        return Counter.builder("validation.failure")
                .description("Total number of failed email data validations")
                .tag("service", "microservice-1")
                .tag("type", "emaildata")
                .register(registry);
    }

    @Bean
    public Timer sqsPublishTimer(MeterRegistry registry) {
        return Timer.builder("sqs.publish.duration")
                .description("Time taken to publish messages to SQS")
                .tag("service", "microservice-1")
                .register(registry);
    }

    @Bean
    public Timer apiRequestTimer(MeterRegistry registry) {
        return Timer.builder("api.request.duration")
                .description("API request processing duration")
                .tag("service", "microservice-1")
                .register(registry);
    }
}

