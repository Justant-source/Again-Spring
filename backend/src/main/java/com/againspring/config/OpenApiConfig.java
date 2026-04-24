package com.againspring.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Again Spring API")
                .version("0.1.0")
                .description("다시봄: AI-mediated relationship conflict resolution")
                .contact(new Contact()
                    .name("Again Spring Team")
                    .url("https://again-spring.example.com")));
    }

}
