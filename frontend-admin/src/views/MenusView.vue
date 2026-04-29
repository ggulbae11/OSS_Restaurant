<template>
  <div>
    <div class="top-bar">
      <h2>메뉴 관리</h2>
      <button class="ai-btn" @click="suggestMenu">🤖 AI 신메뉴 추천</button>
    </div>

    <!-- AI 추천 결과 -->
    <div v-if="suggestion" class="ai-result">
      <strong>💡 추천: {{ suggestion.name }}</strong>
      — 예상 가격 {{ suggestion.estimatedPrice?.toLocaleString() }}원
      <p>{{ suggestion.reason }}</p>
      <p class="detail">{{ suggestion.suggestion }}</p>
    </div>

    <!-- 새 메뉴 등록 폼 -->
    <div class="form-card">
      <h3>새 메뉴 등록</h3>
      <div class="form-grid">
        <div class="field">
          <label>메뉴 이름</label>
          <input v-model="form.name" placeholder="예: 떡볶이" />
        </div>
        <div class="field">
          <label>가격 (원)</label>
          <input v-model.number="form.price" type="number" placeholder="5000" />
        </div>
        <div class="field">
          <label>조리 시간 (분)</label>
          <input v-model.number="form.cookTime" type="number" placeholder="10" />
        </div>
        <div class="field">
          <label>맵기 (0~5)</label>
          <input v-model.number="form.spicyLevel" type="number" min="0" max="5" />
        </div>
        <div class="field">
          <label>카테고리 ID</label>
          <input v-model.number="form.categoryId" type="number" placeholder="1" />
        </div>
        <div class="field">
          <label>이미지 URL</label>
          <input v-model="form.imageUrl" placeholder="https://..." />
        </div>
      </div>
      <button class="submit-btn" @click="createMenu" :disabled="creating">
        {{ creating ? '등록 중...' : '메뉴 등록' }}
      </button>
      <p v-if="formError" class="err">{{ formError }}</p>
    </div>

    <!-- 메뉴 목록 -->
    <div class="grid">
      <div v-for="m in menus" :key="m.id" class="mcard" :class="{ disabled: !m.available }">
        <img v-if="m.imageUrl" :src="m.imageUrl" alt="" class="thumb" />
        <div class="mcard-body">
          <div class="mcard-header">
            <strong>{{ m.name }}</strong>
            <span class="tag" :class="m.available ? 'on' : 'off'">
              {{ m.available ? '판매중' : '중지' }}
            </span>
          </div>
          <p class="mprice">{{ m.price.toLocaleString() }}원</p>
          <p class="mmeta">🌶️ {{ m.spicyLevel }} | ⏱ {{ m.cookTime }}분</p>
          <button v-if="m.available" class="disable-btn" @click="disableMenu(m.id)">
            판매 중지
          </button>
          <button v-else class="enable-btn" @click="enableMenu(m.id)">
            판매 재개
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '../services/api.js'

const menus      = ref([])
const suggestion = ref(null)
const creating   = ref(false)
const formError  = ref('')
const form = reactive({
  name: '', price: 0, cookTime: 10, spicyLevel: 0, categoryId: 1, imageUrl: ''
})

onMounted(load)

async function load() {
  try { menus.value = (await api.get('/admin/menus')).data } catch {}
}

async function createMenu() {
  formError.value = ''
  if (!form.name) { formError.value = '메뉴 이름을 입력하세요.'; return }
  if (form.price <= 0) { formError.value = '가격을 입력하세요.'; return }
  creating.value = true
  try {
    await api.post('/admin/menus', { ...form })
    Object.assign(form, { name: '', price: 0, cookTime: 10, spicyLevel: 0, categoryId: 1, imageUrl: '' })
    await load()
  } catch (e) {
    formError.value = e.response?.data?.message || '등록 실패'
  } finally {
    creating.value = false
  }
}

async function disableMenu(id) {
  if (!confirm('이 메뉴를 판매 중지하시겠습니까?')) return
  await api.delete(`/admin/menus/${id}`)
  await load()
}

async function enableMenu(id) {
  if (!confirm('이 메뉴를 판매 재개하시겠습니까?')) return
  await api.patch(`/admin/menus/${id}/enable`)
  await load()
}

async function suggestMenu() {
  try {
    const res = await api.post('/admin/ai/menu/suggest', {})
    suggestion.value = res.data
  } catch { alert('AI 추천 실패') }
}
</script>

<style scoped>
.top-bar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.ai-btn { padding: 8px 18px; background: #6c63ff; color: white;
  border: none; border-radius: 8px; cursor: pointer; }
.ai-result { background: #f0ebff; border: 1px solid #b39ddb; border-radius: 10px;
  padding: 14px 18px; margin-bottom: 20px; }
.ai-result strong { font-size: 1rem; }
.ai-result p { margin-top: 4px; font-size: .9rem; color: #555; }
.detail { color: #888 !important; font-size: .85rem !important; }
.form-card { background: white; border-radius: 12px; padding: 24px;
  margin-bottom: 24px; box-shadow: 0 2px 10px rgba(0,0,0,.07); }
.form-card h3 { margin-bottom: 16px; color: #1a1a2e; }
.form-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field label { font-size: .85rem; color: #666; font-weight: 500; }
.field input { padding: 10px; border: 1px solid #ddd; border-radius: 8px;
  font-size: .95rem; outline: none; }
.field input:focus { border-color: #6c63ff; }
.submit-btn { margin-top: 16px; padding: 12px 32px; background: #1a1a2e;
  color: white; border: none; border-radius: 8px; font-size: 1rem; cursor: pointer; }
.submit-btn:disabled { background: #ccc; cursor: not-allowed; }
.err { color: #e53935; font-size: .88rem; margin-top: 8px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 14px; }
.mcard { background: white; border-radius: 10px; overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,.07); }
.mcard.disabled { opacity: .55; }
.thumb { width: 100%; height: 130px; object-fit: cover; }
.mcard-body { padding: 14px; }
.mcard-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: .75rem; }
.tag.on  { background: #e8f5e9; color: #388e3c; }
.tag.off { background: #ffebee; color: #c62828; }
.mprice { color: #ff6b35; font-weight: bold; margin-bottom: 4px; }
.mmeta  { font-size: .82rem; color: #888; }
.disable-btn { margin-top: 10px; width: 100%; padding: 7px;
  border: 1px solid #e53935; color: #e53935; background: white;
  border-radius: 6px; cursor: pointer; font-size: .85rem; }
.disable-btn:hover { background: #ffebee; }
.enable-btn { margin-top: 10px; width: 100%; padding: 7px;
  border: 1px solid #388e3c; color: #388e3c; background: white;
  border-radius: 6px; cursor: pointer; font-size: .85rem; }
.enable-btn:hover { background: #e8f5e9; }
</style>
