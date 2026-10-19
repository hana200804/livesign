# backend — Spring Boot + MySQL

API nhận lần thấy, gộp biển, tính điểm tin cậy, quản lý vòng đời biển, và web admin tối giản.

## Khởi tạo project

Project chưa được tạo. Người phụ trách tạo bằng [Spring Initializr](https://start.spring.io) rồi giải nén thẳng vào thư mục này:

- Project: Maven, Java 21, Spring Boot bản 3.x ổn định mới nhất
- Group: `vn.livesign`, Artifact: `backend`
- Dependencies: Spring Web, Spring Data JPA, MySQL Driver, Flyway Migration, Validation, Lombok, Spring Boot DevTools

Cấu hình kết nối lấy từ biến môi trường (`MYSQL_URL`, `MYSQL_USER`, `MYSQL_PASSWORD`, xem `.env.example` ở gốc repo), không ghi mật khẩu vào `application.properties`.
