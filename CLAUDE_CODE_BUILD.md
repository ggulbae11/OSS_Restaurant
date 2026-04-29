# 🍽️ AI 기반 식당 운영 서비스 — Claude Code 빌드 가이드

> 이 파일을 Claude Code에서 열고 아래 지시를 순서대로 실행하세요.  
> 모든 코드는 이 문서에 포함되어 있습니다.

---

## 0. 사전 확인

```bash
# 필수 도구 버전 확인
docker --version          # 24.x 이상
docker compose version    # v2.x 이상
java --version            # 21 이상 (로컬 빌드 시)
python3 --version         # 3.11 이상 (로컬 테스트 시)
node --version            # 20 이상 (프론트 로컬 개발 시)
```

---

## 1. 프로젝트 골격 생성

```bash
mkdir -p restaurant-msa && cd restaurant-msa

# 서비스별 디렉터리
mkdir -p \
  api-gateway \
  auth-service/src/main/{java/com/restaurant/auth/{controller,service,domain,repository,config,dto,security},resources} \
  core-service/src/main/{java/com/restaurant/core/{controller,service,domain,repository,config,dto},resources} \
  ai-service/{routers,services,utils} \
  frontend-customer/src/{components,views,stores,services,router} \
  frontend-admin/src/{components,views,stores,services,router}
```

---

## 2. 환경 변수 파일

```bash
cat > .env << 'EOF'
JWT_SECRET=restaurant-super-secret-key-min-32chars!!
JWT_EXPIRY_SECONDS=3600
OPENAI_API_KEY=sk-your-openai-api-key-here
EOF
```

> ⚠️ `.env`는 절대 Git에 커밋하지 마세요.

---

## 3. docker-compose.yml

```bash
cat > docker-compose.yml << 'YAML'
version: "3.9"

services:
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  auth-service:
    build: ./auth-service
    ports: ["8081:8081"]
    environment:
      - SPRING_REDIS_HOST=redis
      - JWT_SECRET=${JWT_SECRET}
      - JWT_EXPIRY_SECONDS=${JWT_EXPIRY_SECONDS}
    depends_on:
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5

  core-service:
    build: ./core-service
    ports: ["8082:8082"]
    environment:
      - SPRING_REDIS_HOST=redis
      - AUTH_SERVICE_URL=http://auth-service:8081
      - AI_SERVICE_URL=http://ai-service:8000
    volumes: ["core-data:/data"]
    depends_on:
      auth-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5

  ai-service:
    build: ./ai-service
    ports: ["8000:8000"]
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - CORE_SERVICE_URL=http://core-service:8082
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 15s
      timeout: 5s
      retries: 5

  api-gateway:
    build: ./api-gateway
    ports: ["80:80"]
    depends_on:
      - auth-service
      - core-service
      - ai-service

  frontend-customer:
    build: ./frontend-customer
    ports: ["3000:80"]
    depends_on: [api-gateway]

  frontend-admin:
    build: ./frontend-admin
    ports: ["3001:80"]
    depends_on: [api-gateway]

volumes:
  core-data:
YAML
```

---

## 4. API Gateway (Nginx)

### `api-gateway/Dockerfile`

```bash
cat > api-gateway/Dockerfile << 'EOF'
FROM nginx:1.25-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
EOF
```

### `api-gateway/nginx.conf`

```bash
cat > api-gateway/nginx.conf << 'EOF'
upstream auth_service { server auth-service:8081; }
upstream core_service { server core-service:8082; }
upstream ai_service   { server ai-service:8000; }

server {
    listen 80;

    location /auth/ {
        proxy_pass         http://auth_service/;
        proxy_set_header   Host            $host;
        proxy_set_header   X-Real-IP       $remote_addr;
    }

    location /ai/ {
        proxy_pass         http://ai_service/ai/;
        proxy_set_header   Host            $host;
        proxy_read_timeout 120s;
    }

    location /admin/ {
        proxy_pass         http://core_service/admin/;
        proxy_set_header   Authorization   $http_authorization;
    }

    location / {
        proxy_pass         http://core_service/;
        proxy_set_header   Authorization   $http_authorization;
    }
}
EOF
```

---

## 5. Auth Service (Spring Boot)

### `auth-service/Dockerfile`

```bash
cat > auth-service/Dockerfile << 'EOF'
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
VOLUME /data
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF
```

### `auth-service/build.gradle`

```bash
cat > auth-service/build.gradle << 'EOF'
plugins {
    id 'org.springframework.boot' version '3.2.5'
    id 'io.spring.dependency-management' version '1.1.5'
    id 'java'
}
group = 'com.restaurant'
version = '0.0.1'
java { sourceCompatibility = JavaVersion.VERSION_21 }
repositories { mavenCentral() }
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
    runtimeOnly  'io.jsonwebtoken:jjwt-impl:0.12.5'
    runtimeOnly  'io.jsonwebtoken:jjwt-jackson:0.12.5'
    runtimeOnly  'org.xerial:sqlite-jdbc:3.45.3.0'
    runtimeOnly  'org.hibernate.orm:hibernate-community-dialects'
    compileOnly  'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
EOF
```

### `auth-service/src/main/resources/application.yml`

```bash
mkdir -p auth-service/src/main/resources
cat > auth-service/src/main/resources/application.yml << 'EOF'
server:
  port: 8081
spring:
  datasource:
    url: jdbc:sqlite:/data/auth.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate.ddl-auto: update
    database-platform: org.hibernate.community.dialect.SQLiteDialect
  data.redis:
    host: ${SPRING_REDIS_HOST:localhost}
    port: 6379
jwt:
  secret: ${JWT_SECRET:restaurant-secret-key-min-32chars!!}
  expiry-seconds: ${JWT_EXPIRY_SECONDS:3600}
management.endpoints.web.exposure.include: health
EOF
```

### Java 소스 파일 생성

```bash
BASE=auth-service/src/main/java/com/restaurant/auth

# ── AuthServiceApplication.java
cat > $BASE/AuthServiceApplication.java << 'EOF'
package com.restaurant.auth;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
EOF

# ── domain/User.java
cat > $BASE/domain/User.java << 'EOF'
package com.restaurant.auth.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false) private String username;
    @Column(nullable = false)               private String password;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void prePersist() { this.createdAt = LocalDateTime.now(); }
    public enum Role { CUSTOMER, ADMIN, RIDER }
}
EOF

# ── dto/
cat > $BASE/dto/RegisterRequest.java << 'EOF'
package com.restaurant.auth.dto;
import com.restaurant.auth.domain.User.Role;
import jakarta.validation.constraints.*;
public record RegisterRequest(
    @NotBlank @Size(min=3,max=30) String username,
    @NotBlank @Size(min=8)        String password,
    Role role) {}
EOF

cat > $BASE/dto/LoginRequest.java << 'EOF'
package com.restaurant.auth.dto;
import jakarta.validation.constraints.NotBlank;
public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
EOF

cat > $BASE/dto/TokenResponse.java << 'EOF'
package com.restaurant.auth.dto;
public record TokenResponse(String accessToken) {}
EOF

# ── repository/UserRepository.java
cat > $BASE/repository/UserRepository.java << 'EOF'
package com.restaurant.auth.repository;
import com.restaurant.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
EOF

# ── security/JwtProvider.java
cat > $BASE/security/JwtProvider.java << 'EOF'
package com.restaurant.auth.security;
import com.restaurant.auth.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
@Component
public class JwtProvider {
    private final SecretKey key;
    private final long expiryMs;
    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiry-seconds}") long expirySeconds) {
        this.key      = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs = expirySeconds * 1000L;
    }
    public String generate(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(user.getUsername())
            .claim("role",   user.getRole().name())
            .claim("userId", user.getId())
            .issuedAt(new Date(now))
            .expiration(new Date(now + expiryMs))
            .signWith(key).compact();
    }
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(token).getPayload();
    }
}
EOF

# ── service/AuthService.java
cat > $BASE/service/AuthService.java << 'EOF'
package com.restaurant.auth.service;
import com.restaurant.auth.domain.User;
import com.restaurant.auth.domain.User.Role;
import com.restaurant.auth.dto.*;
import com.restaurant.auth.repository.UserRepository;
import com.restaurant.auth.security.JwtProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;
@Service @RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redis;
    private static final String BL = "jwt:blacklist:";
    public void register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username()))
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        userRepository.save(User.builder()
            .username(req.username())
            .password(passwordEncoder.encode(req.password()))
            .role(req.role() != null ? req.role() : Role.CUSTOMER)
            .build());
    }
    public TokenResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
            .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다."));
        if (!passwordEncoder.matches(req.password(), user.getPassword()))
            throw new IllegalArgumentException("아이디 또는 비밀번호가 틀렸습니다.");
        return new TokenResponse(jwtProvider.generate(user));
    }
    public Map<String,Object> verify(String bearer) {
        String token = strip(bearer);
        if (Boolean.TRUE.equals(redis.hasKey(BL + token)))
            throw new IllegalStateException("로그아웃된 토큰입니다.");
        Claims c = jwtProvider.parse(token);
        return Map.of("username", c.getSubject(),
                      "role",     c.get("role", String.class),
                      "userId",   c.get("userId", Long.class));
    }
    public void logout(String bearer) {
        String token = strip(bearer);
        Claims c = jwtProvider.parse(token);
        long ttl = c.getExpiration().getTime() - System.currentTimeMillis();
        if (ttl > 0) redis.opsForValue().set(BL + token, "1", Duration.ofMillis(ttl));
    }
    private String strip(String h) {
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);
        throw new IllegalArgumentException("Authorization 헤더 형식이 잘못됐습니다.");
    }
}
EOF

# ── controller/AuthController.java
cat > $BASE/controller/AuthController.java << 'EOF'
package com.restaurant.auth.controller;
import com.restaurant.auth.dto.*;
import com.restaurant.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String auth) {
        authService.logout(auth);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/verify")
    public ResponseEntity<Map<String,Object>> verify(@RequestHeader("Authorization") String auth) {
        return ResponseEntity.ok(authService.verify(auth));
    }
}
EOF

# ── config/SecurityConfig.java
cat > $BASE/config/SecurityConfig.java << 'EOF'
package com.restaurant.auth.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .build();
    }
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
EOF
```

---

## 6. Core Service (Spring Boot)

### `core-service/Dockerfile`

```bash
cat > core-service/Dockerfile << 'EOF'
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
VOLUME /data
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF
```

### `core-service/build.gradle`

```bash
cat > core-service/build.gradle << 'EOF'
plugins {
    id 'org.springframework.boot' version '3.2.5'
    id 'io.spring.dependency-management' version '1.1.5'
    id 'java'
}
group = 'com.restaurant'
version = '0.0.1'
java { sourceCompatibility = JavaVersion.VERSION_21 }
repositories { mavenCentral() }
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    runtimeOnly  'org.xerial:sqlite-jdbc:3.45.3.0'
    runtimeOnly  'org.hibernate.orm:hibernate-community-dialects'
    compileOnly  'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
EOF
```

### `core-service/src/main/resources/application.yml`

```bash
mkdir -p core-service/src/main/resources
cat > core-service/src/main/resources/application.yml << 'EOF'
server:
  port: 8082
spring:
  datasource:
    url: jdbc:sqlite:/data/core.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    hibernate.ddl-auto: update
    database-platform: org.hibernate.community.dialect.SQLiteDialect
  data.redis:
    host: ${SPRING_REDIS_HOST:localhost}
    port: 6379
services:
  auth-url: ${AUTH_SERVICE_URL:http://localhost:8081}
  ai-url:   ${AI_SERVICE_URL:http://localhost:8000}
management.endpoints.web.exposure.include: health
EOF
```

### Java 소스 파일 생성

```bash
BASE=core-service/src/main/java/com/restaurant/core

# ── Application
cat > $BASE/CoreServiceApplication.java << 'EOF'
package com.restaurant.core;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
@SpringBootApplication @EnableConfigurationProperties
public class CoreServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
EOF

# ── domain: Category
cat > $BASE/domain/Category.java << 'EOF'
package com.restaurant.core.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="categories")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Category {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true) private String name;
}
EOF

# ── domain: Menu
cat > $BASE/domain/Menu.java << 'EOF'
package com.restaurant.core.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="menus")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Menu {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(nullable=false) private int price;
    private String imageUrl;
    private int spicyLevel;   // 0~5
    private int cookTime;     // 분
    private boolean available = true;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="category_id") private Category category;
}
EOF

# ── domain: Ingredient
cat > $BASE/domain/Ingredient.java << 'EOF'
package com.restaurant.core.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="ingredients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ingredient {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    private double stock;
    private String unit;  // g, ml, ea
}
EOF

# ── domain: MenuIngredient
cat > $BASE/domain/MenuIngredient.java << 'EOF'
package com.restaurant.core.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="menu_ingredients")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MenuIngredient {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="menu_id",nullable=false) private Menu menu;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ingredient_id",nullable=false) private Ingredient ingredient;
    private double requiredAmount;  // 1인분 소요량
}
EOF

# ── domain: Order
cat > $BASE/domain/Order.java << 'EOF'
package com.restaurant.core.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
@Entity @Table(name="orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Order {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long customerId;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private OrderStatus status;
    private int totalPrice;
    private String requestNote;
    private Integer estimatedTime;
    @Column(nullable=false, updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @OneToMany(mappedBy="order", cascade=CascadeType.ALL, orphanRemoval=true)
    @Builder.Default private List<OrderItem> items = new ArrayList<>();
    @PrePersist  void prePersist()  { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   void preUpdate()   { updatedAt = LocalDateTime.now(); }
    public enum OrderStatus { CREATED, ACCEPTED, COOKING, READY, DELIVERING, COMPLETED, CANCELED }
}
EOF

# ── domain: OrderItem
cat > $BASE/domain/OrderItem.java << 'EOF'
package com.restaurant.core.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="order_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="order_id",nullable=false) private Order order;
    @Column(nullable=false) private Long menuId;
    @Column(nullable=false) private String menuName;
    @Column(nullable=false) private int unitPrice;
    @Column(nullable=false) private int quantity;
    private String options;  // 쉼표 구분
}
EOF

# ── domain: Review
cat > $BASE/domain/Review.java << 'EOF'
package com.restaurant.core.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="reviews")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Review {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private Long customerId;
    @Column(nullable=false) private Long menuId;
    @Column(nullable=false) private int rating;
    @Column(columnDefinition="TEXT", nullable=false) private String content;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private ReviewStatus status;
    private String moderationReason;
    @Column(nullable=false, updatable=false) private LocalDateTime createdAt;
    @PrePersist void prePersist() { createdAt = LocalDateTime.now(); }
    public enum ReviewStatus { NORMAL, SUSPICIOUS, BLOCKED }
}
EOF

# ── domain: Rider
cat > $BASE/domain/Rider.java << 'EOF'
package com.restaurant.core.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Table(name="riders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Rider {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    @Column(nullable=false, unique=true) private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private RiderStatus status;
    private int totalDeliveryCount;
    private LocalDateTime lastAssignedAt;
    public enum RiderStatus { WAITING, DELIVERING, OFFLINE }
}
EOF

# ── DTOs
cat > $BASE/dto/MenuResponse.java << 'EOF'
package com.restaurant.core.dto;
import com.restaurant.core.domain.Menu;
public record MenuResponse(Long id, String name, int price, String imageUrl,
                           int spicyLevel, int cookTime, boolean available, Long categoryId) {
    public static MenuResponse from(Menu m) {
        return new MenuResponse(m.getId(), m.getName(), m.getPrice(), m.getImageUrl(),
            m.getSpicyLevel(), m.getCookTime(), m.isAvailable(),
            m.getCategory() != null ? m.getCategory().getId() : null);
    }
}
EOF

cat > $BASE/dto/CreateMenuRequest.java << 'EOF'
package com.restaurant.core.dto;
import jakarta.validation.constraints.*;
public record CreateMenuRequest(
    @NotBlank String name, @Min(0) int price,
    @Min(0) @Max(5) int spicyLevel, @Min(1) int cookTime,
    String imageUrl, @NotNull Long categoryId) {}
EOF

cat > $BASE/dto/CreateOrderRequest.java << 'EOF'
package com.restaurant.core.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record CreateOrderRequest(
    @NotEmpty @Valid List<OrderItemRequest> items, String requestNote) {
    public record OrderItemRequest(
        @NotNull Long menuId, @Min(1) int quantity, List<String> options) {}
}
EOF

cat > $BASE/dto/OrderResponse.java << 'EOF'
package com.restaurant.core.dto;
import com.restaurant.core.domain.Order;
import com.restaurant.core.domain.Order.OrderStatus;
import com.restaurant.core.domain.OrderItem;
import java.time.LocalDateTime;
import java.util.List;
public record OrderResponse(Long id, OrderStatus status, int totalPrice,
    String requestNote, Integer estimatedTime, LocalDateTime createdAt,
    List<ItemResponse> items) {
    public record ItemResponse(Long menuId, String menuName,
                               int unitPrice, int quantity, String options) {
        public static ItemResponse from(OrderItem i) {
            return new ItemResponse(i.getMenuId(), i.getMenuName(),
                i.getUnitPrice(), i.getQuantity(), i.getOptions());
        }
    }
    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getStatus(), o.getTotalPrice(),
            o.getRequestNote(), o.getEstimatedTime(), o.getCreatedAt(),
            o.getItems().stream().map(ItemResponse::from).toList());
    }
}
EOF

cat > $BASE/dto/UpdateStatusRequest.java << 'EOF'
package com.restaurant.core.dto;
import com.restaurant.core.domain.Order.OrderStatus;
import jakarta.validation.constraints.NotNull;
public record UpdateStatusRequest(@NotNull OrderStatus status) {}
EOF

cat > $BASE/dto/CreateReviewRequest.java << 'EOF'
package com.restaurant.core.dto;
import jakarta.validation.constraints.*;
public record CreateReviewRequest(
    @NotNull Long menuId, @Min(1) @Max(5) int rating,
    @NotBlank @Size(max=500) String content) {}
EOF

cat > $BASE/dto/ReviewResponse.java << 'EOF'
package com.restaurant.core.dto;
import com.restaurant.core.domain.Review;
import java.time.LocalDateTime;
public record ReviewResponse(Long id, Long menuId, int rating, String content,
    Review.ReviewStatus status, LocalDateTime createdAt) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(r.getId(), r.getMenuId(), r.getRating(),
            r.getContent(), r.getStatus(), r.getCreatedAt());
    }
}
EOF

# ── Repositories
cat > $BASE/repository/CategoryRepository.java << 'EOF'
package com.restaurant.core.repository;
import com.restaurant.core.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CategoryRepository extends JpaRepository<Category, Long> {}
EOF

cat > $BASE/repository/MenuRepository.java << 'EOF'
package com.restaurant.core.repository;
import com.restaurant.core.domain.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findByCategoryIdAndAvailableTrue(Long categoryId);
    List<Menu> findByAvailableTrue();
}
EOF

cat > $BASE/repository/OrderRepository.java << 'EOF'
package com.restaurant.core.repository;
import com.restaurant.core.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    @Query("SELECT COUNT(o) FROM Order o WHERE o.status IN ('ACCEPTED','COOKING','READY')")
    long countActiveOrders();
}
EOF

cat > $BASE/repository/ReviewRepository.java << 'EOF'
package com.restaurant.core.repository;
import com.restaurant.core.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByMenuIdAndStatus(Long menuId, Review.ReviewStatus status);
    List<Review> findByMenuId(Long menuId);
}
EOF

cat > $BASE/repository/RiderRepository.java << 'EOF'
package com.restaurant.core.repository;
import com.restaurant.core.domain.Rider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
public interface RiderRepository extends JpaRepository<Rider, Long> {
    @Query(value = """
        SELECT * FROM riders WHERE status='WAITING'
        ORDER BY last_assigned_at ASC NULLS FIRST, total_delivery_count ASC
        LIMIT 1""", nativeQuery = true)
    Optional<Rider> findFirstAvailableRider();
}
EOF

cat > $BASE/repository/IngredientRepository.java << 'EOF'
package com.restaurant.core.repository;
import com.restaurant.core.domain.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {}
EOF

cat > $BASE/repository/MenuIngredientRepository.java << 'EOF'
package com.restaurant.core.repository;
import com.restaurant.core.domain.MenuIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MenuIngredientRepository extends JpaRepository<MenuIngredient, Long> {
    List<MenuIngredient> findByMenuId(Long menuId);
}
EOF

# ── config: ServiceProperties
cat > $BASE/config/ServiceProperties.java << 'EOF'
package com.restaurant.core.config;
import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Component @ConfigurationProperties(prefix="services")
@Getter @Setter
public class ServiceProperties {
    private String authUrl;
    private String aiUrl;
}
EOF

# ── config: JwtAuthFilter
cat > $BASE/config/JwtAuthFilter.java << 'EOF'
package com.restaurant.core.config;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
@Slf4j @Component @RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final RestTemplate restTemplate;
    private final ServiceProperties props;
    @Override
    protected void doFilterInternal(HttpServletRequest req,
            HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                HttpHeaders h = new HttpHeaders();
                h.set(HttpHeaders.AUTHORIZATION, auth);
                ResponseEntity<Map> r = restTemplate.exchange(
                    props.getAuthUrl() + "/verify", HttpMethod.GET,
                    new HttpEntity<>(h), Map.class);
                if (r.getStatusCode().is2xxSuccessful() && r.getBody() != null) {
                    Map<?,?> b = r.getBody();
                    req.setAttribute("userId",   ((Number) b.get("userId")).longValue());
                    req.setAttribute("username", b.get("username"));
                    SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(b.get("username"), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + b.get("role")))));
                }
            } catch (Exception e) { log.debug("JWT 검증 실패: {}", e.getMessage()); }
        }
        chain.doFilter(req, res);
    }
}
EOF

# ── config: SecurityConfig
cat > $BASE/config/SecurityConfig.java << 'EOF'
package com.restaurant.core.config;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
@Configuration @EnableMethodSecurity @RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/categories/**", "/menus/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
    @Bean public RestTemplate restTemplate() { return new RestTemplate(); }
}
EOF

# ── config: GlobalExceptionHandler
cat > $BASE/config/GlobalExceptionHandler.java << 'EOF'
package com.restaurant.core.config;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
@RestControllerAdvice
public class GlobalExceptionHandler {
    record Err(int status, String message, LocalDateTime timestamp) {}
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Err> bad(IllegalArgumentException e) {
        return err(HttpStatus.BAD_REQUEST, e.getMessage()); }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Err> conflict(IllegalStateException e) {
        return err(HttpStatus.CONFLICT, e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(e.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField,
                f -> f.getDefaultMessage() != null ? f.getDefaultMessage() : "invalid",
                (a,b) -> a))); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Err> generic(Exception e) {
        return err(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."); }
    private ResponseEntity<Err> err(HttpStatus s, String m) {
        return ResponseEntity.status(s).body(new Err(s.value(), m, LocalDateTime.now())); }
}
EOF

# ── service: AiClient
cat > $BASE/service/AiClient.java << 'EOF'
package com.restaurant.core.service;
import com.restaurant.core.config.ServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
@Slf4j @Component @RequiredArgsConstructor
public class AiClient {
    private final RestTemplate restTemplate;
    private final ServiceProperties props;
    public Integer predictTime(long activeOrders, int avgCookTime, int capacity) {
        try {
            ResponseEntity<Map> r = post("/ai/time/predict",
                Map.of("activeOrderCount", activeOrders, "avgCookTime", avgCookTime, "capacity", capacity));
            if (r.getStatusCode().is2xxSuccessful() && r.getBody() != null)
                return ((Number) r.getBody().get("estimatedTime")).intValue();
        } catch (Exception e) { log.warn("AI 시간 예측 실패: {}", e.getMessage()); }
        return capacity > 0 ? (int) Math.ceil((double) activeOrders * avgCookTime / capacity) : null;
    }
    public Map<String,Object> moderateReview(String content) {
        try {
            ResponseEntity<Map> r = post("/ai/review/moderate", Map.of("content", content));
            if (r.getStatusCode().is2xxSuccessful() && r.getBody() != null) return r.getBody();
        } catch (Exception e) { log.warn("AI 검열 실패: {}", e.getMessage()); }
        return Map.of("status","NORMAL","reason","","score",0.0);
    }
    public Map<String,Object> suggestNewMenu(Object salesData) {
        try {
            ResponseEntity<Map> r = post("/ai/menu/new", Map.of("salesData", salesData != null ? salesData : "없음"));
            if (r.getStatusCode().is2xxSuccessful()) return r.getBody();
        } catch (Exception e) { log.warn("AI 신메뉴 추천 실패: {}", e.getMessage()); }
        return Map.of("suggestion","데이터 부족으로 추천 불가");
    }
    public Map<String,Object> optimizeCookSequence(Object items) {
        try {
            ResponseEntity<Map> r = post("/ai/cook/sequence", Map.of("items", items));
            if (r.getStatusCode().is2xxSuccessful()) return r.getBody();
        } catch (Exception e) { log.warn("AI 조리 순서 실패: {}", e.getMessage()); }
        return Map.of("order", items, "reason","AI 서비스 연결 실패");
    }
    private ResponseEntity<Map> post(String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(props.getAiUrl() + path, new HttpEntity<>(body, h), Map.class);
    }
}
EOF

# ── service: MenuService
cat > $BASE/service/MenuService.java << 'EOF'
package com.restaurant.core.service;
import com.restaurant.core.domain.*;
import com.restaurant.core.dto.*;
import com.restaurant.core.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class MenuService {
    private final MenuRepository menuRepository;
    private final CategoryRepository categoryRepository;
    public List<Category> getCategories() { return categoryRepository.findAll(); }
    public List<MenuResponse> getMenus(Long categoryId) {
        List<Menu> menus = categoryId != null
            ? menuRepository.findByCategoryIdAndAvailableTrue(categoryId)
            : menuRepository.findByAvailableTrue();
        return menus.stream().map(MenuResponse::from).toList();
    }
    public MenuResponse getMenu(Long id) {
        return MenuResponse.from(menuRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("메뉴 없음: " + id)));
    }
    @Transactional
    public MenuResponse createMenu(CreateMenuRequest req) {
        Category cat = categoryRepository.findById(req.categoryId())
            .orElseThrow(() -> new IllegalArgumentException("카테고리 없음: " + req.categoryId()));
        return MenuResponse.from(menuRepository.save(Menu.builder()
            .name(req.name()).price(req.price()).spicyLevel(req.spicyLevel())
            .cookTime(req.cookTime()).imageUrl(req.imageUrl()).category(cat).available(true).build()));
    }
    @Transactional
    public MenuResponse updateMenu(Long id, CreateMenuRequest req) {
        Menu m = menuRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("메뉴 없음: " + id));
        Category cat = categoryRepository.findById(req.categoryId())
            .orElseThrow(() -> new IllegalArgumentException("카테고리 없음: " + req.categoryId()));
        m.setName(req.name()); m.setPrice(req.price()); m.setSpicyLevel(req.spicyLevel());
        m.setCookTime(req.cookTime()); m.setImageUrl(req.imageUrl()); m.setCategory(cat);
        return MenuResponse.from(m);
    }
    @Transactional
    public void disableMenu(Long id) {
        menuRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("메뉴 없음: " + id))
            .setAvailable(false);
    }
}
EOF

# ── service: OrderService
cat > $BASE/service/OrderService.java << 'EOF'
package com.restaurant.core.service;
import com.restaurant.core.domain.*;
import com.restaurant.core.domain.Order.OrderStatus;
import com.restaurant.core.domain.Rider.RiderStatus;
import com.restaurant.core.dto.*;
import com.restaurant.core.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Slf4j @Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class OrderService {
    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final MenuIngredientRepository menuIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final RiderRepository riderRepository;
    private final AiClient aiClient;
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest req, Long customerId) {
        Order order = Order.builder().customerId(customerId).status(OrderStatus.CREATED)
            .requestNote(req.requestNote()).build();
        int total = 0;
        for (var ir : req.items()) {
            Menu m = menuRepository.findById(ir.menuId())
                .orElseThrow(() -> new IllegalArgumentException("메뉴 없음: " + ir.menuId()));
            if (!m.isAvailable()) throw new IllegalStateException("판매 중지 메뉴: " + m.getName());
            deductIngredients(m, ir.quantity());
            order.getItems().add(OrderItem.builder().order(order)
                .menuId(m.getId()).menuName(m.getName()).unitPrice(m.getPrice())
                .quantity(ir.quantity())
                .options(ir.options() != null ? String.join(",", ir.options()) : "")
                .build());
            total += m.getPrice() * ir.quantity();
        }
        order.setTotalPrice(total);
        double avgCook = order.getItems().stream()
            .mapToInt(i -> menuRepository.findById(i.getMenuId()).map(Menu::getCookTime).orElse(10))
            .average().orElse(10);
        order.setEstimatedTime(aiClient.predictTime(
            orderRepository.countActiveOrders(), (int) avgCook, 3));
        return OrderResponse.from(orderRepository.save(order));
    }
    public OrderResponse getOrder(Long id) { return OrderResponse.from(find(id)); }
    public List<OrderResponse> getMyOrders(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
            .stream().map(OrderResponse::from).toList();
    }
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream().map(OrderResponse::from).toList();
    }
    @Transactional
    public OrderResponse updateStatus(Long id, UpdateStatusRequest req) {
        Order o = find(id);
        validate(o.getStatus(), req.status());
        o.setStatus(req.status());
        if (req.status() == OrderStatus.DELIVERING) matchRider(o);
        return OrderResponse.from(o);
    }
    private void deductIngredients(Menu menu, int qty) {
        for (MenuIngredient mi : menuIngredientRepository.findByMenuId(menu.getId())) {
            Ingredient ing = mi.getIngredient();
            double need = mi.getRequiredAmount() * qty;
            if (ing.getStock() < need) throw new IllegalStateException(
                "재고 부족: " + ing.getName() + " (필요:" + need + " 재고:" + ing.getStock() + ")");
            ing.setStock(ing.getStock() - need);
        }
    }
    private void matchRider(Order o) {
        Rider r = riderRepository.findFirstAvailableRider()
            .orElseThrow(() -> new IllegalStateException("가용 라이더 없음"));
        r.setStatus(RiderStatus.DELIVERING);
        r.setTotalDeliveryCount(r.getTotalDeliveryCount() + 1);
        r.setLastAssignedAt(LocalDateTime.now());
        riderRepository.save(r);
        log.info("주문 {} → 라이더 {} 매칭", o.getId(), r.getName());
    }
    private void validate(OrderStatus cur, OrderStatus next) {
        boolean ok = switch (cur) {
            case CREATED    -> next == OrderStatus.ACCEPTED || next == OrderStatus.CANCELED;
            case ACCEPTED   -> next == OrderStatus.COOKING  || next == OrderStatus.CANCELED;
            case COOKING    -> next == OrderStatus.READY;
            case READY      -> next == OrderStatus.DELIVERING;
            case DELIVERING -> next == OrderStatus.COMPLETED;
            default -> false;
        };
        if (!ok) throw new IllegalStateException(cur + " → " + next + " 전이 불가");
    }
    private Order find(Long id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + id));
    }
}
EOF

# ── service: ReviewService
cat > $BASE/service/ReviewService.java << 'EOF'
package com.restaurant.core.service;
import com.restaurant.core.domain.Review;
import com.restaurant.core.domain.Review.ReviewStatus;
import com.restaurant.core.dto.*;
import com.restaurant.core.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final AiClient aiClient;
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest req, Long customerId) {
        Map<String,Object> mod = aiClient.moderateReview(req.content());
        ReviewStatus status = switch ((String) mod.getOrDefault("status","NORMAL")) {
            case "SUSPICIOUS" -> ReviewStatus.SUSPICIOUS;
            case "BLOCKED"    -> ReviewStatus.BLOCKED;
            default           -> ReviewStatus.NORMAL;
        };
        return ReviewResponse.from(reviewRepository.save(Review.builder()
            .customerId(customerId).menuId(req.menuId()).rating(req.rating())
            .content(req.content()).status(status)
            .moderationReason((String) mod.getOrDefault("reason","")).build()));
    }
    public List<ReviewResponse> getReviewsByMenu(Long menuId) {
        return reviewRepository.findByMenuIdAndStatus(menuId, ReviewStatus.NORMAL)
            .stream().map(ReviewResponse::from).toList();
    }
    public List<ReviewResponse> getAllReviews() {
        return reviewRepository.findAll().stream().map(ReviewResponse::from).toList();
    }
    @Transactional
    public ReviewResponse updateReviewStatus(Long id, ReviewStatus status) {
        Review r = reviewRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("리뷰 없음: " + id));
        r.setStatus(status);
        return ReviewResponse.from(r);
    }
}
EOF

# ── controller: CustomerController
cat > $BASE/controller/CustomerController.java << 'EOF'
package com.restaurant.core.controller;
import com.restaurant.core.domain.Category;
import com.restaurant.core.dto.*;
import com.restaurant.core.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
class MenuController {
    private final MenuService menuService;
    @GetMapping("/categories")
    public ResponseEntity<List<Category>> cats() { return ResponseEntity.ok(menuService.getCategories()); }
    @GetMapping("/menus")
    public ResponseEntity<List<MenuResponse>> menus(@RequestParam(required=false) Long categoryId) {
        return ResponseEntity.ok(menuService.getMenus(categoryId)); }
    @GetMapping("/menus/{id}")
    public ResponseEntity<MenuResponse> menu(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.getMenu(id)); }
}

@RestController @RequestMapping("/orders") @RequiredArgsConstructor
class OrderController {
    private final OrderService orderService;
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req,
            HttpServletRequest httpReq) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(orderService.createOrder(req, uid(httpReq))); }
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrder(id)); }
    @GetMapping("/my")
    public ResponseEntity<List<OrderResponse>> my(HttpServletRequest httpReq) {
        return ResponseEntity.ok(orderService.getMyOrders(uid(httpReq))); }
    private Long uid(HttpServletRequest r) {
        Object id = r.getAttribute("userId");
        if (id == null) throw new IllegalStateException("인증 정보 없음");
        return ((Number)id).longValue(); }
}

@RestController @RequestMapping("/reviews") @RequiredArgsConstructor
class ReviewController {
    private final ReviewService reviewService;
    @PostMapping
    public ResponseEntity<ReviewResponse> create(@Valid @RequestBody CreateReviewRequest req,
            HttpServletRequest httpReq) {
        Long uid = (Long) httpReq.getAttribute("userId");
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(req, uid)); }
    @GetMapping
    public ResponseEntity<List<ReviewResponse>> get(@RequestParam Long menuId) {
        return ResponseEntity.ok(reviewService.getReviewsByMenu(menuId)); }
}
EOF

# ── controller: AdminController
cat > $BASE/controller/AdminController.java << 'EOF'
package com.restaurant.core.controller;
import com.restaurant.core.domain.Review.ReviewStatus;
import com.restaurant.core.dto.*;
import com.restaurant.core.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')") @RequiredArgsConstructor
public class AdminController {
    private final MenuService menuService;
    private final OrderService orderService;
    private final ReviewService reviewService;
    private final AiClient aiClient;
    @PostMapping("/menus")
    public ResponseEntity<MenuResponse> createMenu(@Valid @RequestBody CreateMenuRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createMenu(req)); }
    @PutMapping("/menus/{id}")
    public ResponseEntity<MenuResponse> updateMenu(@PathVariable Long id,
            @Valid @RequestBody CreateMenuRequest req) { return ResponseEntity.ok(menuService.updateMenu(id, req)); }
    @DeleteMapping("/menus/{id}")
    public ResponseEntity<Void> disableMenu(@PathVariable Long id) {
        menuService.disableMenu(id); return ResponseEntity.noContent().build(); }
    @GetMapping("/orders")
    public ResponseEntity<List<OrderResponse>> orders() { return ResponseEntity.ok(orderService.getAllOrders()); }
    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<OrderResponse> status(@PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest req) { return ResponseEntity.ok(orderService.updateStatus(id, req)); }
    @GetMapping("/reviews")
    public ResponseEntity<List<ReviewResponse>> reviews() { return ResponseEntity.ok(reviewService.getAllReviews()); }
    @PatchMapping("/reviews/{id}/status")
    public ResponseEntity<ReviewResponse> reviewStatus(@PathVariable Long id,
            @RequestParam ReviewStatus status) { return ResponseEntity.ok(reviewService.updateReviewStatus(id, status)); }
    @PostMapping("/ai/menu/suggest")
    public ResponseEntity<Map<String,Object>> suggest(@RequestBody(required=false) Map<String,Object> body) {
        return ResponseEntity.ok(aiClient.suggestNewMenu(body != null ? body.get("salesData") : null)); }
    @PostMapping("/ai/cook/sequence")
    public ResponseEntity<Map<String,Object>> cookSeq(@RequestBody Map<String,Object> body) {
        return ResponseEntity.ok(aiClient.optimizeCookSequence(body.get("items"))); }
}
EOF
```

---

## 7. AI Service (FastAPI)

### `ai-service/Dockerfile`

```bash
cat > ai-service/Dockerfile << 'EOF'
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 8000
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
EOF
```

### `ai-service/requirements.txt`

```bash
cat > ai-service/requirements.txt << 'EOF'
fastapi==0.111.0
uvicorn[standard]==0.29.0
openai==1.30.1
pydantic==2.7.1
slowapi==0.1.9
httpx==0.27.0
python-dotenv==1.0.1
EOF
```

### `ai-service/main.py`

```bash
cat > ai-service/main.py << 'EOF'
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from routers import recommend, review, menu, time_predict, cook

limiter = Limiter(key_func=get_remote_address)
app = FastAPI(title="Restaurant AI Service", version="1.0.0")
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)
app.add_middleware(CORSMiddleware, allow_origins=["*"],
    allow_methods=["*"], allow_headers=["*"])

app.include_router(recommend.router,    prefix="/ai")
app.include_router(review.router,       prefix="/ai")
app.include_router(menu.router,         prefix="/ai")
app.include_router(time_predict.router, prefix="/ai")
app.include_router(cook.router,         prefix="/ai")

@app.get("/health")
async def health(): return {"status": "ok"}

@app.exception_handler(Exception)
async def global_exc(request: Request, exc: Exception):
    return JSONResponse(status_code=500, content={"detail": f"AI 서비스 오류: {str(exc)}"})
EOF
```

### `ai-service/utils/openai_client.py`

```bash
mkdir -p ai-service/utils
cat > ai-service/utils/__init__.py << 'EOF'
EOF

cat > ai-service/utils/openai_client.py << 'EOF'
import os
from openai import AsyncOpenAI

_client: AsyncOpenAI | None = None

def get_client() -> AsyncOpenAI:
    global _client
    if _client is None:
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise RuntimeError("OPENAI_API_KEY 환경변수가 설정되지 않았습니다.")
        _client = AsyncOpenAI(api_key=api_key)
    return _client

async def chat(system: str, user: str, model: str = "gpt-4o-mini") -> str:
    client = get_client()
    resp = await client.chat.completions.create(
        model=model,
        messages=[{"role":"system","content":system},{"role":"user","content":user}],
        temperature=0.7, max_tokens=512)
    return resp.choices[0].message.content.strip()
EOF
```

### AI Routers

```bash
mkdir -p ai-service/routers
cat > ai-service/routers/__init__.py << 'EOF'
EOF

# ── recommend.py
cat > ai-service/routers/recommend.py << 'EOF'
import json, re
from fastapi import APIRouter, Request
from pydantic import BaseModel
from slowapi import Limiter
from slowapi.util import get_remote_address
from utils.openai_client import chat

router  = APIRouter()
limiter = Limiter(key_func=get_remote_address)

class RecommendRequest(BaseModel):
    message: str
    time: str | None = None
    userHistory: list[int] | None = None

class RecommendResponse(BaseModel):
    recommendedMenuIds: list[int]
    reason: str

SYSTEM = """
당신은 한국 식당의 AI 메뉴 추천 어시스턴트입니다.
반드시 JSON으로만 응답: {"recommendedMenuIds": [숫자,...], "reason": "추천 이유"}
menuId는 1~20 정수로 최대 3개, reason은 한국어 2문장 이내.
"""

@router.post("/recommend", response_model=RecommendResponse)
@limiter.limit("30/minute")
async def recommend_menu(request: Request, body: RecommendRequest):
    tl = {"breakfast":"아침","lunch":"점심","dinner":"저녁","snack":"간식"}.get(body.time or "","")
    prompt = f'요청: "{body.message}"\n시간대: {tl or "없음"}\n이전주문: {body.userHistory or []}'
    raw = await chat(SYSTEM, prompt)
    cleaned = re.sub(r"```(?:json)?|```","",raw).strip()
    try:
        d = json.loads(cleaned)
        return RecommendResponse(
            recommendedMenuIds=d.get("recommendedMenuIds",[]),
            reason=d.get("reason",""))
    except:
        return RecommendResponse(recommendedMenuIds=[1,2,3], reason="인기 메뉴를 추천드립니다.")
EOF

# ── review.py
cat > ai-service/routers/review.py << 'EOF'
import json, re
from fastapi import APIRouter, Request
from pydantic import BaseModel, Field
from slowapi import Limiter
from slowapi.util import get_remote_address
from utils.openai_client import chat

router  = APIRouter()
limiter = Limiter(key_func=get_remote_address)

class GenerateReviewRequest(BaseModel):
    menuId: int
    keywords: list[str] = Field(default_factory=list)
    tone: str = "casual"

class GenerateReviewResponse(BaseModel):
    rating: int
    content: str

GEN_SYS = """
한국 식당 리뷰 작성 AI. 반드시 JSON으로만 응답: {"rating": 별점(1~5), "content": "리뷰내용"}
자연스럽게, 50~200자, 욕설/과장/허위 금지, 한국어.
"""

@router.post("/review/generate", response_model=GenerateReviewResponse)
@limiter.limit("20/minute")
async def generate_review(request: Request, body: GenerateReviewRequest):
    tone = "친근하고 편안한 말투" if body.tone == "casual" else "정중하고 격식 있는 말투"
    prompt = f"메뉴ID:{body.menuId}\n키워드:{', '.join(body.keywords) or '없음'}\n말투:{tone}"
    raw = await chat(GEN_SYS, prompt)
    cleaned = re.sub(r"```(?:json)?|```","",raw).strip()
    try:
        d = json.loads(cleaned)
        return GenerateReviewResponse(
            rating=max(1, min(5, int(d.get("rating",4)))),
            content=str(d.get("content","")).strip())
    except:
        return GenerateReviewResponse(rating=4, content="맛있게 잘 먹었습니다. 다음에 또 오고 싶네요.")

class ModerateRequest(BaseModel):
    content: str

class ModerateResponse(BaseModel):
    status: str
    reason: str
    score: float

MOD_SYS = """
식당 리뷰 검열 AI. 분류: NORMAL(정상)/SUSPICIOUS(경계)/BLOCKED(욕설·비방·허위).
반드시 JSON: {"status":"NORMAL|SUSPICIOUS|BLOCKED","reason":"이유","score":0.0~1.0}
"""

@router.post("/review/moderate", response_model=ModerateResponse)
@limiter.limit("60/minute")
async def moderate_review(request: Request, body: ModerateRequest):
    if not body.content.strip():
        return ModerateResponse(status="BLOCKED", reason="빈 내용", score=1.0)
    raw = await chat(MOD_SYS, f'리뷰: "{body.content}"')
    cleaned = re.sub(r"```(?:json)?|```","",raw).strip()
    try:
        d = json.loads(cleaned)
        st = d.get("status","NORMAL")
        if st not in ("NORMAL","SUSPICIOUS","BLOCKED"): st = "NORMAL"
        return ModerateResponse(status=st, reason=str(d.get("reason","")),
            score=float(d.get("score",0.0)))
    except:
        return ModerateResponse(status="NORMAL", reason="처리 오류", score=0.0)
EOF

# ── time_predict.py
cat > ai-service/routers/time_predict.py << 'EOF'
import math
from fastapi import APIRouter, Request
from pydantic import BaseModel, Field
from slowapi import Limiter
from slowapi.util import get_remote_address
from utils.openai_client import chat

router  = APIRouter()
limiter = Limiter(key_func=get_remote_address)

class TimePredictRequest(BaseModel):
    activeOrderCount: int = Field(ge=0)
    avgCookTime:      int = Field(ge=1)
    capacity:         int = Field(ge=1)

class TimePredictResponse(BaseModel):
    estimatedTime: int
    explanation: str

@router.post("/time/predict", response_model=TimePredictResponse)
@limiter.limit("60/minute")
async def predict_time(request: Request, body: TimePredictRequest):
    raw = math.ceil((body.activeOrderCount * body.avgCookTime) / body.capacity)
    estimated = max(raw, body.avgCookTime)
    prompt = (f"대기주문:{body.activeOrderCount}건, 평균조리:{body.avgCookTime}분, "
              f"동시조리:{body.capacity}개, 예상:{estimated}분. "
              "고객에게 친절하게 한 문장으로 안내해주세요.")
    try:
        explanation = await chat("친절한 식당 안내 AI. 한 문장만 반환.", prompt)
    except:
        explanation = f"현재 약 {estimated}분 후 준비될 예정입니다."
    return TimePredictResponse(estimatedTime=estimated, explanation=explanation)
EOF

# ── cook.py
cat > ai-service/routers/cook.py << 'EOF'
import json, re
from fastapi import APIRouter, Request
from pydantic import BaseModel
from slowapi import Limiter
from slowapi.util import get_remote_address
from utils.openai_client import chat

router  = APIRouter()
limiter = Limiter(key_func=get_remote_address)

class CookItem(BaseModel):
    menuId: int
    cookTime: int

class CookSequenceRequest(BaseModel):
    items: list[CookItem]

class CookSequenceResponse(BaseModel):
    order: list[int]
    reason: str

SYSTEM = """
주방 효율 최적화 AI. LPT(Longest Processing Time) 전략 기반.
반드시 JSON: {"order":[menuId,...],"reason":"한국어 설명"}
"""

@router.post("/cook/sequence", response_model=CookSequenceResponse)
@limiter.limit("30/minute")
async def cook_sequence(request: Request, body: CookSequenceRequest):
    if not body.items:
        return CookSequenceResponse(order=[], reason="조리할 메뉴 없음")
    lpt = [i.menuId for i in sorted(body.items, key=lambda x: x.cookTime, reverse=True)]
    desc = "\n".join(f"- menuId={i.menuId}, 조리시간={i.cookTime}분" for i in body.items)
    raw = await chat(SYSTEM, f"메뉴 목록:\n{desc}\n최적 조리 순서를 결정해주세요.")
    cleaned = re.sub(r"```(?:json)?|```","",raw).strip()
    try:
        d = json.loads(cleaned)
        valid = {i.menuId for i in body.items}
        order = [int(x) for x in d.get("order", lpt) if int(x) in valid]
        return CookSequenceResponse(order=order or lpt, reason=str(d.get("reason","")))
    except:
        return CookSequenceResponse(order=lpt, reason="조리 시간이 긴 메뉴부터 시작합니다.")
EOF

# ── menu.py
cat > ai-service/routers/menu.py << 'EOF'
import json, re
from fastapi import APIRouter, Request
from pydantic import BaseModel
from slowapi import Limiter
from slowapi.util import get_remote_address
from utils.openai_client import chat

router  = APIRouter()
limiter = Limiter(key_func=get_remote_address)

class NewMenuResponse(BaseModel):
    suggestion: str
    name: str | None = None
    estimatedPrice: int | None = None
    reason: str | None = None

NEW_SYS = """
한국 식당 메뉴 컨설턴트 AI.
반드시 JSON: {"name":"메뉴명","estimatedPrice":가격,"reason":"이유","suggestion":"설명"}
"""

@router.post("/menu/new", response_model=NewMenuResponse)
@limiter.limit("10/minute")
async def suggest_menu(request: Request, body: dict = {}):
    raw = await chat(NEW_SYS, f"매출 데이터: {body.get('salesData','없음')}\n신메뉴 1개 추천.")
    cleaned = re.sub(r"```(?:json)?|```","",raw).strip()
    try:
        d = json.loads(cleaned)
        return NewMenuResponse(suggestion=str(d.get("suggestion","")),
            name=d.get("name"), estimatedPrice=d.get("estimatedPrice"), reason=d.get("reason"))
    except:
        return NewMenuResponse(suggestion="데이터 부족으로 추천 어려움")

class PolicyCheckRequest(BaseModel):
    name: str; price: int; description: str | None = None

class PolicyCheckResponse(BaseModel):
    passed: bool; issues: list[str]

POLICY_SYS = """
메뉴 정책 검사 AI. 기준: 이름(2~30자, 욕설금지), 가격(100~100000원), 설명(허위금지).
JSON: {"passed":true/false,"issues":["문제점",...]}
"""

@router.post("/policy/check", response_model=PolicyCheckResponse)
@limiter.limit("30/minute")
async def policy_check(request: Request, body: PolicyCheckRequest):
    raw = await chat(POLICY_SYS, f"이름:{body.name}\n가격:{body.price}\n설명:{body.description or '없음'}")
    cleaned = re.sub(r"```(?:json)?|```","",raw).strip()
    try:
        d = json.loads(cleaned)
        return PolicyCheckResponse(passed=bool(d.get("passed",True)), issues=list(d.get("issues",[])))
    except:
        return PolicyCheckResponse(passed=True, issues=[])

class QualityCheckRequest(BaseModel):
    menuId: int; reviewSummary: str | None = None; avgRating: float | None = None

class QualityCheckResponse(BaseModel):
    score: float; feedback: str; action: str  # KEEP|IMPROVE|REMOVE

QUALITY_SYS = """
메뉴 품질 관리 AI. JSON: {"score":0~10,"feedback":"한국어","action":"KEEP|IMPROVE|REMOVE"}
"""

@router.post("/quality/check", response_model=QualityCheckResponse)
@limiter.limit("20/minute")
async def quality_check(request: Request, body: QualityCheckRequest):
    raw = await chat(QUALITY_SYS,
        f"메뉴ID:{body.menuId}\n평균별점:{body.avgRating}\n리뷰요약:{body.reviewSummary or '없음'}")
    cleaned = re.sub(r"```(?:json)?|```","",raw).strip()
    try:
        d = json.loads(cleaned)
        action = d.get("action","KEEP")
        if action not in ("KEEP","IMPROVE","REMOVE"): action = "KEEP"
        return QualityCheckResponse(
            score=round(min(max(float(d.get("score",5.0)),0),10),1),
            feedback=str(d.get("feedback","")), action=action)
    except:
        return QualityCheckResponse(score=5.0, feedback="분석 실패", action="KEEP")
EOF
```

---

## 8. Frontend - Customer (Vue3)

### `frontend-customer/Dockerfile`

```bash
cat > frontend-customer/Dockerfile << 'EOF'
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.25-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EOF

cat > frontend-customer/nginx.conf << 'EOF'
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;
    location / { try_files $uri $uri/ /index.html; }
    location /api/ {
        proxy_pass http://api-gateway:80/;
        proxy_set_header Authorization $http_authorization;
    }
}
EOF
```

### `frontend-customer/package.json`

```bash
cat > frontend-customer/package.json << 'EOF'
{
  "name": "frontend-customer",
  "version": "0.0.1",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.3.0",
    "pinia": "^2.1.0",
    "axios": "^1.7.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "vite": "^5.2.0"
  }
}
EOF
```

### `frontend-customer/vite.config.js`

```bash
cat > frontend-customer/vite.config.js << 'EOF'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:80', changeOrigin: true, rewrite: p => p.replace(/^\/api/, '') }
    }
  }
})
EOF
```

### `frontend-customer/index.html`

```bash
cat > frontend-customer/index.html << 'EOF'
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>식당 주문</title>
</head>
<body>
  <div id="app"></div>
  <script type="module" src="/src/main.js"></script>
</body>
</html>
EOF
```

### Vue 소스 파일

```bash
mkdir -p frontend-customer/src/{components,views,stores,services,router}

cat > frontend-customer/src/main.js << 'EOF'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router/index.js'
const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
EOF

cat > frontend-customer/src/App.vue << 'EOF'
<template>
  <div id="root">
    <nav class="nav">
      <span class="logo">🍽️ 우리 식당</span>
      <router-link to="/">메뉴</router-link>
      <router-link to="/cart">장바구니</router-link>
      <router-link to="/orders">주문내역</router-link>
      <span v-if="!authStore.token">
        <router-link to="/login">로그인</router-link>
      </span>
      <span v-else>
        <button @click="authStore.logout">로그아웃</button>
      </span>
    </nav>
    <main><router-view/></main>
  </div>
</template>
<script setup>
import { useAuthStore } from './stores/auth.js'
const authStore = useAuthStore()
</script>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: sans-serif; background: #f5f5f5; }
.nav { background: #ff6b35; color: white; padding: 12px 24px;
  display: flex; align-items: center; gap: 16px; }
.logo { font-size: 1.2rem; font-weight: bold; margin-right: auto; }
.nav a { color: white; text-decoration: none; }
.nav button { background: transparent; border: 1px solid white;
  color: white; padding: 4px 12px; border-radius: 4px; cursor: pointer; }
main { max-width: 1200px; margin: 0 auto; padding: 24px; }
</style>
EOF

cat > frontend-customer/src/router/index.js << 'EOF'
import { createRouter, createWebHistory } from 'vue-router'
import MenuView    from '../views/MenuView.vue'
import CartView    from '../views/CartView.vue'
import OrdersView  from '../views/OrdersView.vue'
import LoginView   from '../views/LoginView.vue'
export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/',       component: MenuView },
    { path: '/cart',   component: CartView },
    { path: '/orders', component: OrdersView },
    { path: '/login',  component: LoginView },
  ]
})
EOF

cat > frontend-customer/src/services/api.js << 'EOF'
import axios from 'axios'
const api = axios.create({ baseURL: '/api' })
api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
export default api
EOF

cat > frontend-customer/src/stores/auth.js << 'EOF'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '../services/api.js'
export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token'))
  async function login(username, password) {
    const res = await api.post('/auth/login', { username, password })
    token.value = res.data.accessToken
    localStorage.setItem('token', token.value)
  }
  function logout() {
    token.value = null
    localStorage.removeItem('token')
  }
  return { token, login, logout }
})
EOF

cat > frontend-customer/src/stores/cart.js << 'EOF'
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
export const useCartStore = defineStore('cart', () => {
  const items = ref(JSON.parse(localStorage.getItem('cart') || '[]'))
  const total = computed(() => items.value.reduce((s,i) => s + i.price * i.quantity, 0))
  function save() { localStorage.setItem('cart', JSON.stringify(items.value)) }
  function add(menu, options = []) {
    const key = menu.id + '|' + options.join(',')
    const ex  = items.value.find(i => i.menuId + '|' + (i.options||[]).join(',') === key)
    if (ex) { ex.quantity++ }
    else { items.value.push({ menuId: menu.id, name: menu.name, price: menu.price, quantity: 1, options }) }
    save()
  }
  function remove(index) { items.value.splice(index, 1); save() }
  function clear() { items.value = []; save() }
  return { items, total, add, remove, clear }
})
EOF

cat > frontend-customer/src/views/LoginView.vue << 'EOF'
<template>
  <div class="login-box">
    <h2>로그인</h2>
    <input v-model="username" placeholder="아이디"/>
    <input v-model="password" type="password" placeholder="비밀번호"/>
    <button @click="submit">로그인</button>
    <p class="err" v-if="error">{{ error }}</p>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
const username = ref(''), password = ref(''), error = ref('')
const router = useRouter()
const auth   = useAuthStore()
async function submit() {
  try { await auth.login(username.value, password.value); router.push('/') }
  catch { error.value = '로그인 실패. 아이디/비밀번호를 확인하세요.' }
}
</script>
<style scoped>
.login-box { max-width: 360px; margin: 80px auto; background: white;
  padding: 32px; border-radius: 12px; display: flex; flex-direction: column; gap: 12px; }
input { padding: 10px; border: 1px solid #ddd; border-radius: 6px; font-size: 1rem; }
button { padding: 12px; background: #ff6b35; color: white; border: none;
  border-radius: 6px; cursor: pointer; font-size: 1rem; }
.err { color: red; font-size: 0.85rem; }
</style>
EOF

cat > frontend-customer/src/views/MenuView.vue << 'EOF'
<template>
  <div>
    <!-- AI 추천 -->
    <div class="ai-box">
      <h3>🤖 AI 메뉴 추천</h3>
      <div class="ai-row">
        <input v-model="aiMessage" placeholder="예: 매콤한 거 추천해줘" @keyup.enter="recommend"/>
        <button @click="recommend" :disabled="aiLoading">추천받기</button>
      </div>
      <p v-if="aiReason" class="ai-reason">{{ aiReason }}</p>
    </div>
    <!-- 카테고리 탭 -->
    <div class="tabs">
      <button :class="{ active: !selectedCat }" @click="selectedCat = null">전체</button>
      <button v-for="c in categories" :key="c.id"
        :class="{ active: selectedCat === c.id }" @click="selectedCat = c.id">{{ c.name }}</button>
    </div>
    <!-- 메뉴 그리드 -->
    <div class="grid">
      <div v-for="m in filteredMenus" :key="m.id"
        class="card" :class="{ recommended: recommendedIds.includes(m.id) }">
        <img v-if="m.imageUrl" :src="m.imageUrl" alt=""/>
        <div class="card-body">
          <h4>{{ m.name }}</h4>
          <p>🌶️ {{ m.spicyLevel }} | ⏱ {{ m.cookTime }}분</p>
          <p class="price">{{ m.price.toLocaleString() }}원</p>
          <button @click="addToCart(m)">담기</button>
        </div>
      </div>
    </div>
    <div v-if="added" class="toast">장바구니에 담겼습니다!</div>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../services/api.js'
import { useCartStore } from '../stores/cart.js'

const categories     = ref([])
const menus          = ref([])
const selectedCat    = ref(null)
const aiMessage      = ref('')
const aiReason       = ref('')
const aiLoading      = ref(false)
const recommendedIds = ref([])
const added          = ref(false)
const cart           = useCartStore()

const filteredMenus = computed(() =>
  selectedCat.value ? menus.value.filter(m => m.categoryId === selectedCat.value) : menus.value)

onMounted(async () => {
  const [catRes, menuRes] = await Promise.all([api.get('/categories'), api.get('/menus')])
  categories.value = catRes.data
  menus.value      = menuRes.data
})

async function recommend() {
  if (!aiMessage.value.trim()) return
  aiLoading.value = true
  try {
    const res = await api.post('/ai/recommend', { message: aiMessage.value })
    recommendedIds.value = res.data.recommendedMenuIds
    aiReason.value       = res.data.reason
  } finally { aiLoading.value = false }
}

function addToCart(m) {
  cart.add(m)
  added.value = true
  setTimeout(() => added.value = false, 2000)
}
</script>
<style scoped>
.ai-box { background: #fff8f5; border: 1px solid #ffc4a8; border-radius: 10px;
  padding: 16px; margin-bottom: 20px; }
.ai-row { display: flex; gap: 8px; margin-top: 8px; }
.ai-row input { flex: 1; padding: 8px; border: 1px solid #ddd; border-radius: 6px; }
.ai-row button { padding: 8px 16px; background: #ff6b35; color: white;
  border: none; border-radius: 6px; cursor: pointer; }
.ai-reason { margin-top: 8px; color: #555; font-size: 0.9rem; }
.tabs { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
.tabs button { padding: 6px 16px; border: 1px solid #ddd; border-radius: 20px;
  background: white; cursor: pointer; }
.tabs button.active { background: #ff6b35; color: white; border-color: #ff6b35; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.card { background: white; border-radius: 10px; overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,.08); transition: transform .2s; }
.card:hover { transform: translateY(-2px); }
.card.recommended { border: 2px solid #ff6b35; }
.card img { width: 100%; height: 140px; object-fit: cover; }
.card-body { padding: 12px; }
.card-body h4 { margin-bottom: 4px; }
.card-body p { font-size: 0.85rem; color: #666; }
.price { font-size: 1rem !important; color: #ff6b35 !important; font-weight: bold; }
.card-body button { margin-top: 8px; width: 100%; padding: 8px;
  background: #ff6b35; color: white; border: none; border-radius: 6px; cursor: pointer; }
.toast { position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: #333; color: white; padding: 12px 24px; border-radius: 24px; font-size: .9rem; }
</style>
EOF

cat > frontend-customer/src/views/CartView.vue << 'EOF'
<template>
  <div class="cart">
    <h2>🛒 장바구니</h2>
    <div v-if="cart.items.length === 0" class="empty">장바구니가 비어 있습니다.</div>
    <div v-else>
      <div v-for="(item, i) in cart.items" :key="i" class="item">
        <span class="name">{{ item.name }}</span>
        <span v-if="item.options?.length" class="opts">{{ item.options.join(', ') }}</span>
        <span>{{ item.price.toLocaleString() }}원 × {{ item.quantity }}</span>
        <button @click="cart.remove(i)">삭제</button>
      </div>
      <div class="total">합계: {{ cart.total.toLocaleString() }}원</div>
      <textarea v-model="note" placeholder="요청 사항 (예: 덜 맵게)"></textarea>
      <button class="order-btn" @click="placeOrder">주문하기</button>
    </div>
    <div v-if="result" class="result">
      <p>✅ 주문 완료! 주문번호: {{ result.id }}</p>
      <p>예상 대기 시간: {{ result.estimatedTime }}분</p>
    </div>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { useCartStore } from '../stores/cart.js'
import api from '../services/api.js'
const cart   = useCartStore()
const note   = ref('')
const result = ref(null)
async function placeOrder() {
  if (!cart.items.length) return
  const res = await api.post('/orders', {
    items: cart.items.map(i => ({ menuId: i.menuId, quantity: i.quantity, options: i.options })),
    requestNote: note.value
  })
  result.value = res.data
  cart.clear()
}
</script>
<style scoped>
.cart { max-width: 600px; }
.empty { color: #888; margin-top: 40px; text-align: center; }
.item { background: white; padding: 12px 16px; border-radius: 8px;
  margin-bottom: 8px; display: flex; align-items: center; gap: 12px; }
.name { font-weight: bold; flex: 1; }
.opts { color: #888; font-size: .85rem; }
.item button { padding: 4px 10px; border: 1px solid #ff6b35; color: #ff6b35;
  background: white; border-radius: 4px; cursor: pointer; }
.total { text-align: right; font-size: 1.1rem; font-weight: bold;
  color: #ff6b35; margin: 12px 0; }
textarea { width: 100%; padding: 10px; border: 1px solid #ddd;
  border-radius: 6px; resize: vertical; min-height: 60px; }
.order-btn { margin-top: 12px; width: 100%; padding: 14px;
  background: #ff6b35; color: white; border: none; border-radius: 8px;
  font-size: 1.1rem; cursor: pointer; }
.result { margin-top: 20px; background: #f0fff4; border: 1px solid #6fcf97;
  border-radius: 8px; padding: 16px; }
</style>
EOF

cat > frontend-customer/src/views/OrdersView.vue << 'EOF'
<template>
  <div>
    <h2>📦 주문 내역</h2>
    <div v-if="orders.length === 0" class="empty">주문 내역이 없습니다.</div>
    <div v-for="o in orders" :key="o.id" class="order-card">
      <div class="order-header">
        <span>주문 #{{ o.id }}</span>
        <span class="badge" :class="o.status">{{ statusLabel(o.status) }}</span>
      </div>
      <p>합계: {{ o.totalPrice.toLocaleString() }}원</p>
      <p v-if="o.estimatedTime">예상 대기: {{ o.estimatedTime }}분</p>
      <div v-for="item in o.items" :key="item.menuId" class="item-row">
        {{ item.menuName }} × {{ item.quantity }}
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import api from '../services/api.js'
const orders = ref([])
onMounted(async () => {
  try { orders.value = (await api.get('/orders/my')).data }
  catch { orders.value = [] }
})
function statusLabel(s) {
  return { CREATED:'접수됨', ACCEPTED:'확인됨', COOKING:'조리중',
           READY:'완료', DELIVERING:'배달중', COMPLETED:'배달완료', CANCELED:'취소' }[s] || s
}
</script>
<style scoped>
.empty { color: #888; margin-top: 40px; text-align: center; }
.order-card { background: white; border-radius: 10px; padding: 16px;
  margin-bottom: 12px; box-shadow: 0 2px 6px rgba(0,0,0,.06); }
.order-header { display: flex; justify-content: space-between; margin-bottom: 8px; font-weight: bold; }
.badge { padding: 2px 10px; border-radius: 12px; font-size: .8rem; color: white; background: #888; }
.badge.COOKING { background: #f0a500; } .badge.DELIVERING { background: #2196f3; }
.badge.COMPLETED { background: #4caf50; } .badge.CANCELED { background: #e53935; }
.item-row { font-size: .9rem; color: #555; margin-top: 4px; }
</style>
EOF
```

---

## 9. Frontend - Admin (Vue3)

```bash
cp frontend-customer/package.json frontend-admin/package.json
cp frontend-customer/vite.config.js frontend-admin/vite.config.js
sed -i 's/frontend-customer/frontend-admin/' frontend-admin/package.json
cp -r frontend-customer/nginx.conf frontend-admin/nginx.conf

cat > frontend-admin/Dockerfile << 'EOF'
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM nginx:1.25-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EOF

cat > frontend-admin/index.html << 'EOF'
<!DOCTYPE html>
<html lang="ko">
<head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>식당 관리자</title></head>
<body><div id="app"></div><script type="module" src="/src/main.js"></script></body>
</html>
EOF

mkdir -p frontend-admin/src/{views,stores,services,router}

cat > frontend-admin/src/main.js << 'EOF'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router/index.js'
createApp(App).use(createPinia()).use(router).mount('#app')
EOF

cat > frontend-admin/src/services/api.js << 'EOF'
import axios from 'axios'
const api = axios.create({ baseURL: '/api' })
api.interceptors.request.use(c => {
  const t = localStorage.getItem('adminToken')
  if (t) c.headers.Authorization = `Bearer ${t}`
  return c
})
export default api
EOF

cat > frontend-admin/src/router/index.js << 'EOF'
import { createRouter, createWebHistory } from 'vue-router'
import LoginView  from '../views/LoginView.vue'
import OrdersView from '../views/OrdersView.vue'
import MenusView  from '../views/MenusView.vue'
export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/',       redirect: '/orders' },
    { path: '/login',  component: LoginView },
    { path: '/orders', component: OrdersView },
    { path: '/menus',  component: MenusView },
  ]
})
EOF

cat > frontend-admin/src/App.vue << 'EOF'
<template>
  <div>
    <nav class="nav">
      <span class="logo">🔧 관리자</span>
      <router-link to="/orders">주문 관리</router-link>
      <router-link to="/menus">메뉴 관리</router-link>
      <button @click="logout">로그아웃</button>
    </nav>
    <main><router-view/></main>
  </div>
</template>
<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
function logout() { localStorage.removeItem('adminToken'); router.push('/login') }
</script>
<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body { font-family: sans-serif; background: #f0f2f5; }
.nav { background: #1a1a2e; color: white; padding: 12px 24px;
  display: flex; align-items: center; gap: 16px; }
.logo { font-size: 1.2rem; font-weight: bold; margin-right: auto; }
.nav a { color: #a0a8c0; text-decoration: none; }
.nav a:hover, .nav a.router-link-active { color: white; }
.nav button { background: transparent; border: 1px solid #a0a8c0;
  color: #a0a8c0; padding: 4px 12px; border-radius: 4px; cursor: pointer; }
main { max-width: 1200px; margin: 0 auto; padding: 24px; }
</style>
EOF

cat > frontend-admin/src/views/LoginView.vue << 'EOF'
<template>
  <div class="box">
    <h2>관리자 로그인</h2>
    <input v-model="u" placeholder="아이디"/>
    <input v-model="p" type="password" placeholder="비밀번호"/>
    <button @click="login">로그인</button>
    <p class="err" v-if="err">{{ err }}</p>
  </div>
</template>
<script setup>
import { ref } from 'vue'; import { useRouter } from 'vue-router'; import api from '../services/api.js'
const u = ref(''), p = ref(''), err = ref(''), router = useRouter()
async function login() {
  try {
    const res = await api.post('/auth/login', { username: u.value, password: p.value })
    localStorage.setItem('adminToken', res.data.accessToken)
    router.push('/orders')
  } catch { err.value = '로그인 실패' }
}
</script>
<style scoped>
.box { max-width: 360px; margin: 80px auto; background: white; padding: 32px;
  border-radius: 12px; display: flex; flex-direction: column; gap: 12px; box-shadow: 0 4px 20px rgba(0,0,0,.1); }
input { padding: 10px; border: 1px solid #ddd; border-radius: 6px; }
button { padding: 12px; background: #1a1a2e; color: white; border: none; border-radius: 6px; cursor: pointer; }
.err { color: red; font-size: .85rem; }
</style>
EOF

cat > frontend-admin/src/views/OrdersView.vue << 'EOF'
<template>
  <div>
    <h2>주문 관리</h2>
    <!-- 조리 순서 최적화 버튼 -->
    <button class="ai-btn" @click="optimizeCook">🤖 AI 조리 순서 최적화</button>
    <p v-if="cookReason" class="ai-reason">{{ cookReason }}</p>
    <div class="orders">
      <div v-for="o in orders" :key="o.id" class="card">
        <div class="hd">
          <strong>주문 #{{ o.id }}</strong>
          <span class="badge" :class="o.status">{{ o.status }}</span>
        </div>
        <p>{{ o.totalPrice.toLocaleString() }}원</p>
        <p v-if="o.requestNote" class="note">요청: {{ o.requestNote }}</p>
        <div class="items">
          <span v-for="i in o.items" :key="i.menuId">{{ i.menuName }}×{{ i.quantity }} </span>
        </div>
        <div class="actions">
          <button v-for="s in nextStatuses(o.status)" :key="s" @click="updateStatus(o.id, s)">
            → {{ s }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import api from '../services/api.js'
const orders = ref([]), cookReason = ref('')
onMounted(load)
async function load() {
  try { orders.value = (await api.get('/admin/orders')).data } catch {}
}
async function updateStatus(id, status) {
  await api.patch(`/admin/orders/${id}/status`, { status })
  await load()
}
async function optimizeCook() {
  const activeItems = orders.value
    .filter(o => ['ACCEPTED','COOKING'].includes(o.status))
    .flatMap(o => o.items.map(i => ({ menuId: i.menuId, cookTime: 10 })))
  if (!activeItems.length) return alert('활성 주문 없음')
  const res = await api.post('/admin/ai/cook/sequence', { items: activeItems })
  cookReason.value = res.data.reason
}
function nextStatuses(s) {
  return { CREATED:['ACCEPTED','CANCELED'], ACCEPTED:['COOKING','CANCELED'],
    COOKING:['READY'], READY:['DELIVERING'], DELIVERING:['COMPLETED'] }[s] || []
}
</script>
<style scoped>
h2 { margin-bottom: 16px; }
.ai-btn { padding: 8px 16px; background: #6c63ff; color: white;
  border: none; border-radius: 6px; cursor: pointer; margin-bottom: 8px; }
.ai-reason { font-size: .9rem; color: #555; margin-bottom: 16px; }
.orders { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px,1fr)); gap: 12px; }
.card { background: white; border-radius: 10px; padding: 16px; box-shadow: 0 2px 8px rgba(0,0,0,.07); }
.hd { display: flex; justify-content: space-between; margin-bottom: 8px; }
.badge { padding: 2px 8px; border-radius: 10px; font-size: .75rem;
  color: white; background: #888; }
.badge.COOKING { background: #f0a500; } .badge.READY { background: #4caf50; }
.badge.DELIVERING { background: #2196f3; } .badge.CANCELED { background: #e53935; }
.note { color: #888; font-size: .85rem; }
.items { font-size: .85rem; color: #555; margin: 6px 0; }
.actions { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
.actions button { padding: 4px 10px; border: 1px solid #1a1a2e; background: white;
  border-radius: 4px; cursor: pointer; font-size: .8rem; }
</style>
EOF

cat > frontend-admin/src/views/MenusView.vue << 'EOF'
<template>
  <div>
    <div class="top">
      <h2>메뉴 관리</h2>
      <button class="ai-btn" @click="suggestMenu">🤖 AI 신메뉴 추천</button>
    </div>
    <div v-if="suggestion" class="suggestion">
      <strong>{{ suggestion.name }}</strong> — {{ suggestion.estimatedPrice?.toLocaleString() }}원
      <p>{{ suggestion.reason }}</p>
    </div>
    <!-- 메뉴 생성 폼 -->
    <div class="form-card">
      <h3>새 메뉴 등록</h3>
      <input v-model="form.name"       placeholder="메뉴 이름"/>
      <input v-model.number="form.price" placeholder="가격" type="number"/>
      <input v-model.number="form.cookTime" placeholder="조리 시간(분)" type="number"/>
      <input v-model.number="form.spicyLevel" placeholder="맵기 (0~5)" type="number"/>
      <input v-model.number="form.categoryId" placeholder="카테고리 ID" type="number"/>
      <button @click="createMenu">등록</button>
    </div>
    <!-- 메뉴 목록 -->
    <div class="grid">
      <div v-for="m in menus" :key="m.id" class="mcard">
        <strong>{{ m.name }}</strong>
        <span class="price">{{ m.price.toLocaleString() }}원</span>
        <span class="tag" :class="m.available ? 'on':'off'">{{ m.available?'판매중':'중지' }}</span>
        <button v-if="m.available" @click="disable(m.id)">판매 중지</button>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import api from '../services/api.js'
const menus = ref([]), suggestion = ref(null)
const form  = ref({ name:'', price:0, cookTime:10, spicyLevel:0, categoryId:1 })
onMounted(load)
async function load() {
  try { menus.value = (await api.get('/menus')).data } catch {}
}
async function createMenu() {
  await api.post('/admin/menus', form.value); await load()
  form.value = { name:'', price:0, cookTime:10, spicyLevel:0, categoryId:1 }
}
async function disable(id) { await api.delete(`/admin/menus/${id}`); await load() }
async function suggestMenu() {
  const res = await api.post('/admin/ai/menu/suggest', {})
  suggestion.value = res.data
}
</script>
<style scoped>
.top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.ai-btn { padding: 8px 16px; background: #6c63ff; color: white; border: none; border-radius: 6px; cursor: pointer; }
.suggestion { background: #f0ebff; border: 1px solid #b39ddb; border-radius: 8px;
  padding: 12px; margin-bottom: 16px; }
.form-card { background: white; border-radius: 10px; padding: 20px;
  display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 20px; box-shadow: 0 2px 8px rgba(0,0,0,.07); }
.form-card h3 { grid-column: 1/-1; }
.form-card button { grid-column: 1/-1; padding: 12px; background: #1a1a2e;
  color: white; border: none; border-radius: 6px; cursor: pointer; }
input { padding: 8px; border: 1px solid #ddd; border-radius: 6px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px,1fr)); gap: 12px; }
.mcard { background: white; padding: 14px; border-radius: 8px;
  box-shadow: 0 2px 6px rgba(0,0,0,.06); display: flex; flex-direction: column; gap: 6px; }
.price { color: #ff6b35; font-weight: bold; }
.tag { padding: 2px 8px; border-radius: 10px; font-size: .75rem; width: fit-content; }
.tag.on { background: #e8f5e9; color: #388e3c; }
.tag.off { background: #ffebee; color: #c62828; }
.mcard button { padding: 6px; border: 1px solid #e53935; color: #e53935;
  background: white; border-radius: 4px; cursor: pointer; font-size: .8rem; }
</style>
EOF
```

---

## 10. gradlew 권한 설정 (auth-service, core-service)

```bash
# gradlew 스크립트가 없다면 Gradle Wrapper 생성
for svc in auth-service core-service; do
  cd $svc
  gradle wrapper --gradle-version 8.7 2>/dev/null || true
  chmod +x gradlew 2>/dev/null || true
  cd ..
done
```

> Gradle이 로컬에 없는 경우 아래 방법으로 Wrapper를 직접 다운로드합니다.

```bash
for svc in auth-service core-service; do
  mkdir -p $svc/gradle/wrapper
  curl -sL https://services.gradle.org/distributions/gradle-8.7-bin.zip -o /tmp/gradle.zip 2>/dev/null

  cat > $svc/gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

  curl -sL https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradlew -o $svc/gradlew
  chmod +x $svc/gradlew
done
```

---

## 11. 빌드 및 실행

```bash
# 1. 전체 서비스 빌드 후 실행
docker compose --env-file .env up --build

# 2. 백그라운드 실행
docker compose --env-file .env up --build -d

# 3. 로그 확인
docker compose logs -f core-service
docker compose logs -f ai-service
```

---

## 12. 동작 확인

```bash
# ── Health Check ─────────────────────────────────────────────
curl http://localhost:8081/actuator/health   # auth
curl http://localhost:8082/actuator/health   # core
curl http://localhost:8000/health            # ai

# ── 회원가입 / 로그인 ────────────────────────────────────────
curl -X POST http://localhost/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"password123","role":"ADMIN"}'

TOKEN=$(curl -s -X POST http://localhost/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"password123"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

echo "TOKEN=$TOKEN"

# ── 메뉴 조회 ────────────────────────────────────────────────
curl http://localhost/menus

# ── AI 메뉴 추천 ─────────────────────────────────────────────
curl -X POST http://localhost/ai/recommend \
  -H "Content-Type: application/json" \
  -d '{"message":"매콤한 거 추천해줘","time":"dinner","userHistory":[1,2]}'

# ── 주문 생성 ────────────────────────────────────────────────
curl -X POST http://localhost/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"items":[{"menuId":1,"quantity":2,"options":["곱빼기"]}],"requestNote":"덜 맵게"}'

# ── 관리자: 주문 상태 변경 ───────────────────────────────────
curl -X PATCH http://localhost/admin/orders/1/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"status":"ACCEPTED"}'
```

---

## 13. 접속 URL 요약

| 서비스 | URL |
|---|---|
| 고객 웹 | http://localhost:3000 |
| 관리자 웹 | http://localhost:3001 |
| API Gateway | http://localhost:80 |
| Auth Service | http://localhost:8081 |
| Core Service | http://localhost:8082 |
| AI Service | http://localhost:8000 |

---

## 14. 종료 및 초기화

```bash
# 종료
docker compose down

# 볼륨(DB)까지 초기화
docker compose down -v

# 특정 서비스만 재빌드
docker compose up --build core-service -d
```

---

> **Claude Code 사용 팁**: 이 파일을 Claude Code에서 열고,  
> 각 코드 블록을 순서대로 bash에서 실행하거나  
> `claude "CLAUDE_CODE_BUILD.md 파일에 따라 전체 프로젝트를 구성해줘"` 로 자동 실행할 수 있습니다.
