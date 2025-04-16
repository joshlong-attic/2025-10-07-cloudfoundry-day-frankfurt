package com.example.adoptions;

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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;


@SpringBootApplication
public class AdoptionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdoptionsApplication.class, args);
    }

    @Bean
    ChatClient chatClient(
            ChatClient.Builder ai,
            DogRepository repository,
            VectorStore vectorStore,
            JdbcClient db,
            McpSyncClient mcpSyncClient) {

        var vectorizeRunnable = (Runnable) () -> {
            var needsInit = db.sql("select count(id) from vector_store").query(Integer.class).single().equals(0);
            if (needsInit) {
                repository.findAll().forEach(dog -> {
                    var dogument = new Document("id: %s, name: %s, description: %s".formatted(
                            dog.id(), dog.name(), dog.description()
                    ));
                    vectorStore.add(List.of(dogument));
                });
                System.out.println("finished inserting doguments!");
            }
        };

        var system = """
                You are an AI powered assistant to help people adopt a dog from the adoption\s
                agency named Pooch Palace with locations in Austin, Seoul, Tokyo, Singapore, Paris,\s
                Mumbai, New Delhi, Barcelona, San Francisco, and London. Information about the dogs available\s
                will be presented below. If there is no information, then return a polite response suggesting we\s
                don't have any dogs available.
                """;

        return ai
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                .defaultSystem(system)
                .defaultTools(new SyncMcpToolCallbackProvider(mcpSyncClient))
                .build();

    }
    /*   @Override
            public void run(ApplicationArguments args) throws Exception {
                try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
                    ex.execute(this.vectorizeRunnable);
                }
            }*/
    @Bean
    McpSyncClient myMcpSyncClient() {
        var mcp = McpClient
                .sync(HttpClientSseClientTransport.builder("http://localhost:8081").build())
                .build();
        mcp.initialize();
        return mcp;
    }
}


interface DogRepository extends ListCrudRepository<Dog, Integer> {
}


record Dog(@Id int id, String name, String owner, String description) {
}


@Controller
@ResponseBody
class SimpleAdoptionsController {

    private final Map<String, PromptChatMemoryAdvisor> memory = new ConcurrentHashMap<>();
    private final DogRepository dogRepository;
    private final ChatClient chatClient;

    SimpleAdoptionsController(DogRepository repository, ChatClient chatClient) {
        this.dogRepository = repository;
        this.chatClient = chatClient;
    }

    @GetMapping("/dogs")
    Collection<Dog> all() {
        return dogRepository.findAll();
    }

    @GetMapping("/{user}/inquire")
    String inquire(@PathVariable String user, @RequestParam String question) {

        var advisor = this.memory
                .computeIfAbsent(user, u -> PromptChatMemoryAdvisor.builder(new InMemoryChatMemory()).build());
        return chatClient
                .prompt()
                .user(question)
                .advisors(advisor)
                .call()
                .content();
    }


}









