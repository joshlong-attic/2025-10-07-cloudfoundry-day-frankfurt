package com.example.web;

import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.util.Map;

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
    ApplicationRunner runner (
            OpenAiChatProperties openAiChatProperties,
            DataSourceProperties dataSourceProperties ,
            Environment environment) {
        return args -> {
            System.out.println("===============");
            System.out.println(dataSourceProperties.determineUsername() + '/' + dataSourceProperties.determinePassword() + '/' + dataSourceProperties.determineUrl() + '/' +
                    dataSourceProperties.determineDriverClassName());
            System.out.println(openAiChatProperties.getCompletionsPath() +'/' + openAiChatProperties.getApiKey() + '/'+
                    openAiChatProperties.getBaseUrl() );
            System.out.println(environment.getProperty("VCAP_SERVICES"));
            System.out.println(environment.getProperty("VCAP_APPLICATION"));
        };
    }

}
