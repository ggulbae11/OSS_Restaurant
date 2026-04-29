<template>
  <div>
    <h2>리뷰 관리</h2>

    <!-- 필터 탭 -->
    <div class="tabs">
      <button v-for="f in filters" :key="f.value"
        :class="{ active: currentFilter === f.value }"
        @click="currentFilter = f.value">
        {{ f.label }}
        <span class="cnt">{{ countBy(f.value) }}</span>
      </button>
    </div>

    <div v-if="loading" class="empty">불러오는 중...</div>
    <div v-else-if="filtered.length === 0" class="empty">해당 리뷰가 없습니다.</div>

    <div class="review-list">
      <div v-for="r in filtered" :key="r.id" class="rcard" :class="r.status.toLowerCase()">
        <div class="rcard-header">
          <div class="meta">
            <span class="rid">#{{ r.id }}</span>
            <span class="menu">메뉴 #{{ r.menuId }}</span>
            <span class="stars">{{ '★'.repeat(r.rating) }}{{ '☆'.repeat(5 - r.rating) }}</span>
          </div>
          <span class="badge" :class="r.status">{{ statusLabel(r.status) }}</span>
        </div>
        <p class="content">{{ r.content }}</p>
        <div class="rcard-footer">
          <span class="date">{{ formatDate(r.createdAt) }}</span>
          <div class="actions">
            <button v-if="r.status !== 'NORMAL'"
              @click="changeStatus(r.id, 'NORMAL')" class="btn-approve">승인</button>
            <button v-if="r.status !== 'SUSPICIOUS'"
              @click="changeStatus(r.id, 'SUSPICIOUS')" class="btn-warn">의심</button>
            <button v-if="r.status !== 'BLOCKED'"
              @click="changeStatus(r.id, 'BLOCKED')" class="btn-block">차단</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../services/api.js'

const reviews       = ref([])
const loading       = ref(true)
const currentFilter = ref('ALL')

const filters = [
  { label: '전체',    value: 'ALL' },
  { label: '정상',    value: 'NORMAL' },
  { label: '의심',    value: 'SUSPICIOUS' },
  { label: '차단',    value: 'BLOCKED' },
]

const filtered = computed(() =>
  currentFilter.value === 'ALL'
    ? reviews.value
    : reviews.value.filter(r => r.status === currentFilter.value)
)

function countBy(val) {
  if (val === 'ALL') return reviews.value.length
  return reviews.value.filter(r => r.status === val).length
}

onMounted(load)

async function load() {
  try {
    reviews.value = (await api.get('/admin/reviews')).data
  } catch {}
  finally { loading.value = false }
}

async function changeStatus(id, status) {
  try {
    await api.patch(`/admin/reviews/${id}/status?status=${status}`)
    await load()
  } catch { alert('상태 변경 실패') }
}

function statusLabel(s) {
  return { NORMAL: '정상', SUSPICIOUS: '의심', BLOCKED: '차단' }[s] || s
}

function formatDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleString('ko-KR')
}
</script>

<style scoped>
.tabs { display: flex; gap: 8px; margin-bottom: 20px; }
.tabs button { padding: 7px 18px; border: 1px solid #ddd; border-radius: 20px;
  background: white; cursor: pointer; font-size: .9rem; }
.tabs button.active { background: #1a1a2e; color: white; border-color: #1a1a2e; }
.cnt { background: rgba(0,0,0,.12); border-radius: 10px;
  padding: 1px 7px; font-size: .75rem; margin-left: 6px; }
.empty { text-align: center; color: #888; margin-top: 60px; }
.review-list { display: flex; flex-direction: column; gap: 12px; }
.rcard { background: white; border-radius: 10px; padding: 16px;
  box-shadow: 0 1px 6px rgba(0,0,0,.07); border-left: 4px solid #ccc; }
.rcard.normal     { border-left-color: #4caf50; }
.rcard.suspicious { border-left-color: #ff9800; }
.rcard.blocked    { border-left-color: #e53935; }
.rcard-header { display: flex; justify-content: space-between;
  align-items: center; margin-bottom: 10px; }
.meta { display: flex; align-items: center; gap: 12px; }
.rid  { font-weight: bold; color: #1a1a2e; }
.menu { font-size: .85rem; color: #888; }
.stars { color: #f0a500; letter-spacing: 2px; }
.badge { padding: 3px 10px; border-radius: 12px; font-size: .78rem;
  font-weight: 600; color: white; }
.badge.NORMAL     { background: #4caf50; }
.badge.SUSPICIOUS { background: #ff9800; }
.badge.BLOCKED    { background: #e53935; }
.content { color: #333; line-height: 1.6; font-size: .95rem; }
.rcard-footer { display: flex; justify-content: space-between;
  align-items: center; margin-top: 12px; }
.date { font-size: .78rem; color: #aaa; }
.actions { display: flex; gap: 6px; }
.btn-approve { padding: 4px 12px; background: #e8f5e9; color: #388e3c;
  border: 1px solid #a5d6a7; border-radius: 6px; cursor: pointer; font-size: .82rem; }
.btn-warn    { padding: 4px 12px; background: #fff3e0; color: #e65100;
  border: 1px solid #ffcc80; border-radius: 6px; cursor: pointer; font-size: .82rem; }
.btn-block   { padding: 4px 12px; background: #ffebee; color: #c62828;
  border: 1px solid #ef9a9a; border-radius: 6px; cursor: pointer; font-size: .82rem; }
</style>
