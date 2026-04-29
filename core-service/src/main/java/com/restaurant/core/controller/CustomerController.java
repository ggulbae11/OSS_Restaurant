package com.restaurant.core.controller;

import com.restaurant.core.domain.Category;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// ════════════════════════════════════════════════════
// 카테고리 & 메뉴 (공개)
// ════════════════════════════════════════════════════
@RestController
@RequiredArgsConstructor
@RequestMapping
@Tag(name = "메뉴", description = "카테고리 및 메뉴 조회 (인증 불필요)")
class MenuController {

    private final MenuService menuService;

    @Operation(summary = "카테고리 목록 조회")
    @ApiResponse(responseCode = "200", description = "카테고리 목록 반환")
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getCategories() {
        return ResponseEntity.ok(menuService.getCategories());
    }

    @Operation(summary = "메뉴 목록 조회", description = "categoryId를 지정하면 해당 카테고리 메뉴만 반환합니다.")
    @ApiResponse(responseCode = "200", description = "메뉴 목록 반환")
    @GetMapping("/menus")
    public ResponseEntity<List<MenuResponse>> getMenus(
            @Parameter(description = "카테고리 ID (생략 시 전체)", example = "1")
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(menuService.getMenus(categoryId));
    }

    @Operation(summary = "메뉴 단건 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메뉴 정보 반환"),
            @ApiResponse(responseCode = "404", description = "메뉴 없음")
    })
    @GetMapping("/menus/{id}")
    public ResponseEntity<MenuResponse> getMenu(
            @Parameter(description = "메뉴 ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(menuService.getMenu(id));
    }
}

// ════════════════════════════════════════════════════
// 주문 (인증 필요)
// ════════════════════════════════════════════════════
@RestController
@RequiredArgsConstructor
@RequestMapping("/orders")
@Tag(name = "주문", description = "주문 생성 및 조회 (CUSTOMER 인증 필요)")
@SecurityRequirement(name = "bearerAuth")
class OrderController {

    private final OrderService orderService;

    @Operation(summary = "주문 생성", description = "장바구니 아이템을 주문으로 등록합니다. AI가 예상 대기 시간을 자동 계산합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "주문 생성 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음 또는 만료")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = """
                    {
                      "items": [
                        {"menuId": 1, "quantity": 1, "options": []},
                        {"menuId": 12, "quantity": 2, "options": ["아이스"]}
                      ],
                      "requestNote": "빠른 조리 부탁드려요"
                    }
                    """))
    )
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest req,
            HttpServletRequest httpReq) {
        Long customerId = extractUserId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.createOrder(req, customerId));
    }

    @Operation(summary = "주문 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "주문 정보 반환"),
            @ApiResponse(responseCode = "404", description = "주문 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @Parameter(description = "주문 ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @Operation(summary = "내 주문 목록 조회", description = "로그인한 고객의 전체 주문 이력을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "주문 목록 반환")
    @GetMapping("/my")
    public ResponseEntity<List<OrderResponse>> getMyOrders(HttpServletRequest httpReq) {
        Long customerId = extractUserId(httpReq);
        return ResponseEntity.ok(orderService.getMyOrders(customerId));
    }

    private Long extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new IllegalStateException("인증 정보가 없습니다.");
        return ((Number) id).longValue();
    }
}

// ════════════════════════════════════════════════════
// 리뷰 (공개 조회 / 인증 작성)
// ════════════════════════════════════════════════════
@RestController
@RequiredArgsConstructor
@RequestMapping("/reviews")
@Tag(name = "리뷰", description = "리뷰 조회(공개) / 리뷰 작성(CUSTOMER 인증 필요)")
class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "리뷰 작성",
            description = "리뷰 제출 시 AI가 자동으로 내용을 검열합니다. " +
                          "BLOCKED 판정을 받은 리뷰는 관리자 승인 전까지 노출되지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "리뷰 등록 성공"),
            @ApiResponse(responseCode = "400", description = "유효성 검사 실패 (rating 1~5, content 최대 500자)"),
            @ApiResponse(responseCode = "401", description = "인증 토큰 없음")
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(examples = @ExampleObject(value = """
                    {"menuId": 1, "rating": 5, "content": "김치찌개가 정말 맛있어요!"}
                    """))
    )
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(
            @Valid @RequestBody CreateReviewRequest req,
            HttpServletRequest httpReq) {
        Long customerId = (Long) httpReq.getAttribute("userId");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.createReview(req, customerId));
    }

    @Operation(summary = "메뉴별 리뷰 조회", description = "NORMAL 상태의 리뷰만 반환합니다.")
    @ApiResponse(responseCode = "200", description = "리뷰 목록 반환")
    @GetMapping
    public ResponseEntity<List<ReviewResponse>> getReviews(
            @Parameter(description = "메뉴 ID", required = true, example = "1")
            @RequestParam Long menuId) {
        return ResponseEntity.ok(reviewService.getReviewsByMenu(menuId));
    }
}
