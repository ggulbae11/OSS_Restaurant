<template>
  <div class="wrap">
    <div class="box">
      <h2>🔧 관리자 로그인</h2>
      <input v-model="username" placeholder="아이디" @keyup.enter="login" />
      <input v-model="password" type="password" placeholder="비밀번호" @keyup.enter="login" />
      <button @click="login" :disabled="loading">
        {{ loading ? '로그인 중...' : '로그인' }}
      </button>
      <p class="err" v-if="error">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '../services/api.js'

const username = ref('')
const password = ref('')
const error    = ref('')
const loading  = ref(false)
const router   = useRouter()

async function login() {
  if (!username.value || !password.value) { error.value = '아이디와 비밀번호를 입력하세요.'; return }
  loading.value = true
  error.value   = ''
  try {
    const res = await api.post('/auth/login', {
      username: username.value, password: password.value
    })
    localStorage.setItem('adminToken', res.data.accessToken)
    router.push('/orders')
  } catch {
    error.value = '로그인 실패. 관리자 계정을 확인하세요.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.wrap { display: flex; justify-content: center; align-items: center; min-height: 90vh; }
.box { width: 100%; max-width: 380px; background: white; padding: 40px;
  border-radius: 14px; box-shadow: 0 4px 24px rgba(0,0,0,.12);
  display: flex; flex-direction: column; gap: 14px; }
h2 { text-align: center; color: #1a1a2e; margin-bottom: 4px; }
input { padding: 12px; border: 1px solid #ddd; border-radius: 8px; font-size: 1rem; outline: none; }
input:focus { border-color: #6c63ff; }
button { padding: 13px; background: #1a1a2e; color: white; border: none;
  border-radius: 8px; font-size: 1rem; cursor: pointer; }
button:hover:not(:disabled) { background: #2d2d4e; }
button:disabled { background: #ccc; cursor: not-allowed; }
.err { color: #e53935; font-size: .88rem; text-align: center; }
</style>
