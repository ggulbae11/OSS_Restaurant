<template>
  <div class="cart-page">
    <h2>🛒 장바구니</h2>

    <!-- 빈 장바구니 -->
    <div v-if="cart.items.length === 0 && !ordered" class="empty">
      <p>장바구니가 비어 있습니다.</p>
      <RouterLink to="/" class="btn-go-menu">메뉴 보러 가기</RouterLink>
    </div>

    <!-- 주문 완료 화면 -->
    <div v-else-if="ordered" class="result">
      <div class="result-icon">✅</div>
      <h3>주문이 완료됐습니다!</h3>
      <p>주문번호: <strong>#{{ orderId }}</strong></p>
      <p v-if="estimatedTime">예상 대기 시간: <strong>{{ estimatedTime }}분</strong></p>
      <RouterLink to="/orders" class="btn-primary">내 주문 확인하기</RouterLink>
    </div>

    <!-- 장바구니 목록 -->
    <div v-else class="cart-body">
      <div class="item-list">
        <div v-for="item in cart.items" :key="item.menuId + item.optionKey" class="item-row">
          <div class="item-name">
            <span class="name">{{ item.name }}</span>
            <span v-if="item.options?.length" class="opts">{{ item.options.join(', ') }}</span>
          </div>
          <div class="item-qty">
            <button class="qty-btn" @click="cart.updateQuantity(item.menuId, item.optionKey, item.quantity - 1)">−</button>
            <span class="qty-num">{{ item.quantity }}</span>
            <button class="qty-btn" @click="cart.updateQuantity(item.menuId, item.optionKey, item.quantity + 1)">+</button>
          </div>
          <span class="item-price">{{ (item.price * item.quantity).toLocaleString() }}원</span>
          <button class="btn-remove" @click="cart.removeItem(item.menuId, item.optionKey)">✕</button>
        </div>
      </div>

      <div class="divider"></div>

      <div class="total-row">
        <span>합계</span>
        <span class="total-price">{{ cart.totalPrice.toLocaleString() }}원</span>
      </div>

      <textarea
        v-model="note"
        placeholder="요청 사항 (예: 덜 맵게, 빠른 조리 부탁드려요)"
        class="note-area"
      ></textarea>

      <p v-if="error" class="err">{{ error }}</p>

      <button class="btn-order" @click="placeOrder" :disabled="loading">
        {{ loading ? '주문 중...' : `${cart.totalPrice.toLocaleString()}원 주문하기` }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useCartStore } from '../stores/cart.js'
import { orderApi } from '../services/api.js'

const cart    = useCartStore()
const router  = useRouter()
const note    = ref('')
const loading = ref(false)
const error   = ref('')
const ordered = ref(false)
const orderId = ref(null)
const estimatedTime = ref(null)

async function placeOrder() {
  if (!cart.items.length) return
  loading.value = true
  error.value   = ''
  try {
    const { data } = await orderApi.create({
      items: cart.items.map(i => ({
        menuId:   i.menuId,
        quantity: i.quantity,
        options:  i.options || [],
      })),
      requestNote: note.value,
    })
    orderId.value       = data.id
    estimatedTime.value = data.estimatedTime
    cart.clear()
    ordered.value = true
  } catch (e) {
    error.value = e.response?.status === 401
      ? '로그인이 필요합니다.'
      : '주문에 실패했습니다. 다시 시도해주세요.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.cart-page { max-width: 640px; }
.cart-page h2 { font-size: 22px; font-weight: 700; margin-bottom: 24px; }

.empty { text-align: center; padding: 60px 0; color: #888; }
.empty p { font-size: 1.1rem; margin-bottom: 20px; }
.btn-go-menu {
  display: inline-block; padding: 10px 24px;
  background: #e8400c; color: #fff; border-radius: 8px;
  font-weight: 600; font-size: 15px;
}

.item-list { display: flex; flex-direction: column; gap: 10px; }
.item-row {
  display: flex; align-items: center; gap: 12px;
  background: #fff; padding: 14px 16px; border-radius: 10px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}
.item-name { flex: 1; }
.name { font-weight: 600; font-size: 15px; }
.opts { display: block; color: #888; font-size: 12px; margin-top: 2px; }

.item-qty { display: flex; align-items: center; gap: 8px; }
.qty-btn {
  width: 28px; height: 28px; border: 1px solid #ddd; background: #f7f7f5;
  border-radius: 6px; font-size: 16px; cursor: pointer; line-height: 1;
}
.qty-btn:hover { background: #eee; }
.qty-num { min-width: 24px; text-align: center; font-weight: 600; }

.item-price { font-weight: 600; color: #e8400c; min-width: 80px; text-align: right; }
.btn-remove {
  background: none; border: none; color: #bbb; font-size: 16px;
  cursor: pointer; padding: 4px;
}
.btn-remove:hover { color: #e8400c; }

.divider { height: 1px; background: #eee; margin: 16px 0; }
.total-row {
  display: flex; justify-content: space-between;
  font-size: 17px; font-weight: 700; margin-bottom: 16px;
}
.total-price { color: #e8400c; }

.note-area {
  width: 100%; padding: 10px 12px; border: 1px solid #ddd;
  border-radius: 8px; font-size: 14px; resize: vertical; min-height: 64px;
  box-sizing: border-box;
}

.err { color: #e8400c; font-size: 14px; margin-top: 8px; }

.btn-order {
  margin-top: 14px; width: 100%; padding: 16px;
  background: #e8400c; color: #fff; border: none;
  border-radius: 10px; font-size: 16px; font-weight: 700; cursor: pointer;
}
.btn-order:hover:not(:disabled) { background: #c73409; }
.btn-order:disabled { background: #ccc; cursor: not-allowed; }

/* 주문 완료 */
.result {
  text-align: center; padding: 60px 20px;
  background: #fff; border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0,0,0,.08);
}
.result-icon { font-size: 48px; margin-bottom: 12px; }
.result h3 { font-size: 22px; font-weight: 700; margin-bottom: 12px; }
.result p { font-size: 16px; color: #555; margin-bottom: 8px; }
.btn-primary {
  display: inline-block; margin-top: 20px; padding: 12px 28px;
  background: #e8400c; color: #fff; border-radius: 10px;
  font-size: 15px; font-weight: 600;
}
</style>
