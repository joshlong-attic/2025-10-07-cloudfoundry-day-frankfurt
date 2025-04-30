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

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class AssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssistantApplication.class, args);
    }

    @Bean
    JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }

    @Bean
    ApplicationRunner vectorStoreInitializer(
            JdbcClient db,
            DogRepository repository,
            VectorStore vectorStore) {
        return args -> {

            var countOfVectorStoreRows =
                    db.sql("select count(*) from vector_store").query(Integer.class).single();

            if (countOfVectorStoreRows != 0)
                return;

            repository.findAll().forEach(dog -> {
                var dogument = new Document("id: %s, name:%s, description: %s"
                        .formatted(dog.id(), dog.name(), dog.description()));
                vectorStore.add(List.of(dogument));
            });
        };
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return new QuestionAnswerAdvisor(vectorStore);
    }

    @Bean
    McpSyncClient mcpSyncClient(@Value("${ADOPTIONS_SCHEDULER_HOST:localhost:8081}") String host) {
        var transport = HttpClientSseClientTransport.builder("http://" + host).build();
        var mcp = McpClient.sync(transport).build();
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
class AdoptionsAssistantController {

    static final String GLOBAL_PROMPT =
            """
                    You are an AI powered assistant to help people adopt a dog from the adoption\s
                    agency named Pooch Palace with locations in Antwerp, Seoul, Tokyo, Singapore, Paris,\s
                    Mumbai, New Delhi, Barcelona, San Francisco, and London. Information about the dogs available\s
                    will be presented below. If there is no information, then return a polite response suggesting we\s
                    don't have any dogs available.
                    """;
    private final ChatClient router;
    private final Map<String, PromptChatMemoryAdvisor> memory = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> delegates;
    private final DefaultListableBeanFactory beanFactory;

    AdoptionsAssistantController(
            Map<String, ChatClient> chatClientMap,
            DefaultListableBeanFactory beanFactory,
            ChatClient.Builder builder,
            QuestionAnswerAdvisor advisor) {

        this.delegates = chatClientMap;
        this.beanFactory = beanFactory;
        var system = """
                You are a router. When a request comes in, inspect the request and determine which of the following categories best matches the nature of the request and then return  
                the category, and only the category, of the best match. Here is a description of each category and an explanation of why you might choose that category.
                """;
        for (var beanName : chatClientMap.keySet()) {
            system += "\n\n" + beanName + ": " + descriptionForChatClient(beanName) + "\n\n";
        }


        this.router = builder
                .defaultAdvisors(advisor)
                .defaultSystem(system)
                .build();
    }

    private String descriptionForChatClient(String beanName) {
        var beanDefinition = beanFactory.getBeanDefinition(beanName);
        return beanDefinition.getDescription();
    }

    @GetMapping("/{user}/inquire")
    String inquire(@PathVariable String user, @RequestParam String question) {

        var memoryAdvisor = this.memory
                .computeIfAbsent(user, u -> PromptChatMemoryAdvisor.builder(new InMemoryChatMemory())
                        .build());

        var resolvedChatClient = this.router
                .prompt()
                .user(question)
                .call()
                .content();

        return delegates
                .get(resolvedChatClient)
                .prompt()
                .advisors(memoryAdvisor)
                .user(question)
                .system(GLOBAL_PROMPT + descriptionForChatClient(resolvedChatClient))
                .call()
                .content();
    }
}

@Configuration
class ChatClientsConfiguration {

    @Bean
    @Description("""
            Handle all requests for our post adoption care. 
            This might include a range of activities like training, therapy, dog obedience training, etc. 
            We provide free training at our locations Monday to Friday and Saturdays before noon.  
            
            """)
    ChatClient postAdoptionFollowupCare(QuestionAnswerAdvisor advisor, ChatClient.Builder builder) {
        return builder
                .defaultSystem(AdoptionsAssistantController.GLOBAL_PROMPT)
                .defaultAdvisors(advisor)
                .build();
    }

    @Description("""
            This chat client should be used to handle inquiries about adopting dogs and scheduling adoptions. 
            It has access to the scheduling subsystem via tools. This tool should be used to determine when a 
            dog might be picked up or adopted from a given Pooch Palace location
            """)
    @Bean
    ChatClient adoptionsAndScheduling(QuestionAnswerAdvisor advisor, ChatClient.Builder builder, McpSyncClient client) {
        return builder
                .defaultSystem(AdoptionsAssistantController.GLOBAL_PROMPT)
                .defaultTools(new SyncMcpToolCallbackProvider(client))
                .defaultAdvisors(advisor)
                .build();
    }
}
