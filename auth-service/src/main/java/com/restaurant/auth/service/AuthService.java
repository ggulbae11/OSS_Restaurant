package com.restaurant.auth.service;

import com.restaurant.auth.domain.User;
import com.restaurant.auth.domain.User.Role;
import com.restaurant.auth.dto.*;
import com.restaurant.auth.repository.UserRepository;
import com.restaurant.auth.security.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redis;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    // ──── 회원가입 ────────────────────────────────────────────
    public void register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        User user = User.builder()
                .username(req.username())
                .password(passwordEncoder.encode(req.password()))
                .role(req.role() != null ? req.role() : Role.CUSTOMER)
                .build();
        userRepository.save(user);
    }

    // ──── 로그인 ────────────────────────────────────────────
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다."));

        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다.");
        }
        return new TokenResponse(jwtProvider.generate(user));
    }

    // ──── 토큰 검증 (다른 서비스가 내부적으로 호출) ──────────────
    public Map<String, Object> verify(String bearerToken) {
        String token = stripBearer(bearerToken);

        if (isBlacklisted(token)) {
            throw new IllegalStateException("로그아웃된 토큰입니다.");
        }

        Claims claims = jwtProvider.parse(token);
        return Map.of(
                "username", claims.getSubject(),
                "role",     claims.get("role", String.class),
                "userId",   claims.get("userId", Long.class)
        );
    }

    // ──── 로그아웃 (토큰 블랙리스트) ─────────────────────────
    public void logout(String bearerToken) {
        String token = stripBearer(bearerToken);
        Claims claims = jwtProvider.parse(token);
        long ttl = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (ttl > 0) {
            redis.opsForValue().set(BLACKLIST_PREFIX + token, "1", Duration.ofMillis(ttl));
        }
    }

    // ──── helpers ──────────────────────────────────────────
    private String stripBearer(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        throw new IllegalArgumentException("Authorization 헤더 형식이 잘못됐습니다.");
    }

    private boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + token));
    }
}
