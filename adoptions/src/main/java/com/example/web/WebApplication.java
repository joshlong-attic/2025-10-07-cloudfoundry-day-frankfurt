package com.example.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Controller
@ResponseBody
@SpringBootApplication
public class WebApplication {

    @GetMapping("/")
    Map<String, Object> home() {
        return Map.of("message", "hello CloudFoundry");
    }

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ApplicationRunner runner(
            ChatClient ai,
            EmbeddingModel em,
            OpenAiChatProperties openAiChatProperties,
            DataSourceProperties dataSourceProperties,
            Environment environment) {
        return args -> {


            System.out.println("===============");
            var d = " /// ";

            System.out.println(dataSourceProperties.determineUsername() + d +
                    dataSourceProperties.determinePassword() + d +
                    dataSourceProperties.determineUrl() + d +
                    dataSourceProperties.determineDriverClassName());

            System.out.println(openAiChatProperties.getCompletionsPath() + d +
                    openAiChatProperties.getApiKey() + d +
                    openAiChatProperties.getBaseUrl());

            System.out.println(environment.getProperty("VCAP_SERVICES"));

            System.out.println(environment.getProperty("VCAP_APPLICATION"));

            System.out.println("===============");

            var content = ai
                    .prompt()
                    .user("tell me a joke")
                    .call()
                    .content();
            System.out.println("content: [" + content + "]");
            System.out.println("===============");
            var result = em.call(new EmbeddingRequest(List.of("hello, world"),
                    null));

            for (var r : result.getResults())
                System.out.println("embedding result: [" + Arrays.toString(r.getOutput()) + "]");

            System.out.println("===============");

        };
    }

}


interface DogRepository extends ListCrudRepository<Dog, Integer> {
}


record Dog(@Id int id, String name, String owner, String description) {
}


@Controller
@ResponseBody
class SimpleAdoptionsController implements ApplicationRunner {

    private final ChatClient ai;

    private final Map<String, PromptChatMemoryAdvisor> memory = new ConcurrentHashMap<>();

    private final Runnable vectorizeRunnable;

    SimpleAdoptionsController(ChatClient.Builder ai, DogRepository repository, VectorStore vectorStore) {

        this.vectorizeRunnable = () -> repository.findAll().forEach(dog -> {
            var dogument = new Document("id: %s, name: %s, description: %s".formatted(
                    dog.id(), dog.name(), dog.description()
            ));
            vectorStore.add(List.of(dogument));
        });

        var system = """
                You are an AI powered assistant to help people adopt a dog from the adoption\s
                agency named Pooch Palace with locations in Austin, Seoul, Tokyo, Singapore, Paris,\s
                Mumbai, New Delhi, Barcelona, San Francisco, and London. Information about the dogs available\s
                will be presented below. If there is no information, then return a polite response suggesting we\s
                don't have any dogs available.
                """;

        this.ai = ai
                .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
                .defaultSystem(system)
                .build();
    }

    @GetMapping("/{user}/inquire")
    String inquire(@PathVariable String user, @RequestParam String question) {

        var advisor = this.memory
                .computeIfAbsent(user, u -> PromptChatMemoryAdvisor.builder(new InMemoryChatMemory())
                .build());
        return this.ai
                .prompt()
                .user(question)
                .advisors(advisor)
                .call()
                .content();

    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            ex.execute(this.vectorizeRunnable);
        }
    }
}









