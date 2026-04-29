<template>
  <div class="wrap">
    <div class="box">
      <h2>회원가입</h2>
      <input v-model="form.username" placeholder="아이디 (3~30자)" />
      <input v-model="form.password" type="password" placeholder="비밀번호 (8자 이상)" />
      <button @click="submit" :disabled="loading">
        {{ loading ? '처리 중...' : '가입하기' }}
      </button>
      <p class="err" v-if="error">{{ error }}</p>
      <p class="hint"><a href="#" @click.prevent="$router.push('/login')">로그인으로 돌아가기</a></p>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import api from '../services/api.js'

const form    = reactive({ username: '', password: '' })
const error   = ref('')
const loading = ref(false)
const router  = useRouter()

async function submit() {
  error.value = ''
  if (form.username.length < 3) { error.value = '아이디는 3자 이상이어야 합니다.'; return }
  if (form.password.length < 8) { error.value = '비밀번호는 8자 이상이어야 합니다.'; return }
  loading.value = true
  try {
    await api.post('/auth/register', { ...form, role: 'CUSTOMER' })
    alert('가입 완료! 로그인해주세요.')
    router.push('/login')
  } catch (e) {
    error.value = e.response?.data?.message || '가입 실패. 이미 사용 중인 아이디일 수 있습니다.'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.wrap { display: flex; justify-content: center; align-items: center; min-height: 70vh; }
.box { width: 100%; max-width: 380px; background: white; padding: 36px;
  border-radius: 14px; box-shadow: 0 4px 20px rgba(0,0,0,.1);
  display: flex; flex-direction: column; gap: 14px; }
h2 { text-align: center; margin-bottom: 4px; }
input { padding: 12px; border: 1px solid #ddd; border-radius: 8px; font-size: 1rem; }
input:focus { outline: none; border-color: #ff6b35; }
button { padding: 13px; background: #ff6b35; color: white; border: none;
  border-radius: 8px; font-size: 1rem; cursor: pointer; }
button:disabled { background: #ccc; cursor: not-allowed; }
.err { color: #e53935; font-size: .88rem; }
.hint { text-align: center; font-size: .88rem; }
.hint a { color: #ff6b35; text-decoration: none; }
</style>
