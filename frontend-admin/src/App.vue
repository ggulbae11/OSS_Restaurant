<template>
  <div>
    <nav class="nav" v-if="isLoggedIn">
      <span class="logo">🔧 식당 관리자</span>
      <router-link to="/orders">주문 관리</router-link>
      <router-link to="/menus">메뉴 관리</router-link>
      <router-link to="/reviews">리뷰 관리</router-link>
      <button @click="logout">로그아웃</button>
    </nav>
    <main><router-view /></main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const router    = useRouter()
const isLoggedIn = computed(() => !!localStorage.getItem('adminToken'))

function logout() {
  localStorage.removeItem('adminToken')
  router.push('/login')
}
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  background: #f0f2f5; color: #222; }
.nav { background: #1a1a2e; color: white; padding: 0 24px;
  display: flex; align-items: center; gap: 0; height: 56px; }
.logo { font-size: 1.1rem; font-weight: bold; margin-right: auto; color: white; }
.nav a { color: #a0a8c0; text-decoration: none; padding: 0 16px; height: 56px;
  display: flex; align-items: center; font-size: .95rem; transition: color .2s; }
.nav a:hover,
.nav a.router-link-active { color: white; border-bottom: 2px solid #6c63ff; }
.nav button { margin-left: 16px; background: transparent;
  border: 1px solid #a0a8c0; color: #a0a8c0; padding: 6px 14px;
  border-radius: 6px; cursor: pointer; font-size: .9rem; }
.nav button:hover { color: white; border-color: white; }
main { max-width: 1280px; margin: 0 auto; padding: 28px 24px; }
h2 { font-size: 1.4rem; font-weight: 600; margin-bottom: 20px; color: #1a1a2e; }
</style>
