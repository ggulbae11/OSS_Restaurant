import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 요청 인터셉터 — Authorization 헤더 자동 삽입
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 응답 인터셉터 — 401 시 로그인 페이지 리다이렉트
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('accessToken')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

// ── Auth ─────────────────────────────────────────────────────────────────
export const authApi = {
  login:    (data)  => api.post('/auth/login', data),
  register: (data)  => api.post('/auth/register', data),
  logout:   ()      => api.post('/auth/logout'),
}

// ── Menu ─────────────────────────────────────────────────────────────────
export const menuApi = {
  getCategories: ()         => api.get('/categories'),
  getMenus:      (catId)    => api.get('/menus', { params: { categoryId: catId } }),
  getMenu:       (id)       => api.get(`/menus/${id}`),
}

// ── Order ─────────────────────────────────────────────────────────────────
export const orderApi = {
  create:   (data)  => api.post('/orders', data),
  get:      (id)    => api.get(`/orders/${id}`),
  getMyOrders: ()   => api.get('/orders/my'),
}

// ── Review ────────────────────────────────────────────────────────────────
export const reviewApi = {
  create:   (data)    => api.post('/reviews', data),
  getByMenu:(menuId)  => api.get('/reviews', { params: { menuId } }),
}

// ── AI ───────────────────────────────────────────────────────────────────
export const aiApi = {
  recommend:       (data)  => api.post('/ai/recommend', data),
  generateReview:  (data)  => api.post('/ai/review/generate', data),
}

export default api
