package com.restaurant.core.controller;

import com.restaurant.core.domain.Review.ReviewStatus;
import com.restaurant.core.dto.*;
import com.restaurant.core.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "관리자", description = "메뉴·주문·리뷰 관리 및 AI 기능 (ADMIN 전용)")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final MenuService   menuService;
    private final OrderService  orderService;
    private final ReviewService reviewService;
    private final AiClient      aiClient;

    // ── 메뉴 관리 ─────────────────────────────────────────────────────

    @Operation(summary = "전체 메뉴 조회", description = "비활성화된 메뉴를 포함한 전체 목록을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "메뉴 목록 반환")
    @GetMapping("/menus")
    public ResponseEntity<List<MenuResponse>> getAllMenus() {
        return ResponseEntity.ok(menuService.getAllMenus());
    }

    @Operation(summary = "메뉴 등록",
            description = "메뉴 등록 시 AI가 자동으로 정책 검사(이름·가격·설명)를 수행합니다. " +
                          "정책 위반 시 등록이 거부됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "메뉴 등록 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 또는 AI 정책 위반"),
            @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = """
                    {
                      "name": "삼겹살 구이",
                      "price": 14000,
                      "spicyLevel": 1,
                      "cookTime": 18,
                      "imageUrl": "https://example.com/samgyup.jpg",
                      "categoryId": 1
                    }
                    """))
    )
    @PostMapping("/menus")
    public ResponseEntity<MenuResponse> createMenu(@Valid @RequestBody CreateMenuRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createMenu(req));
    }

    @Operation(summary = "메뉴 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "404", description = "메뉴 없음")
    })
    @PutMapping("/menus/{id}")
    public ResponseEntity<MenuResponse> updateMenu(
            @Parameter(description = "메뉴 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody CreateMenuRequest req) {
        return ResponseEntity.ok(menuService.updateMenu(id, req));
    }

    @Operation(summary = "메뉴 비활성화", description = "메뉴를 삭제하지 않고 판매 중지 상태로 변경합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "비활성화 성공"),
            @ApiResponse(responseCode = "404", description = "메뉴 없음")
    })
    @DeleteMapping("/menus/{id}")
    public ResponseEntity<Void> disableMenu(
            @Parameter(description = "메뉴 ID", example = "1") @PathVariable Long id) {
        menuService.disableMenu(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "메뉴 활성화", description = "비활성화된 메뉴를 다시 판매 가능 상태로 변경합니다.")
    @ApiResponse(responseCode = "204", description = "활성화 성공")
    @PatchMapping("/menus/{id}/enable")
    public ResponseEntity<Void> enableMenu(
            @Parameter(description = "메뉴 ID", example = "1") @PathVariable Long id) {
        menuService.enableMenu(id);
        return ResponseEntity.noContent().build();
    }

    // ── 주문 관리 ─────────────────────────────────────────────────────

    @Operation(summary = "전체 주문 조회")
    @ApiResponse(responseCode = "200", description = "주문 목록 반환")
    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @Operation(summary = "주문 상태 변경",
            description = """
                    주문 상태를 다음 단계로 변경합니다.

                    허용 상태값: `ACCEPTED` · `COOKING` · `READY` · `DELIVERING` · `COMPLETED` · `CANCELED`
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상태 변경 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 상태값"),
            @ApiResponse(responseCode = "404", description = "주문 없음")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = """
                    {"status": "COOKING"}
                    """))
    )
    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @Parameter(description = "주문 ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest req) {
        return ResponseEntity.ok(orderService.updateStatus(id, req));
    }

    // ── 리뷰 관리 ─────────────────────────────────────────────────────

    @Operation(summary = "전체 리뷰 조회", description = "BLOCKED·SUSPICIOUS 포함 모든 리뷰를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "리뷰 목록 반환")
    @GetMapping("/reviews")
    public ResponseEntity<List<ReviewResponse>> getAllReviews() {
        return ResponseEntity.ok(reviewService.getAllReviews());
    }

    @Operation(summary = "리뷰 상태 변경",
            description = "AI가 SUSPICIOUS으로 분류한 리뷰를 NORMAL 또는 BLOCKED로 최종 판정합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상태 변경 성공"),
            @ApiResponse(responseCode = "404", description = "리뷰 없음")
    })
    @PatchMapping("/reviews/{id}/status")
    public ResponseEntity<ReviewResponse> updateReviewStatus(
            @Parameter(description = "리뷰 ID", example = "1") @PathVariable Long id,
            @Parameter(description = "변경할 상태 (NORMAL | SUSPICIOUS | BLOCKED)", example = "NORMAL")
            @RequestParam ReviewStatus status) {
        return ResponseEntity.ok(reviewService.updateReviewStatus(id, status));
    }

    // ── AI 기능 ───────────────────────────────────────────────────────

    @Operation(summary = "AI 신메뉴 추천",
            description = "AI가 현재 매출 데이터를 분석해 새로운 메뉴 아이디어를 1건 제안합니다.")
    @ApiResponse(responseCode = "200", description = "신메뉴 추천 결과",
            content = @Content(examples = @ExampleObject(value = """
                    {
                      "name": "된장 크림 파스타",
                      "estimatedPrice": 13000,
                      "reason": "한식·양식 퓨전 트렌드에 맞고 기존 재료 재활용 가능",
                      "suggestion": "된장과 크림 소스를 결합한 퓨전 파스타..."
                    }
                    """)))
    @PostMapping("/ai/menu/suggest")
    public ResponseEntity<Map<String, Object>> suggestNewMenu() {
        return ResponseEntity.ok(aiClient.suggestNewMenu(""));
    }

    @Operation(summary = "AI 조리 순서 최적화",
            description = "LPT(Longest Processing Time) 알고리즘과 AI를 결합해 최적 조리 순서를 반환합니다.")
    @ApiResponse(responseCode = "200", description = "최적화된 조리 순서",
            content = @Content(examples = @ExampleObject(value = """
                    {
                      "order": [3, 1, 2],
                      "reason": "불고기(20분)를 먼저 시작하고, 김치찌개(15분), 된장찌개(12분) 순으로 조리합니다."
                    }
                    """)))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = """
                    {
                      "items": [
                        {"menuId": 1, "cookTime": 15, "menuName": "김치찌개"},
                        {"menuId": 2, "cookTime": 12, "menuName": "된장찌개"},
                        {"menuId": 3, "cookTime": 20, "menuName": "불고기"}
                      ]
                    }
                    """))
    )
    @PostMapping("/ai/cook/sequence")
    public ResponseEntity<Map<String, Object>> optimizeCookSequence(
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(aiClient.optimizeCookSequence(body.get("items")));
    }
}
