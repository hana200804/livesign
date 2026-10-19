# backend — Spring Boot + MySQL

API nhận lần thấy, gộp biển, tính điểm tin cậy, quản lý vòng đời biển, và web admin tối giản.

## Việc thuộc phần này

| Mã | Việc | Tuần |
| --- | --- | --- |
| B1 | Thiết kế CSDL, dựng Spring Boot + MySQL; thử gộp biển 500m (B0) bằng detection của Mapillary | 1 |
| B2 | API lần thấy; gộp biển theo loại + khoảng cách + hướng, tách biển chung cột; chạy B0, B1 | 2–4 |
| B3 | Điểm tin cậy giảm dần theo năm, CHƯA RÕ, mâu thuẫn, vòng đời biển; chạy B2 (và B3) | 5–6 |
| B4 | Web admin: bản đồ biển theo trạng thái + hàng chờ ảnh trước / sau | 5–6 |
| B5 | Chạy thang B0–B3 trên bộ mốc theo các mốc thời gian; mô phỏng biển giả | 7 |

## Khởi tạo project

Project chưa được tạo. Người phụ trách tạo bằng [Spring Initializr](https://start.spring.io) rồi giải nén thẳng vào thư mục này:

- Project: Maven, Java 21, Spring Boot bản 3.x ổn định mới nhất
- Group: `vn.livesign`, Artifact: `backend`
- Dependencies: Spring Web, Spring Data JPA, MySQL Driver, Flyway Migration, Validation, Lombok, Spring Boot DevTools

Cấu hình kết nối lấy từ biến môi trường (`MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`, xem `.env.example` ở gốc repo), không ghi mật khẩu vào `application.properties`.
