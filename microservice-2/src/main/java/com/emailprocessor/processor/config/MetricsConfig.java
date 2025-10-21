package com.emailprocessor.processor.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Counter sqsMessagesReceivedCounter(MeterRegistry registry) {
        return Counter.builder("sqs.messages.received")
                .description("Total number of messages received from SQS")
                .tag("service", "microservice-2")
                .register(registry);
    }

    @Bean
    public Counter sqsMessagesProcessedSuccessCounter(MeterRegistry registry) {
        return Counter.builder("sqs.messages.processed")
                .description("Total number of successfully processed messages")
                .tag("service", "microservice-2")
                .tag("status", "success")
                .register(registry);
    }

    @Bean
    public Counter sqsMessagesProcessedFailureCounter(MeterRegistry registry) {
        return Counter.builder("sqs.messages.processed")
                .description("Total number of failed message processing attempts")
                .tag("service", "microservice-2")
                .tag("status", "failure")
                .register(registry);
    }

    @Bean
    public Counter s3UploadsSuccessCounter(MeterRegistry registry) {
        return Counter.builder("s3.uploads")
                .description("Total number of successful S3 uploads")
                .tag("service", "microservice-2")
                .tag("status", "success")
                .register(registry);
    }

    @Bean
    public Counter s3UploadsFailureCounter(MeterRegistry registry) {
        return Counter.builder("s3.uploads")
                .description("Total number of failed S3 uploads")
                .tag("service", "microservice-2")
                .tag("status", "failure")
                .register(registry);
    }

    @Bean
    public Timer messageProcessingTimer(MeterRegistry registry) {
        return Timer.builder("sqs.message.processing.duration")
                .description("Time taken to process SQS messages")
                .tag("service", "microservice-2")
                .register(registry);
    }

    @Bean
    public Timer s3UploadTimer(MeterRegistry registry) {
        return Timer.builder("s3.upload.duration")
                .description("Time taken to upload files to S3")
                .tag("service", "microservice-2")
                .register(registry);
    }

    @Bean
    public DistributionSummary s3FileSizeSummary(MeterRegistry registry) {
        return DistributionSummary.builder("s3.upload.file.size")
                .description("Size of files uploaded to S3 in bytes")
                .tag("service", "microservice-2")
                .baseUnit("bytes")
                .register(registry);
    }
}

