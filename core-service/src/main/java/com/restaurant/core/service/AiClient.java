package com.restaurant.core.service;

import com.restaurant.core.config.ServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * core-service → ai-service 호출 클라이언트.
 * AI 서비스가 응답 실패해도 주문 흐름은 중단되지 않도록 fallback 처리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestTemplate restTemplate;
    private final ServiceProperties props;

    // ── 예상 조리 시간 예측 ─────────────────────────────────────────────
    public Integer predictTime(long activeOrderCount, int avgCookTime, int capacity) {
        try {
            Map<String, Object> body = Map.of(
                    "activeOrderCount", activeOrderCount,
                    "avgCookTime",      avgCookTime,
                    "capacity",         capacity
            );
            ResponseEntity<Map> resp = post("/ai/time/predict", body);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object t = resp.getBody().get("estimatedTime");
                return t instanceof Number n ? n.intValue() : null;
            }
        } catch (Exception e) {
            log.warn("AI 시간 예측 실패 (fallback): {}", e.getMessage());
        }
        // fallback: 공식 계산
        if (capacity > 0) {
            return (int) Math.ceil((double) activeOrderCount * avgCookTime / capacity);
        }
        return null;
    }

    // ── 리뷰 검열 ─────────────────────────────────────────────────────
    public Map<String, Object> moderateReview(String content) {
        try {
            Map<String, Object> body = Map.of("content", content);
            ResponseEntity<Map> resp = post("/ai/review/moderate", body);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                return resp.getBody();
            }
        } catch (Exception e) {
            log.warn("AI 리뷰 검열 실패 (fallback=NORMAL): {}", e.getMessage());
        }
        return Map.of("status", "NORMAL", "reason", "", "score", 0.0);
    }

    // ── 신메뉴 추천 ───────────────────────────────────────────────────
    public Map<String, Object> suggestNewMenu(Object salesData) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("salesData", salesData != null ? salesData : "");
            ResponseEntity<Map> resp = post("/ai/menu/new", body);
            if (resp.getStatusCode().is2xxSuccessful()) return resp.getBody();
        } catch (Exception e) {
            log.warn("AI 신메뉴 추천 실패: {}", e.getMessage());
        }
        return Map.of("suggestion", "데이터 부족으로 추천 불가");
    }

    // ── 조리 순서 최적화 ──────────────────────────────────────────────
    public Map<String, Object> optimizeCookSequence(Object items) {
        try {
            ResponseEntity<Map> resp = post("/ai/cook/sequence", Map.of("items", items));
            if (resp.getStatusCode().is2xxSuccessful()) return resp.getBody();
        } catch (Exception e) {
            log.warn("AI 조리 순서 최적화 실패: {}", e.getMessage());
        }
        return Map.of("order", items, "reason", "AI 서비스 연결 실패");
    }

    // ── 공통 POST ─────────────────────────────────────────────────────
    private ResponseEntity<Map> post(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForEntity(props.getAiUrl() + path, entity, Map.class);
    }
}
