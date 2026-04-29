package com.restaurant.core.service;

import com.restaurant.core.domain.*;
import com.restaurant.core.dto.*;
import com.restaurant.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository      menuRepository;
    private final CategoryRepository  categoryRepository;

    // ── 카테고리 목록 조회 ────────────────────────────────────────────
    public List<Category> getCategories() {
        return categoryRepository.findAll();
    }

    // ── 메뉴 목록 조회 (카테고리 필터) ───────────────────────────────
    public List<MenuResponse> getMenus(Long categoryId) {
        List<Menu> menus = (categoryId != null)
                ? menuRepository.findByCategoryIdAndAvailableTrue(categoryId)
                : menuRepository.findByAvailableTrue();

        return menus.stream().map(MenuResponse::from).toList();
    }

    // ── 메뉴 단건 조회 ────────────────────────────────────────────────
    public MenuResponse getMenu(Long id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다: " + id));
        return MenuResponse.from(menu);
    }

    // ── 메뉴 생성 (관리자) ────────────────────────────────────────────
    @Transactional
    public MenuResponse createMenu(CreateMenuRequest req) {
        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리가 없습니다: " + req.categoryId()));

        Menu menu = Menu.builder()
                .name(req.name())
                .price(req.price())
                .spicyLevel(req.spicyLevel())
                .cookTime(req.cookTime())
                .imageUrl(req.imageUrl())
                .category(category)
                .available(true)
                .build();

        return MenuResponse.from(menuRepository.save(menu));
    }

    // ── 메뉴 수정 (관리자) ────────────────────────────────────────────
    @Transactional
    public MenuResponse updateMenu(Long id, CreateMenuRequest req) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다: " + id));

        Category category = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("카테고리가 없습니다: " + req.categoryId()));

        menu.setName(req.name());
        menu.setPrice(req.price());
        menu.setSpicyLevel(req.spicyLevel());
        menu.setCookTime(req.cookTime());
        menu.setImageUrl(req.imageUrl());
        menu.setCategory(category);

        return MenuResponse.from(menu);
    }

    // ── 전체 메뉴 목록 조회 (관리자용, 판매중지 포함) ─────────────────
    public List<MenuResponse> getAllMenus() {
        return menuRepository.findAll().stream().map(MenuResponse::from).toList();
    }

    // ── 메뉴 판매 중지 ────────────────────────────────────────────────
    @Transactional
    public void disableMenu(Long id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다: " + id));
        menu.setAvailable(false);
    }

    // ── 메뉴 판매 재개 ────────────────────────────────────────────────
    @Transactional
    public void enableMenu(Long id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다: " + id));
        menu.setAvailable(true);
    }
}
