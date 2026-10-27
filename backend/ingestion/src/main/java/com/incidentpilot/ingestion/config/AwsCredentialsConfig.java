package com.incidentpilot.ingestion.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@Configuration
public class AwsCredentialsConfig {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider(
            @Value("${aws.credentials.access-key-id:}") String accessKeyId,
            @Value("${aws.credentials.secret-access-key:}") String secretAccessKey) {

        if (!accessKeyId.isBlank() && !secretAccessKey.isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        }
        return DefaultCredentialsProvider.create();
    }
}
