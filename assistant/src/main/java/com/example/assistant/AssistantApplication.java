package com.example.assistant;


import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class AssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }

    @Bean
    ApplicationRunner genAiEnvRunner  (Environment environment) {
        return args -> {
            var properties  = new ArrayList< String>() ;
            properties.add("spring.ai.openai.chat.base-url");
            properties.add ("spring.ai.openai.chat.api-key");
            properties.add ("spring.ai.openai.chat.options.model");
            for (var property : properties)
                System.out.println(property + " = " + environment.getProperty(property));
            System.out.println("Running with " + environment);
        };
    }

    @Bean
    McpSyncClient schedulerMcpClient(
            @Value("${ADOPTIONS_SCHEDULER_HOST:http://localhost:8081}") String host) {
        System.out.println("Connecting to " + host);
        var mcp = McpClient
                .sync(HttpClientSseClientTransport.builder(host).build())
                .build();
        mcp.initialize();
        return mcp;
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore dataSource) {
        return new QuestionAnswerAdvisor(dataSource);
    }

    @Bean
    PromptChatMemoryAdvisor promptChatMemoryAdvisor(DataSource dataSource) {
        var jdbc = JdbcChatMemoryRepository
                .builder()
                .dataSource(dataSource)
                .build();
        var mwa = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(jdbc)
                .build();
        return PromptChatMemoryAdvisor
                .builder(mwa)
                .build();
    }
}

@Controller
@ResponseBody
class AssistantController {

    private final ChatClient ai;

    AssistantController(ChatClient.Builder ai,
                        McpSyncClient schedulerMcpClient,
                        QuestionAnswerAdvisor questionAnswerAdvisor,
                        PromptChatMemoryAdvisor promptChatMemoryAdvisor) {
        var prompt = """
                You are an AI powered assistant to help people adopt a dog from the adoption\s
                agency named Pooch Palace with locations in Oslo, Seoul, Denver, Tokyo, Singapore, Paris,\s
                Mumbai, New Delhi, Barcelona, San Francisco, and London. Information about the dogs available\s
                will be presented below. If there is no information, then return a polite response suggesting we\s
                don't have any dogs available.
                """;
        this.ai = ai
                .defaultSystem(prompt)
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(schedulerMcpClient))
                .defaultAdvisors(promptChatMemoryAdvisor, questionAnswerAdvisor)
                .build();
    }

    @GetMapping("/ask")
    Map<String, String> question(Principal principal, @RequestParam String question) {
        return Map.of("reply", this.ai
                .prompt(question)
                .call()
                .content());
    }
}
