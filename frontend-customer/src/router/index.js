import { createRouter, createWebHistory } from 'vue-router'
import MenuView     from '../views/MenuView.vue'
import CartView     from '../views/CartView.vue'
import OrdersView   from '../views/OrdersView.vue'
import LoginView    from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/',         component: MenuView },
    { path: '/cart',     component: CartView },
    { path: '/orders',   component: OrdersView },
    { path: '/login',    component: LoginView },
    { path: '/register', component: RegisterView },
  ]
})
