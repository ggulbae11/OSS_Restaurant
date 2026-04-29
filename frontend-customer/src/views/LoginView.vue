<template>
  <div class="login-wrap">
    <div class="login-box">
      <h2>🍽️ 로그인</h2>
      <input v-model="username" placeholder="아이디" @keyup.enter="submit" />
      <input v-model="password" type="password" placeholder="비밀번호" @keyup.enter="submit" />
      <button @click="submit" :disabled="loading">
        {{ loading ? '로그인 중...' : '로그인' }}
      </button>
      <p class="err" v-if="error">{{ error }}</p>
      <p class="hint">계정이 없으신가요? <a href="#" @click.prevent="goRegister">회원가입</a></p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

const username = ref('')
const password = ref('')
const error    = ref('')
const loading  = ref(false)
const router   = useRouter()
const auth     = useAuthStore()

async function submit() {
  if (!username.value || !password.value) {
    error.value = '아이디와 비밀번호를 입력해주세요.'
    return
  }
  loading.value = true
  error.value   = ''
  try {
    await auth.login(username.value, password.value)
    router.push('/')
  } catch {
    error.value = '아이디 또는 비밀번호가 틀렸습니다.'
  } finally {
    loading.value = false
  }
}

function goRegister() {
  router.push('/register')
}
</script>

<style scoped>
.login-wrap { display: flex; justify-content: center; align-items: center; min-height: 70vh; }
.login-box { width: 100%; max-width: 380px; background: white; padding: 36px;
  border-radius: 14px; box-shadow: 0 4px 20px rgba(0,0,0,.1);
  display: flex; flex-direction: column; gap: 14px; }
h2 { text-align: center; color: #ff6b35; margin-bottom: 4px; }
input { padding: 12px; border: 1px solid #ddd; border-radius: 8px;
  font-size: 1rem; outline: none; transition: border-color .2s; }
input:focus { border-color: #ff6b35; }
button { padding: 13px; background: #ff6b35; color: white; border: none;
  border-radius: 8px; font-size: 1rem; cursor: pointer; transition: background .2s; }
button:hover:not(:disabled) { background: #e55a2b; }
button:disabled { background: #ccc; cursor: not-allowed; }
.err { color: #e53935; font-size: .88rem; text-align: center; }
.hint { text-align: center; font-size: .88rem; color: #888; }
.hint a { color: #ff6b35; text-decoration: none; }
</style>
