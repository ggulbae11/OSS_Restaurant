<template>
  <div>
    <!-- AI 추천 패널 -->
    <div class="ai-panel">
      <h2>🤖 AI 메뉴 추천</h2>
      <div class="ai-input-row">
        <input
          v-model="aiMessage"
          placeholder="예: 매콤한 거 먹고 싶어요, 가볍게 먹고 싶은데..."
          @keyup.enter="requestAiRecommend"
        />
        <select v-model="aiTime">
          <option value="">시간대</option>
          <option value="breakfast">아침</option>
          <option value="lunch">점심</option>
          <option value="dinner">저녁</option>
          <option value="snack">간식</option>
        </select>
        <button @click="requestAiRecommend" :disabled="aiLoading" class="btn-primary">
          {{ aiLoading ? '분석 중...' : '추천받기' }}
        </button>
      </div>
      <div v-if="aiResult" class="ai-result">
        <p class="ai-reason">{{ aiResult.reason }}</p>
        <div class="ai-chips">
          <button
            v-for="id in aiResult.recommendedMenuIds"
            :key="id"
            class="chip"
            @click="scrollToMenu(id)"
          >{{ menuNameById(id) }}</button>
        </div>
      </div>
    </div>

    <!-- 카테고리 탭 -->
    <div class="category-tabs">
      <button
        :class="['tab', { active: selectedCategory === null }]"
        @click="selectedCategory = null"
      >전체</button>
      <button
        v-for="cat in categories"
        :key="cat.id"
        :class="['tab', { active: selectedCategory === cat.id }]"
        @click="selectedCategory = cat.id"
      >{{ cat.name }}</button>
    </div>

    <!-- 메뉴 그리드 -->
    <div v-if="loading" class="loading">불러오는 중...</div>
    <div v-else class="menu-grid">
      <div
        v-for="menu in filteredMenus"
        :key="menu.id"
        :id="`menu-${menu.id}`"
        :class="['menu-card', { highlighted: highlightedIds.has(menu.id) }]"
      >
        <div class="menu-img-placeholder">
          <img v-if="menu.imageUrl" :src="menu.imageUrl" :alt="menu.name" />
          <span v-else class="emoji-placeholder">🍽️</span>
        </div>
        <div class="menu-info">
          <h3>{{ menu.name }}</h3>
          <div class="meta-row">
            <span class="spicy" v-if="menu.spicyLevel > 0">
              {{ '🌶️'.repeat(menu.spicyLevel) }}
            </span>
            <span class="cook-time">⏱ {{ menu.cookTime }}분</span>
          </div>
          <div class="price-row">
            <span class="price">{{ menu.price.toLocaleString() }}원</span>
            <button class="btn-add" @click="addToCart(menu)">+ 담기</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 토스트 -->
    <Transition name="toast">
      <div v-if="toastMsg" class="toast">{{ toastMsg }}</div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { menuApi, aiApi } from '../services/api.js'
import { useCartStore } from '../stores/cart.js'

const categories      = ref([])
const menus           = ref([])
const selectedCategory= ref(null)
const loading         = ref(false)

const aiMessage       = ref('')
const aiTime          = ref('')
const aiLoading       = ref(false)
const aiResult        = ref(null)
const highlightedIds  = ref(new Set())

const toastMsg        = ref('')
const cart            = useCartStore()

const filteredMenus = computed(() => menus.value)

async function fetchMenus(catId) {
  loading.value = true
  try {
    const { data } = await menuApi.getMenus(catId)
    menus.value = data
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const { data } = await menuApi.getCategories()
  categories.value = data
  await fetchMenus(null)
})

watch(selectedCategory, (catId) => fetchMenus(catId))

async function requestAiRecommend() {
  if (!aiMessage.value.trim()) return
  aiLoading.value = true
  aiResult.value  = null
  try {
    const { data } = await aiApi.recommend({
      message:     aiMessage.value,
      time:        aiTime.value || undefined,
      userHistory: [],
    })
    aiResult.value = data
    highlightedIds.value = new Set(data.recommendedMenuIds)
    // 3초 후 하이라이트 해제
    setTimeout(() => { highlightedIds.value = new Set() }, 3000)
  } catch (e) {
    showToast('AI 추천 요청 실패: ' + (e.response?.data?.detail || e.message))
  } finally {
    aiLoading.value = false
  }
}

function menuNameById(id) {
  return menus.value.find(m => m.id === id)?.name ?? `#${id}`
}

function scrollToMenu(menuId) {
  document.getElementById(`menu-${menuId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function addToCart(menu) {
  cart.addItem(menu)
  showToast(`${menu.name}을(를) 장바구니에 담았습니다.`)
}

function showToast(msg) {
  toastMsg.value = msg
  setTimeout(() => { toastMsg.value = '' }, 2500)
}
</script>

<style scoped>
.ai-panel {
  background: #fff7f5; border: 1px solid #fcd3c7;
  border-radius: 12px; padding: 20px; margin-bottom: 28px;
}
.ai-panel h2 { font-size: 17px; font-weight: 700; margin-bottom: 12px; }
.ai-input-row { display: flex; gap: 8px; }
.ai-input-row input {
  flex: 1; padding: 10px 14px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px;
}
.ai-input-row select {
  padding: 10px 8px; border: 1px solid #ddd; border-radius: 8px; font-size: 14px;
}
.btn-primary {
  padding: 10px 18px; background: #e8400c; color: #fff;
  border: none; border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer;
}
.btn-primary:disabled { opacity: 0.6; cursor: default; }
.ai-result { margin-top: 14px; }
.ai-reason { font-size: 14px; color: #555; margin-bottom: 8px; }
.ai-chips { display: flex; gap: 8px; flex-wrap: wrap; }
.chip {
  padding: 6px 14px; background: #e8400c; color: #fff;
  border: none; border-radius: 999px; font-size: 13px; cursor: pointer;
}

.category-tabs { display: flex; gap: 8px; margin-bottom: 24px; flex-wrap: wrap; }
.tab {
  padding: 8px 18px; border: 1px solid #ddd; border-radius: 999px;
  background: #fff; font-size: 14px; cursor: pointer; transition: all 0.15s;
}
.tab.active { background: #e8400c; color: #fff; border-color: #e8400c; }

.menu-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}
.menu-card {
  background: #fff; border-radius: 12px; overflow: hidden;
  border: 2px solid transparent; transition: border-color 0.3s, box-shadow 0.2s;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.menu-card.highlighted { border-color: #e8400c; box-shadow: 0 0 0 3px rgba(232,64,12,0.15); }
.menu-img-placeholder {
  width: 100%; height: 160px; background: #f2f2f0;
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.menu-img-placeholder img { width: 100%; height: 100%; object-fit: cover; }
.emoji-placeholder { font-size: 48px; }
.menu-info { padding: 14px; }
.menu-info h3 { font-size: 16px; font-weight: 700; margin-bottom: 6px; }
.meta-row { display: flex; gap: 12px; font-size: 13px; color: #888; margin-bottom: 10px; }
.price-row { display: flex; align-items: center; justify-content: space-between; }
.price { font-size: 17px; font-weight: 700; color: #e8400c; }
.btn-add {
  padding: 7px 14px; background: #e8400c; color: #fff;
  border: none; border-radius: 8px; font-size: 13px; cursor: pointer;
}

.loading { text-align: center; padding: 60px; color: #888; }

.toast {
  position: fixed; bottom: 32px; left: 50%; transform: translateX(-50%);
  background: #1a1a1a; color: #fff; padding: 12px 24px;
  border-radius: 999px; font-size: 14px; z-index: 999;
}
.toast-enter-active, .toast-leave-active { transition: opacity 0.3s; }
.toast-enter-from, .toast-leave-to { opacity: 0; }
</style>
