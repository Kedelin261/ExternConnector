package com.externconnector.sync.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${BASE_URL:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ExternConnector — Linear ↔ ClickUp Sync")
                        .description("Production-grade 2-way synchronization middleware between Linear and ClickUp")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ExternConnector")
                                .url("https://github.com/ExternConnector")))
                .servers(List.of(new Server().url(baseUrl).description("Active server")));
    }
}
