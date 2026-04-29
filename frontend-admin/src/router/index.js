import { createRouter, createWebHistory } from 'vue-router'
import LoginView   from '../views/LoginView.vue'
import OrdersView  from '../views/OrdersView.vue'
import MenusView   from '../views/MenusView.vue'
import ReviewsView from '../views/ReviewsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/',        redirect: '/orders' },
    { path: '/login',   component: LoginView },
    { path: '/orders',  component: OrdersView,  meta: { requiresAuth: true } },
    { path: '/menus',   component: MenusView,   meta: { requiresAuth: true } },
    { path: '/reviews', component: ReviewsView, meta: { requiresAuth: true } },
  ]
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !localStorage.getItem('adminToken')) {
    next('/login')
  } else {
    next()
  }
})

export default router
