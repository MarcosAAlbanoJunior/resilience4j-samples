package com.malbano.resilience4j.samples.commum.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Resilience4j Samples API")
                        .version("1.0.0")
                        .description("API de exemplos com Resilience4j - Retry, Circuit Breaker e Rate Limiter")
                        .contact(new Contact()
                                .name("Marcos A. Albano Junior")
                                .url("https://github.com/MarcosAAlbanoJunior"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8085")
                                .description("Servidor de Desenvolvimento")
                ));
    }
}