package com.restaurant.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI coreOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Core Service API")
                        .description("""
                                메뉴 조회, 주문 생성, 리뷰 작성 등 식당 핵심 비즈니스 API.

                                **인증이 필요한 엔드포인트**는 우측 자물쇠 아이콘을 클릭하여
                                `Bearer <JWT 토큰>` 형식으로 입력하세요.

                                토큰은 Auth Service의 `POST /auth/login`으로 발급받을 수 있습니다.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Restaurant OSS").url("http://localhost:80")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("직접 접근"),
                        new Server().url("http://localhost:80").description("API 게이트웨이")
                ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME,
                                new SecurityScheme()
                                        .name(BEARER_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("로그인 후 발급된 JWT 토큰을 입력하세요.")));
    }
}
