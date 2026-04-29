package com.restaurant.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Auth Service API")
                        .description("JWT 기반 회원가입 / 로그인 / 토큰 검증 서비스")
                        .version("1.0.0")
                        .contact(new Contact().name("Restaurant OSS").url("http://localhost:80")))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("직접 접근"),
                        new Server().url("http://localhost:80/auth").description("API 게이트웨이")
                ));
    }
}
