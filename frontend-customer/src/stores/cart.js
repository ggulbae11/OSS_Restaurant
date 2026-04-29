import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const CART_KEY = 'cart'

function loadFromStorage() {
  try {
    return JSON.parse(localStorage.getItem(CART_KEY) || '[]')
  } catch {
    return []
  }
}

function saveToStorage(items) {
  localStorage.setItem(CART_KEY, JSON.stringify(items))
}

export const useCartStore = defineStore('cart', () => {
  const items = ref(loadFromStorage())

  const totalCount = computed(() =>
    items.value.reduce((sum, i) => sum + i.quantity, 0)
  )

  const totalPrice = computed(() =>
    items.value.reduce((sum, i) => sum + i.price * i.quantity, 0)
  )

  // 같은 메뉴 + 같은 옵션이면 수량 증가, 아니면 신규 항목 추가
  function addItem(menu, options = []) {
    const optKey = [...options].sort().join(',')
    const existing = items.value.find(
      (i) => i.menuId === menu.id && i.optionKey === optKey
    )
    if (existing) {
      existing.quantity++
    } else {
      items.value.push({
        menuId:   menu.id,
        name:     menu.name,
        price:    menu.price,
        quantity: 1,
        options,
        optionKey: optKey,
      })
    }
    saveToStorage(items.value)
  }

  function removeItem(menuId, optionKey) {
    const idx = items.value.findIndex(
      (i) => i.menuId === menuId && i.optionKey === optionKey
    )
    if (idx !== -1) {
      items.value.splice(idx, 1)
      saveToStorage(items.value)
    }
  }

  function updateQuantity(menuId, optionKey, quantity) {
    const item = items.value.find(
      (i) => i.menuId === menuId && i.optionKey === optionKey
    )
    if (item) {
      if (quantity <= 0) removeItem(menuId, optionKey)
      else item.quantity = quantity
      saveToStorage(items.value)
    }
  }

  function clear() {
    items.value = []
    localStorage.removeItem(CART_KEY)
  }

  return { items, totalCount, totalPrice, addItem, removeItem, updateQuantity, clear }
})
