<template>
  <div>
    <nav class="nav">
      <RouterLink to="/" class="brand">🍜 든든한끼</RouterLink>
      <div class="nav-links">
        <RouterLink to="/">메뉴</RouterLink>
        <RouterLink to="/cart" class="cart-link">
          장바구니
          <span v-if="cart.totalCount > 0" class="badge">{{ cart.totalCount }}</span>
        </RouterLink>
        <RouterLink v-if="auth.isLoggedIn" to="/orders">내 주문</RouterLink>
        <button v-if="auth.isLoggedIn" @click="handleLogout" class="btn-link">로그아웃</button>
        <RouterLink v-else to="/login">로그인</RouterLink>
      </div>
    </nav>

    <main class="main-content">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useCartStore } from './stores/cart.js'
import { useAuthStore } from './stores/auth.js'

const cart   = useCartStore()
const auth   = useAuthStore()
const router = useRouter()

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style>
.nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e8e8e6;
  position: sticky;
  top: 0;
  z-index: 100;
}
.brand { font-size: 20px; font-weight: 700; color: #e8400c; }
.nav-links { display: flex; align-items: center; gap: 20px; font-size: 15px; }
.nav-links a, .nav-links button { color: #444; transition: color 0.15s; }
.nav-links a:hover, .nav-links button:hover { color: #e8400c; }
.nav-links a.router-link-active { color: #e8400c; font-weight: 600; }
.cart-link { position: relative; }
.badge {
  position: absolute; top: -8px; right: -12px;
  background: #e8400c; color: #fff;
  font-size: 11px; font-weight: 700;
  border-radius: 999px; padding: 1px 5px;
  min-width: 18px; text-align: center;
}
.btn-link { background: none; border: none; cursor: pointer; font-size: 15px; }
.main-content { max-width: 1100px; margin: 0 auto; padding: 32px 16px; }
</style>
