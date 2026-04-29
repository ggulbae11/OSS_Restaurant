package com.restaurant.core.service;

import com.restaurant.core.domain.Review;
import com.restaurant.core.domain.Review.ReviewStatus;
import com.restaurant.core.dto.*;
import com.restaurant.core.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final AiClient         aiClient;

    // ── 리뷰 등록 (AI 검열 포함) ─────────────────────────────────────
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest req, Long customerId) {

        // 1. AI 검열 호출
        Map<String, Object> modResult = aiClient.moderateReview(req.content());
        String aiStatus = (String) modResult.getOrDefault("status", "NORMAL");
        String aiReason = (String) modResult.getOrDefault("reason", "");

        ReviewStatus status = switch (aiStatus) {
            case "SUSPICIOUS" -> ReviewStatus.SUSPICIOUS;
            case "BLOCKED"    -> ReviewStatus.BLOCKED;
            default           -> ReviewStatus.NORMAL;
        };

        // BLOCKED 상태는 저장하되 노출하지 않음
        Review review = Review.builder()
                .customerId(customerId)
                .menuId(req.menuId())
                .rating(req.rating())
                .content(req.content())
                .status(status)
                .moderationReason(aiReason)
                .build();

        return ReviewResponse.from(reviewRepository.save(review));
    }

    // ── 메뉴별 정상 리뷰 조회 ────────────────────────────────────────
    public List<ReviewResponse> getReviewsByMenu(Long menuId) {
        return reviewRepository.findByMenuIdAndStatus(menuId, ReviewStatus.NORMAL)
                .stream().map(ReviewResponse::from).toList();
    }

    // ── 전체 리뷰 조회 (관리자) ───────────────────────────────────────
    public List<ReviewResponse> getAllReviews() {
        return reviewRepository.findAll()
                .stream().map(ReviewResponse::from).toList();
    }

    // ── 리뷰 상태 수동 변경 (관리자) ─────────────────────────────────
    @Transactional
    public ReviewResponse updateReviewStatus(Long reviewId, ReviewStatus newStatus) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다: " + reviewId));
        review.setStatus(newStatus);
        return ReviewResponse.from(review);
    }
}
