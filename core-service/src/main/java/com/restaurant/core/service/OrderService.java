package com.restaurant.core.service;

import com.restaurant.core.domain.*;
import com.restaurant.core.domain.Order.OrderStatus;
import com.restaurant.core.domain.Rider.RiderStatus;
import com.restaurant.core.dto.*;
import com.restaurant.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalDouble;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository         orderRepository;
    private final MenuRepository          menuRepository;
    private final MenuIngredientRepository menuIngredientRepository;
    private final IngredientRepository    ingredientRepository;
    private final RiderRepository         riderRepository;
    private final AiClient                aiClient;

    // ── 주문 생성 ─────────────────────────────────────────────────────
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req, Long customerId) {

        Order order = Order.builder()
                .customerId(customerId)
                .status(OrderStatus.CREATED)
                .requestNote(req.requestNote())
                .build();

        int totalPrice = 0;

        for (CreateOrderRequest.OrderItemRequest itemReq : req.items()) {
            Menu menu = menuRepository.findById(itemReq.menuId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "메뉴를 찾을 수 없습니다: " + itemReq.menuId()));

            if (!menu.isAvailable()) {
                throw new IllegalStateException("현재 판매 중이 아닌 메뉴입니다: " + menu.getName());
            }

            // 재고 차감
            deductIngredients(menu, itemReq.quantity());

            OrderItem item = OrderItem.builder()
                    .order(order)
                    .menuId(menu.getId())
                    .menuName(menu.getName())
                    .unitPrice(menu.getPrice())
                    .quantity(itemReq.quantity())
                    .options(itemReq.options() != null
                            ? String.join(",", itemReq.options()) : "")
                    .cookTime(menu.getCookTime())
                    .build();

            order.getItems().add(item);
            totalPrice += menu.getPrice() * itemReq.quantity();
        }

        order.setTotalPrice(totalPrice);

        // AI 예상 시간 계산
        long activeOrders = orderRepository.countActiveOrders();
        OptionalDouble avgCook = order.getItems().stream()
                .mapToInt(i -> menuRepository.findById(i.getMenuId())
                        .map(Menu::getCookTime).orElse(10))
                .average();
        Integer estimatedTime = aiClient.predictTime(
                activeOrders,
                (int) avgCook.orElse(10),
                3   // 동시 조리 가능 수 (설정값으로 추출 가능)
        );
        order.setEstimatedTime(estimatedTime);

        return OrderResponse.from(orderRepository.save(order));
    }

    // ── 주문 단건 조회 ────────────────────────────────────────────────
    public OrderResponse getOrder(Long orderId) {
        return OrderResponse.from(findOrThrow(orderId));
    }

    // ── 내 주문 목록 ─────────────────────────────────────────────────
    public List<OrderResponse> getMyOrders(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream().map(OrderResponse::from).toList();
    }

    // ── 전체 주문 목록 (관리자) ───────────────────────────────────────
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream().map(OrderResponse::from).toList();
    }

    // ── 주문 상태 변경 (관리자) ───────────────────────────────────────
    @Transactional(readOnly = false)
    public OrderResponse updateStatus(Long orderId, UpdateStatusRequest req) {
        Order order = findOrThrow(orderId);
        validateTransition(order.getStatus(), req.status());

        order.setStatus(req.status());

        // READY → DELIVERING 시 배달기사 자동 매칭
        if (req.status() == OrderStatus.DELIVERING) {
            matchRider(order);
        }

        return OrderResponse.from(order);
    }

    // ── 재고 차감 로직 ────────────────────────────────────────────────
    private void deductIngredients(Menu menu, int quantity) {
        List<MenuIngredient> mis = menuIngredientRepository.findByMenu_Id(menu.getId());
        for (MenuIngredient mi : mis) {
            Ingredient ingredient = mi.getIngredient();
            double required = mi.getRequiredAmount() * quantity;
            if (ingredient.getStock() < required) {
                throw new IllegalStateException(
                        "재고 부족: " + ingredient.getName() +
                        " (필요: " + required + ", 재고: " + ingredient.getStock() + ")");
            }
            ingredient.setStock(ingredient.getStock() - required);
        }
    }

    // ── 배달기사 매칭 알고리즘 ────────────────────────────────────────
    private void matchRider(Order order) {
        Rider rider = riderRepository.findFirstAvailableRider()
                .orElseThrow(() -> new IllegalStateException("현재 가용한 배달기사가 없습니다."));

        rider.setStatus(RiderStatus.DELIVERING);
        rider.setTotalDeliveryCount(rider.getTotalDeliveryCount() + 1);
        rider.setLastAssignedAt(LocalDateTime.now());
        riderRepository.save(rider);

        log.info("주문 {} → 라이더 {} 매칭 완료", order.getId(), rider.getName());
    }

    // ── 상태 전이 유효성 검사 ─────────────────────────────────────────
    private void validateTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case CREATED    -> next == OrderStatus.ACCEPTED || next == OrderStatus.CANCELED;
            case ACCEPTED   -> next == OrderStatus.COOKING  || next == OrderStatus.CANCELED;
            case COOKING    -> next == OrderStatus.READY;
            case READY      -> next == OrderStatus.DELIVERING;
            case DELIVERING -> next == OrderStatus.COMPLETED;
            default         -> false;
        };
        if (!valid) {
            throw new IllegalStateException(
                    current + " → " + next + " 상태 전이는 허용되지 않습니다.");
        }
    }

    private Order findOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + id));
    }
}
