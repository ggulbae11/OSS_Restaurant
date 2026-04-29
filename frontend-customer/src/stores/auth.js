import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../services/api.js'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('accessToken') || null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(username, password) {
    const { data } = await authApi.login({ username, password })
    token.value = data.accessToken
    localStorage.setItem('accessToken', data.accessToken)
  }

  async function logout() {
    try { await authApi.logout() } catch { /* 무시 */ }
    token.value = null
    localStorage.removeItem('accessToken')
  }

  return { token, isLoggedIn, login, logout }
})
