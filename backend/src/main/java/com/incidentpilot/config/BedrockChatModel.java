package com.incidentpilot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Spring AI {@link ChatModel} backed by the AWS Bedrock Converse API.
 *
 * <p>Spring AI 1.0.0's built-in {@code BedrockConverseProxyChatAutoConfiguration} fails
 * because it references {@code BedrockProxyChatModel}, a class that only exists in
 * Spring AI 2.0.0+. This class works around the packaging bug by calling the
 * Bedrock Converse API directly via the AWS SDK.
 *
 * <p>Not annotated as a Spring bean — instantiated by {@link BedrockConfig}.
 */
@Slf4j
public class BedrockChatModel implements ChatModel {

    private final BedrockRuntimeClient client;
    private final String modelId;

    public BedrockChatModel(BedrockRuntimeClient client, String modelId) {
        this.client = client;
        this.modelId = modelId;
    }

    /**
     * Translates a Spring AI {@link Prompt} into a Bedrock Converse API call.
     *
     * <p>Bedrock's Converse API requires system instructions and user/assistant
     * messages in separate parameters, so we split them here. If the prompt
     * contains no explicit user/assistant messages (e.g. a plain string call),
     * we wrap the raw content as a single USER message.
     */
    @Override
    public ChatResponse call(Prompt prompt) {
        List<SystemContentBlock> systemBlocks = new ArrayList<>();
        List<Message> messages = new ArrayList<>();

        prompt.getInstructions().forEach(msg -> {
            if (msg.getMessageType() == MessageType.SYSTEM) {
                systemBlocks.add(SystemContentBlock.builder().text(msg.getText()).build());
            } else {
                ConversationRole role = msg.getMessageType() == MessageType.ASSISTANT
                        ? ConversationRole.ASSISTANT
                        : ConversationRole.USER;
                messages.add(Message.builder()
                        .role(role)
                        .content(ContentBlock.fromText(msg.getText()))
                        .build());
            }
        });

        // Fallback: plain-string calls produce no typed messages
        if (messages.isEmpty()) {
            messages.add(Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.fromText(prompt.getContents()))
                    .build());
        }

        ConverseResponse response = client.converse(req -> req
                .modelId(modelId)
                .system(systemBlocks)
                .messages(messages));

        String text = response.output().message().content().stream()
                .filter(block -> block.text() != null)
                .map(ContentBlock::text)
                .findFirst()
                .orElse("");

        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
