package com.harmoni.pos.customer.config;

import io.netty.channel.ChannelOption;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.web.client.RestClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Spring AI wiring: the ChatClient with system prompt, in-memory chat memory (20 messages), the three tool groups (Menu, Cart, Order), plus HttpClient/RestClient beans with long timeouts so local model cold starts are not cut off.
 */
@Configuration
public class AiConfig {

    // Keeps the last 20 messages per session so the AI remembers the conversation.
    @Bean
    public MessageWindowChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    // The main AI client — wires up the system prompt, chat memory and the three tool groups (menu, cart, order).
    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel,
                                 MessageWindowChatMemory chatMemory,
                                 AiProperties aiProps,
                                 com.harmoni.pos.customer.ai.tool.MenuTools menuTools,
                                 com.harmoni.pos.customer.ai.tool.CartTools cartTools,
                                 com.harmoni.pos.customer.ai.tool.OrderTools orderTools) {
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(aiProps.getSystemPrompt())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(menuTools, cartTools, orderTools)
                .build();
    }

    // Longer Netty timeout — cold starts for local models can take a minute, we don't want to cut them off.
    @Bean
    public ReactorResourceFactory reactorResourceFactory() {
        ReactorResourceFactory factory = new ReactorResourceFactory();
        factory.setUseGlobalResources(false);
        return factory;
    }

    @Bean
    public HttpClient httpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
                .responseTimeout(Duration.ofSeconds(180));
    }

    // RestClient uses JDK HttpClient under the hood — bump the read timeout so tool calls have time to finish.
    @Bean
    public RestClient.Builder restClientBuilder() {
        java.net.http.HttpClient jdkHttpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkHttpClient);
        factory.setReadTimeout(Duration.ofSeconds(180));
        return RestClient.builder().requestFactory(factory);
    }
}
