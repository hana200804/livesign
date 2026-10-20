# backend — Spring Boot + MySQL

API nhận lần thấy, gộp biển, tính điểm tin cậy, quản lý vòng đời biển, và web admin tối giản.

## Cấu trúc

Maven nhiều module, Java 21, Spring Boot 3.5.

| Module | Nội dung |
| --- | --- |
| `core` | Thuật toán Java thuần: định vị, hướng mặt, gộp, điểm tin cậy, vòng đời. Không phụ thuộc Spring, không gọi CSDL/HTTP |
| `app` | Dịch vụ Spring Boot: nhập lô, job snapshot, API, admin/auth. Gọi `core` |

Dùng Spring JDBC, không dùng JPA: cột `POINT SRID 4326` cần SQL ghi rõ `axis-order=long-lat`
([db-schema.md](../docs/contracts/db-schema.md)).

## Chạy test

```bash
./mvnw -B test
```

- Cần JDK 21. Maven do wrapper tự tải.
- Test tích hợp dùng Testcontainers với `mysql:8.0`. Máy không có Docker thì các test này tự bỏ qua; CI vẫn chạy đủ.

## Chạy dịch vụ

1. Điền `.env` ở gốc repo theo `.env.example`, rồi `docker compose --env-file .env up -d mysql`.
2. Đặt các biến `MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD` (và các biến `LIVESIGN_*`) vào môi trường terminal. Spring không tự đọc `.env`.
3. `./mvnw -pl app -am spring-boot:run`.

Không ghi mật khẩu hay khóa vào `application*.yml`.
