package com.restaurant.auth.controller;

import com.restaurant.auth.dto.*;
import com.restaurant.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입 · 로그인 · 로그아웃 · 토큰 검증")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "username, password, role(CUSTOMER|ADMIN|RIDER)을 받아 계정을 생성합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "가입 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 (username 3자 이상, password 8자 이상)"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 username")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = """
                    {"username":"newuser","password":"pass1234","role":"CUSTOMER"}
                    """))
    )
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "로그인", description = "인증 성공 시 JWT accessToken을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(schema = @Schema(implementation = TokenResponse.class),
                            examples = @ExampleObject(value = """
                                    {"accessToken":"eyJhbGciOiJIUzI1NiJ9..."}
                                    """))),
            @ApiResponse(responseCode = "401", description = "비밀번호 불일치"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 username")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = """
                    {"username":"customer1","password":"password123"}
                    """))
    )
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @Operation(summary = "로그아웃", description = "현재 토큰을 블랙리스트에 등록합니다. 이후 해당 토큰은 사용 불가.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "400", description = "Authorization 헤더 누락")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Parameter(description = "Bearer {accessToken}", required = true, example = "Bearer eyJhbGci...")
            @RequestHeader("Authorization") String authorization) {
        authService.logout(authorization);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "토큰 검증 (내부 전용)",
            description = "다른 마이크로서비스가 JWT 유효성을 확인하기 위해 호출하는 내부 엔드포인트. " +
                          "userId, username, role을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 유효",
                    content = @Content(examples = @ExampleObject(value = """
                            {"userId":1,"username":"customer1","role":"CUSTOMER"}
                            """))),
            @ApiResponse(responseCode = "401", description = "토큰 만료 또는 블랙리스트 처리됨")
    })
    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(
            @Parameter(description = "Bearer {accessToken}", required = true)
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(authService.verify(authorization));
    }
}
