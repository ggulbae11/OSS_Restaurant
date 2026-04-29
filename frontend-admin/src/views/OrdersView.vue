<template>
  <div>
    <div class="top-bar">
      <h2>주문 관리</h2>
      <button class="ai-btn" @click="optimizeCook">🤖 AI 조리 순서 최적화</button>
    </div>
    <div v-if="cookReason" class="ai-result">💡 {{ cookReason }}</div>

    <div v-if="loading" class="empty">불러오는 중...</div>
    <div v-else-if="orders.length === 0" class="empty">현재 주문이 없습니다.</div>

    <div class="board">
      <div v-for="col in columns" :key="col.status" class="column">
        <div class="col-header" :class="col.status">
          {{ col.label }}
          <span class="count">{{ ordersBy(col.status).length }}</span>
        </div>
        <div v-for="o in ordersBy(col.status)" :key="o.id" class="card">
          <div class="card-header">
            <strong>#{{ o.id }}</strong>
            <span class="price">{{ o.totalPrice.toLocaleString() }}원</span>
          </div>
          <div class="items">
            <span v-for="i in o.items" :key="i.menuId">{{ i.menuName }}×{{ i.quantity }} </span>
          </div>
          <p v-if="o.requestNote" class="note">📝 {{ o.requestNote }}</p>
          <p class="time">{{ formatTime(o.createdAt) }}</p>
          <div class="actions">
            <button v-for="s in nextStatuses(o.status)" :key="s"
              @click="updateStatus(o.id, s)" class="action-btn">
              {{ actionLabel(s) }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import api from '../services/api.js'

const orders     = ref([])
const loading    = ref(true)
const cookReason = ref('')
let   interval   = null

const columns = [
  { status: 'CREATED',    label: '신규 주문' },
  { status: 'ACCEPTED',   label: '접수 완료' },
  { status: 'COOKING',    label: '조리 중' },
  { status: 'READY',      label: '완료 대기' },
  { status: 'DELIVERING', label: '배달 중' },
  { status: 'COMPLETED',  label: '배달 완료' },
]

onMounted(() => { load(); interval = setInterval(load, 15000) })
onUnmounted(() => clearInterval(interval))

async function load() {
  try {
    orders.value  = (await api.get('/admin/orders')).data
  } catch (e) {
    console.error('주문 목록 조회 실패:', e.response?.status, e.message)
  } finally {
    loading.value = false
  }
}

function ordersBy(status) {
  return orders.value.filter(o => o.status === status)
}

async function updateStatus(id, status) {
  try {
    await api.patch(`/admin/orders/${id}/status`, { status })
    await load()
  } catch (e) {
    alert(e.response?.data?.message || '상태 변경 실패')
  }
}

async function optimizeCook() {
  const items = orders.value
    .filter(o => ['ACCEPTED', 'COOKING'].includes(o.status))
    .flatMap(o => o.items.map(i => ({ menuId: i.menuId, menuName: i.menuName, cookTime: i.cookTime ?? 10 })))
  if (!items.length) { alert('최적화할 활성 주문이 없습니다.'); return }
  const res = await api.post('/admin/ai/cook/sequence', { items })
  cookReason.value = res.data.reason
}

function nextStatuses(s) {
  return {
    CREATED:    ['ACCEPTED', 'CANCELED'],
    ACCEPTED:   ['COOKING', 'CANCELED'],
    COOKING:    ['READY'],
    READY:      ['DELIVERING'],
    DELIVERING: ['COMPLETED'],
  }[s] || []
}

function actionLabel(s) {
  return { ACCEPTED:'접수', COOKING:'조리시작', READY:'조리완료',
    DELIVERING:'배달시작', COMPLETED:'배달완료', CANCELED:'취소' }[s] || s
}

function formatTime(d) {
  if (!d) return ''
  return new Date(d).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}
</script>

<style scoped>
.top-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.ai-btn { padding: 8px 18px; background: #6c63ff; color: white;
  border: none; border-radius: 8px; cursor: pointer; font-size: .9rem; }
.ai-result { background: #f0ebff; border: 1px solid #b39ddb; border-radius: 8px;
  padding: 12px 16px; margin-bottom: 16px; font-size: .9rem; color: #4a3f8a; }
.empty { text-align: center; color: #888; margin-top: 60px; }
.board { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; overflow-x: auto; }
.column { min-width: 180px; }
.col-header { font-size: .82rem; font-weight: 600; padding: 6px 10px;
  border-radius: 6px; margin-bottom: 8px; display: flex; justify-content: space-between;
  background: #e8eaf6; color: #3949ab; }
.col-header.CREATED    { background: #e3f2fd; color: #1565c0; }
.col-header.ACCEPTED   { background: #e8f5e9; color: #2e7d32; }
.col-header.COOKING    { background: #fff3e0; color: #e65100; }
.col-header.READY      { background: #f3e5f5; color: #6a1b9a; }
.col-header.DELIVERING { background: #e1f5fe; color: #0277bd; }
.col-header.COMPLETED  { background: #f1f8e9; color: #33691e; }
.count { background: rgba(0,0,0,.12); border-radius: 10px;
  padding: 1px 7px; font-size: .78rem; }
.card { background: white; border-radius: 8px; padding: 12px;
  margin-bottom: 8px; box-shadow: 0 1px 4px rgba(0,0,0,.08); }
.card-header { display: flex; justify-content: space-between;
  align-items: center; margin-bottom: 6px; }
.price { color: #ff6b35; font-size: .9rem; font-weight: bold; }
.items { font-size: .82rem; color: #555; line-height: 1.6; }
.note { font-size: .8rem; color: #888; margin-top: 4px; }
.time { font-size: .75rem; color: #aaa; margin-top: 4px; }
.actions { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 8px; }
.action-btn { padding: 4px 8px; background: #1a1a2e; color: white;
  border: none; border-radius: 4px; font-size: .75rem; cursor: pointer; }
.action-btn:hover { background: #2d2d4e; }
</style>
