package com.example.assistant;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class AssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return new QuestionAnswerAdvisor(vectorStore);
    }

    @Bean
    @Description("""
                This chat client can be used to handle all work with a given user to follow up on adoptions. 
                This might include a range of activities like scheduling training, therapy, dog obedience training, etc.         
            """)
    ChatClient postAdoptionFollowup(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    @Description("""
            This chat client should be used to handle scheduling adoptions. It has access to the scheduling subsystem via tools. This tool should be used to determine when a dog might be picked up or adopted from a given location.
            """)
    ChatClient adoptionScheduling(McpSyncClient mcpSyncClient, QuestionAnswerAdvisor advisor, ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(advisor)
                .defaultTools(new SyncMcpToolCallbackProvider(mcpSyncClient))
                .build();
    }

    @Bean
    @Description("""
            Answer questions for people who are looking to adopt a dog of their specification.
            """)
    ChatClient dogAdoption(QuestionAnswerAdvisor advisor, ChatClient.Builder builder) {
        return builder
                .defaultAdvisors(advisor)
                .build();
    }

    @Bean
    ApplicationRunner vectorStoreInitializer(
            JdbcClient db,
            VectorStore vectorStore,
            DogRepository repository) {
        return applicationArguments -> {

            if (db.sql(" select count( id ) from vector_store ").query(Integer.class).single().equals(0)) {
                repository.findAll().forEach(dog -> {
                    var dogument = new Document("id: %s, name: %s, description: %s"
                            .formatted(dog.id(), dog.name(), dog.description()));
                    vectorStore.add(List.of(dogument));
                });
            }
        };
    }
}

interface DogRepository extends ListCrudRepository<Dog, Integer> {
}

record Dog(@Id int id, String name, String owner, String description) {
}

@Controller
@ResponseBody
class AssistantController {


    private final Map<String, PromptChatMemoryAdvisor> advisors = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> routes;
    private final ChatClient router;

    AssistantController(
            ChatClient.Builder builder,
            DefaultListableBeanFactory beans,
            Map<String, ChatClient> routes) {

        var system =
                """
                        
                        You are an assistant to help people adopt adopt a dog from the adoption agency named "Pooch Palace," 
                        with locations in Palo Alto, Seoul, Tokyo, Singapore, Paris, Mumbai, New Delhi, Barcelona, 
                        San Francisco, and London. 
                        
                        You are also a router. When a request comes in, inspect the request and determine which of the 
                        following categories best matches the nature of the request and then return the category, and only the 
                        category, of the best match. Here is a description of each category and an explanation of why you might 
                        choose that category.
                        
                        """;

        this.routes = routes;

        for (var category : routes.keySet()) {
            var description = beans.getBeanDefinition(category).getDescription();
            system = system + category + " - " + description + System.lineSeparator() + System.lineSeparator();
        }

        this.router = builder
                .defaultSystem(system)
                .build();
    }


    @GetMapping("/{user}/inquire")
    String inquire(@PathVariable String user, @RequestParam String question) {
        var advisor = this.advisors.computeIfAbsent(user, x -> PromptChatMemoryAdvisor.builder(new InMemoryChatMemory()).build());

        var routerSelectedCategory = this.router
                .prompt()
                .advisors(advisor)
                .user(question)
                .call()
                .content();

        var delegateChatClient = this.routes
                .get(routerSelectedCategory);

        return delegateChatClient
                .prompt()
                .user(question)
                .advisors(advisor)
                .call()
                .content();

    }
}

@Configuration
class McpClientConfiguration {

    @Bean
    McpSyncClient mcpSyncClient(@Value("${ADOPTIONS_SCHEDULER_HOST:localhost:8081}") String host) {
        var mcp = McpClient.sync(HttpClientSseClientTransport.builder("http://" + host)
                        .build())
                .build();
        mcp.initialize();
        return mcp;
    }
}