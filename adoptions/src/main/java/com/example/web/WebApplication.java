package com.example.web;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            EmbeddingModel em ,
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
            var result = em.call(new EmbeddingRequest(List.of("hello, world") ,
                    null ));

            for (var r  : result.getResults())
                System.out.println("embedding result: [" + Arrays.toString(r.getOutput())+ "]");

            System.out.println("===============");

        };
    }

}
