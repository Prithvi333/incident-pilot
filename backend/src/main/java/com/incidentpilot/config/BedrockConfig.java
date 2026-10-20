package com.incidentpilot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Creates the AWS Bedrock chat bean that Spring AI 1.0.0's auto-config cannot provide.
 *
 * <p>Embedding is handled by Spring AI's Titan auto-config. Uses the shared
 * {@link AwsCredentialsProvider} from {@link AwsCredentialsConfig}.
 *
 * @see BedrockChatModel
 */
@Configuration
@Slf4j
public class BedrockConfig {

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient(
            AwsCredentialsProvider awsCredentialsProvider,
            @Value("${spring.ai.bedrock.aws.region:ap-south-1}") String region) {
        log.info("Creating BedrockRuntimeClient in region {}", region);
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    @Bean
    public BedrockChatModel bedrockChatModel(
            BedrockRuntimeClient client,
            @Value("${spring.ai.bedrock.converse.chat.options.model}") String modelId) {
        log.info("Creating BedrockChatModel with model {}", modelId);
        return new BedrockChatModel(client, modelId);
    }

}
