package com.restaurant.core.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 모든 요청에서 Authorization 헤더를 꺼내 auth-service /verify 로 검증한다.
 * 검증 성공 시 SecurityContext에 Authentication을 설정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final RestTemplate restTemplate;
    private final ServiceProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<Map> resp = restTemplate.exchange(
                        props.getAuthUrl() + "/verify",
                        HttpMethod.GET,
                        entity,
                        Map.class
                );

                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    Map<?, ?> body   = resp.getBody();
                    String username  = (String) body.get("username");
                    String role      = (String) body.get("role");
                    Long   userId    = ((Number) body.get("userId")).longValue();

                    // 요청 속성에 userId 보관 (컨트롤러에서 사용)
                    request.setAttribute("userId", userId);
                    request.setAttribute("username", username);

                    var auth = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                log.debug("JWT 검증 실패: {}", e.getMessage());
                // 인증 실패는 SecurityContext를 비워두고 계속 진행 → 인가 레이어에서 처리
            }
        }

        chain.doFilter(request, response);
    }
}
