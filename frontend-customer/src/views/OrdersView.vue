<template>
  <div class="orders-page">
    <h2>📦 내 주문</h2>

    <div v-if="loading" class="empty">불러오는 중...</div>

    <div v-else-if="error" class="err-box">
      <p>{{ error }}</p>
      <button class="btn-retry" @click="load">다시 시도</button>
    </div>

    <div v-else-if="orders.length === 0" class="empty">
      <p>주문 내역이 없습니다.</p>
      <RouterLink to="/" class="btn-go">메뉴 보러 가기</RouterLink>
    </div>

    <div v-else class="order-list">
      <div
        v-for="o in orders"
        :key="o.id"
        class="order-card"
        :class="{ open: openIds.has(o.id) }"
      >
        <!-- 카드 헤더 (항상 표시) -->
        <div class="card-header" @click="toggle(o.id)">
          <div class="header-left">
            <span class="order-num">주문 #{{ o.id }}</span>
            <span class="badge" :class="o.status">{{ statusLabel(o.status) }}</span>
          </div>
          <div class="header-right">
            <span class="total">{{ o.totalPrice.toLocaleString() }}원</span>
            <span class="date">{{ formatDate(o.createdAt) }}</span>
            <span class="arrow">{{ openIds.has(o.id) ? '▲' : '▼' }}</span>
          </div>
        </div>

        <!-- 상세 (펼쳤을 때) -->
        <div v-if="openIds.has(o.id)" class="card-detail">
          <div class="divider"></div>

          <table class="item-table">
            <thead>
              <tr><th>메뉴</th><th>단가</th><th>수량</th><th>소계</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in o.items" :key="item.menuId + item.options">
                <td>
                  {{ item.menuName }}
                  <span v-if="item.options" class="opts">({{ item.options }})</span>
                </td>
                <td>{{ item.unitPrice.toLocaleString() }}원</td>
                <td>× {{ item.quantity }}</td>
                <td class="subtotal">{{ (item.unitPrice * item.quantity).toLocaleString() }}원</td>
              </tr>
            </tbody>
          </table>

          <div class="detail-footer">
            <div v-if="o.requestNote" class="note">📝 요청: {{ o.requestNote }}</div>
            <div v-if="o.estimatedTime" class="eta">⏱ 예상 대기: {{ o.estimatedTime }}분</div>
            <div class="total-row">
              합계 <strong>{{ o.totalPrice.toLocaleString() }}원</strong>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { orderApi } from '../services/api.js'

const orders  = ref([])
const loading = ref(true)
const error   = ref('')
const openIds = ref(new Set())

async function load() {
  loading.value = true
  error.value   = ''
  try {
    const { data } = await orderApi.getMyOrders()
    orders.value = data
  } catch (e) {
    if (e.response?.status === 401) {
      error.value = '로그인이 필요합니다.'
    } else {
      error.value = '주문 내역을 불러오지 못했습니다. (' + (e.response?.status ?? '네트워크 오류') + ')'
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)

function toggle(id) {
  const s = new Set(openIds.value)
  s.has(id) ? s.delete(id) : s.add(id)
  openIds.value = s
}

function statusLabel(s) {
  return {
    CREATED: '접수됨', ACCEPTED: '확인됨', COOKING: '조리중',
    READY: '준비완료', DELIVERING: '배달중',
    COMPLETED: '배달완료', CANCELED: '취소됨',
  }[s] || s
}

function formatDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleString('ko-KR', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}
</script>

<style scoped>
.orders-page { max-width: 720px; }
.orders-page h2 { font-size: 22px; font-weight: 700; margin-bottom: 24px; }

.empty { color: #888; margin-top: 60px; text-align: center; font-size: 1rem; }
.empty p { margin-bottom: 16px; }
.btn-go {
  display: inline-block; padding: 10px 22px;
  background: #e8400c; color: #fff; border-radius: 8px; font-weight: 600;
}
.err-box {
  background: #fff0f0; border: 1px solid #ffcccc; border-radius: 10px;
  padding: 20px; text-align: center; color: #c0392b;
}
.btn-retry {
  margin-top: 10px; padding: 8px 20px; background: #e8400c;
  color: #fff; border: none; border-radius: 8px; cursor: pointer;
}

.order-list { display: flex; flex-direction: column; gap: 12px; }

.order-card {
  background: #fff; border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,.07);
  overflow: hidden;
  border: 2px solid transparent;
  transition: border-color 0.2s;
}
.order-card.open { border-color: #e8400c; }

.card-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; cursor: pointer; user-select: none;
}
.card-header:hover { background: #fafaf8; }
.header-left { display: flex; align-items: center; gap: 10px; }
.order-num { font-weight: 700; font-size: 15px; }
.header-right { display: flex; align-items: center; gap: 12px; font-size: 13px; color: #888; }
.total { font-size: 15px; font-weight: 700; color: #e8400c; }
.arrow { font-size: 11px; color: #aaa; }

.badge {
  padding: 3px 10px; border-radius: 12px; font-size: 12px;
  font-weight: 600; color: #fff; background: #aaa;
}
.badge.COOKING    { background: #f0a500; }
.badge.READY      { background: #4caf50; }
.badge.DELIVERING { background: #2196f3; }
.badge.COMPLETED  { background: #388e3c; }
.badge.CANCELED   { background: #e53935; }
.badge.ACCEPTED   { background: #26a69a; }

.card-detail { padding: 0 20px 20px; }
.divider { height: 1px; background: #f0f0ee; margin-bottom: 16px; }

.item-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.item-table th {
  text-align: left; padding: 6px 8px;
  color: #999; font-weight: 500; border-bottom: 1px solid #eee;
}
.item-table td { padding: 8px 8px; border-bottom: 1px solid #f5f5f5; }
.item-table tr:last-child td { border-bottom: none; }
.opts { color: #aaa; font-size: 12px; }
.subtotal { font-weight: 600; color: #333; }

.detail-footer { margin-top: 14px; display: flex; flex-direction: column; gap: 6px; }
.note  { font-size: 13px; color: #888; }
.eta   { font-size: 13px; color: #555; }
.total-row {
  text-align: right; font-size: 15px;
  padding-top: 10px; border-top: 1px solid #eee; color: #333;
}
.total-row strong { color: #e8400c; font-size: 17px; }
</style>
