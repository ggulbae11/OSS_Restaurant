"""
DB 초기화 & 시드 데이터 스크립트
- 실행: python data/seed.py
- 원하는 데이터를 아래 섹션에서 직접 수정 후 다시 실행하면 됩니다.
- 재실행 시 기존 데이터를 모두 지우고 다시 삽입합니다.
"""

import sqlite3
import bcrypt
from datetime import datetime

AUTH_DB  = "data/auth/auth.db"
CORE_DB  = "data/core/core.db"
NOW      = datetime.now().isoformat(sep=" ", timespec="seconds")

def hash_pw(plain: str) -> str:
    return bcrypt.hashpw(plain.encode(), bcrypt.gensalt(10)).decode()

# ──────────────────────────────────────────────
# ✏️  여기서 데이터를 자유롭게 수정하세요
# ──────────────────────────────────────────────

USERS = [
    # (username, password, role)  role: CUSTOMER | ADMIN | RIDER
    ("admin",    "admin1234",    "ADMIN"),
    ("customer1","password123",  "CUSTOMER"),
    ("customer2","password123",  "CUSTOMER"),
    ("testuser", "test1234",     "CUSTOMER"),
    ("rider1",   "password123",  "RIDER"),
    ("rider2",   "password123",  "RIDER"),
]

CATEGORIES = [
    # (name,)
    ("한식",),
    ("중식",),
    ("일식",),
    ("음료",),
]

MENUS = [
    # (name, price, image_url, spicy_level, cook_time, available, category_id)
    # category_id: 1=한식, 2=중식, 3=일식, 4=음료
    ("김치찌개",  9000,  "https://img.bizthenaum.co.kr/data/img/1000000869/ori/1000000869_11.jpg", 3, 15, 1, 1),
    ("된장찌개",  8500,  "https://www.okitchen.co.kr/pds/upfile/2020-08-24_427854552[10].jpg", 0, 12, 1, 1),
    ("불고기",   12000,  "https://recipe1.ezmember.co.kr/cache/recipe/2019/03/03/11baafbe81803965b17c3ab42a5992cb1.jpg", 0, 20, 1, 1),
    ("돈까스",  11000,  "https://i.namu.wiki/i/gMSvGksMaKPdznDSM-QxYwYGN-K1zLqOzOpNSAfOwRlrlx6ZY61kOvHHeAHtk9RWrTnA1nnrPri_XkXSewEkpQ.webp",    0, 20, 1, 1),
    ("잔치국수",  6000,  "https://static.wtable.co.kr/image-resize/production/service/recipe/438/16x9/ed2bf141-5342-4804-ac34-6a30fb525b01.jpg",  1,  5, 1, 1),
    ("비빔밥",   8000,  "https://i.namu.wiki/i/dgjXU86ae29hDSCza-L0GZlFt3T9lRx1Ug9cKtqWSzMzs7Cd0CN2SzyLFEJcHVFviKcxAlIwxcllT9s2sck0RA.jpg",     2,  8, 1, 1),
    ("짜장면",   7000,  "https://i.namu.wiki/i/j2AxLP9AtrcJebh4DVfGxowfXwI3a95dG_YZb_Ktczc6Ca7ACyd_NJL3YHQMw8SABGTQiJDwSpySOSSBLZVEZw.webp",                 0, 10, 1, 2),
    ("짬뽕",     8000,  "https://i.namu.wiki/i/2bjVHzbWhq6POtpD7RYABn7A8s_MblEVOMdHzhs2qMuhpXTw7h7sT_xrJLshhalmwxt8fZlc8ajUYEBLst360g.webp",                    4, 12, 1, 2),
    ("탕수육",  15000,  "https://i.namu.wiki/i/DQPzlCdDvbadZasufp6VhKSLDPHU4YlA1f9GbY2fP9nMvjNOSE32JZxL_ysj2duhcUtcWo-cGQvO_qS3rXctJw.webp", 0, 20, 1, 2),
    ("초밥 세트",18000, "https://i.namu.wiki/i/o9RTcnDqEUKy-tIWwvuuYeqPxZYvlS8T-GIMYo9RtN4v2hIbE7hNe08D7ycw5-ZlS0fUOyqA-uLc-HbfCKHKPw.webp", 0, 15, 1, 3),
    ("라멘",    10000,  "https://i.namu.wiki/i/3K_J10Ow-FSXikTijNbiF0q-Id_kj7h3Bz34bRvB9A0yMRZN3VbrrvtryDdZzvFaJApFRglZuoE_SikFIx0KSw.webp",              0, 12, 1, 3),
    ("아메리카노", 4500, "https://image.istarbucks.co.kr/upload/store/skuimg/2025/06/[110563]_20250626094353711.jpg",      0,  3, 1, 4),
    ("아이스티",  4000,  "https://naturetea.co.kr/recipe/img/zero-powder/main-1.png",          0,  2, 1, 4),
]

INGREDIENTS = [
    # (name, stock, unit)
    ("돼지고기",  5000, "g"),
    ("소고기",    3000, "g"),
    ("김치",      4000, "g"),
    ("두부",      2000, "g"),
    ("된장",      1000, "g"),
    ("밀가루",    8000, "g"),
    ("쌀",       10000, "g"),
    ("커피원두",   2000, "g"),
    ("홍차",       500, "g"),
]

MENU_INGREDIENTS = [
    # (menu_id, ingredient_id, required_amount)
    (1, 1, 200),   # 김치찌개 - 돼지고기
    (1, 3, 150),   # 김치찌개 - 김치
    (2, 4, 100),   # 된장찌개 - 두부
    (2, 5, 50),    # 된장찌개 - 된장
    (3, 2, 250),   # 불고기 - 소고기
    (4, 1, 200),   # 돈까스 - 돼지고기
    (5, 3, 50),    # 잔치국수 - 김치
    (6, 7, 200),   # 비빔밥 - 쌀
    (7, 6, 150),   # 짜장면 - 밀가루
    (12, 8, 15),   # 아메리카노 - 커피원두
    (13, 9, 10),   # 아이스티 - 홍차
]

RIDERS = [
    # (name, phone, status, total_delivery_count)
    # status: WAITING | DELIVERING | OFFLINE
    ("김배달", "010-1111-2222", "WAITING",   42),
    ("이라이더","010-3333-4444", "WAITING",   17),
    ("박퀵",   "010-5555-6666", "OFFLINE",   88),
]

ORDERS = [
    # (customer_id, status, total_price, request_note, estimated_time, created_at, updated_at)
    # status: CREATED|ACCEPTED|COOKING|READY|DELIVERING|COMPLETED|CANCELED
    (2, "COMPLETED", 17000, "빠른 조리 부탁드려요", 20, "2026-04-19 12:30:00", "2026-04-19 13:00:00"),
    (3, "COOKING",   8500,  None,                   12, "2026-04-20 11:00:00", "2026-04-20 11:05:00"),
]

ORDER_ITEMS = [
    # (order_id, menu_id, menu_name, unit_price, quantity, options)
    (1, 1, "김치찌개", 9000, 1, None),
    (1, 12, "아메리카노", 4500, 1, "아이스"),
    (2, 2, "된장찌개",  8500, 1, None),
]

REVIEWS = [
    # (customer_id, menu_id, rating, content, status, moderation_reason, created_at)
    # status: NORMAL | SUSPICIOUS | BLOCKED
    (2, 1, 5, "김치찌개가 정말 맛있어요! 국물이 깊고 진해요.", "NORMAL", None, "2026-04-19 13:10:00"),
    (2, 9, 4, "커피도 맛있고 빠르게 나왔어요.",               "NORMAL", None, "2026-04-19 13:11:00"),
]

# ──────────────────────────────────────────────
# 이하 실행 로직 (수정 불필요)
# ──────────────────────────────────────────────

def is_initialized(con, table):
    """테이블이 존재하고 데이터가 1건 이상이면 True (이미 초기화된 상태)."""
    cur = con.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name=?", (table,))
    if not cur.fetchone():
        return False
    cur.execute(f"SELECT COUNT(*) FROM {table}")
    return cur.fetchone()[0] > 0

def init_auth(path):
    con = sqlite3.connect(path)
    if is_initialized(con, "users"):
        print("[auth.db] 기존 데이터 유지 (초기화 스킵)")
        con.close()
        return
    cur = con.cursor()
    cur.executescript("""
        DROP TABLE IF EXISTS users;
        CREATE TABLE users (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            username   TEXT NOT NULL UNIQUE,
            password   TEXT NOT NULL,
            role       TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
    """)
    for username, plain_pw, role in USERS:
        cur.execute(
            "INSERT INTO users (username, password, role, created_at) VALUES (?,?,?,?)",
            (username, hash_pw(plain_pw), role, NOW)
        )
    con.commit()
    con.close()
    print(f"[auth.db] users {len(USERS)}건 삽입 완료")

def init_core(path):
    con = sqlite3.connect(path)
    if is_initialized(con, "menus"):
        print("[core.db] 기존 데이터 유지 (초기화 스킵)")
        con.close()
        return
    cur = con.cursor()
    cur.executescript("""
        PRAGMA foreign_keys = OFF;

        DROP TABLE IF EXISTS menu_ingredients;
        DROP TABLE IF EXISTS order_items;
        DROP TABLE IF EXISTS reviews;
        DROP TABLE IF EXISTS orders;
        DROP TABLE IF EXISTS menus;
        DROP TABLE IF EXISTS ingredients;
        DROP TABLE IF EXISTS categories;
        DROP TABLE IF EXISTS riders;

        PRAGMA foreign_keys = ON;

        CREATE TABLE categories (
            id   INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE
        );

        CREATE TABLE menus (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            name        TEXT    NOT NULL,
            price       INTEGER NOT NULL,
            image_url   TEXT,
            spicy_level INTEGER NOT NULL DEFAULT 0,
            cook_time   INTEGER NOT NULL DEFAULT 0,
            available   INTEGER NOT NULL DEFAULT 1,
            category_id INTEGER REFERENCES categories(id)
        );

        CREATE TABLE ingredients (
            id    INTEGER PRIMARY KEY AUTOINCREMENT,
            name  TEXT    NOT NULL,
            stock REAL    NOT NULL DEFAULT 0,
            unit  TEXT
        );

        CREATE TABLE menu_ingredients (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            menu_id         INTEGER NOT NULL REFERENCES menus(id),
            ingredient_id   INTEGER NOT NULL REFERENCES ingredients(id),
            required_amount REAL    NOT NULL DEFAULT 0
        );

        CREATE TABLE riders (
            id                   INTEGER PRIMARY KEY AUTOINCREMENT,
            name                 TEXT    NOT NULL,
            phone                TEXT    NOT NULL UNIQUE,
            status               TEXT    NOT NULL,
            total_delivery_count INTEGER NOT NULL DEFAULT 0,
            last_assigned_at     TEXT
        );

        CREATE TABLE orders (
            id             INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id    INTEGER NOT NULL,
            status         TEXT    NOT NULL,
            total_price    INTEGER NOT NULL DEFAULT 0,
            request_note   TEXT,
            estimated_time INTEGER,
            created_at     TEXT    NOT NULL,
            updated_at     TEXT    NOT NULL
        );

        CREATE TABLE order_items (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            order_id  INTEGER NOT NULL REFERENCES orders(id),
            menu_id   INTEGER NOT NULL,
            menu_name TEXT    NOT NULL,
            unit_price INTEGER NOT NULL,
            quantity  INTEGER NOT NULL,
            options   TEXT
        );

        CREATE TABLE reviews (
            id                INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id       INTEGER NOT NULL,
            menu_id           INTEGER NOT NULL,
            rating            INTEGER NOT NULL,
            content           TEXT    NOT NULL,
            status            TEXT    NOT NULL,
            moderation_reason TEXT,
            created_at        TEXT    NOT NULL
        );
    """)

    cur.executemany("INSERT INTO categories (name) VALUES (?)", CATEGORIES)
    cur.executemany("INSERT INTO menus (name, price, image_url, spicy_level, cook_time, available, category_id) VALUES (?,?,?,?,?,?,?)", MENUS)
    cur.executemany("INSERT INTO ingredients (name, stock, unit) VALUES (?,?,?)", INGREDIENTS)
    cur.executemany("INSERT INTO menu_ingredients (menu_id, ingredient_id, required_amount) VALUES (?,?,?)", MENU_INGREDIENTS)
    cur.executemany("INSERT INTO riders (name, phone, status, total_delivery_count) VALUES (?,?,?,?)", RIDERS)
    cur.executemany("INSERT INTO orders (customer_id, status, total_price, request_note, estimated_time, created_at, updated_at) VALUES (?,?,?,?,?,?,?)", ORDERS)
    cur.executemany("INSERT INTO order_items (order_id, menu_id, menu_name, unit_price, quantity, options) VALUES (?,?,?,?,?,?)", ORDER_ITEMS)
    cur.executemany("INSERT INTO reviews (customer_id, menu_id, rating, content, status, moderation_reason, created_at) VALUES (?,?,?,?,?,?,?)", REVIEWS)

    con.commit()
    con.close()
    print(f"[core.db] categories {len(CATEGORIES)}건, menus {len(MENUS)}건, ingredients {len(INGREDIENTS)}건, riders {len(RIDERS)}건, orders {len(ORDERS)}건, reviews {len(REVIEWS)}건 삽입 완료")

if __name__ == "__main__":
    init_auth(AUTH_DB)
    init_core(CORE_DB)
    print("== 완료 ==")
