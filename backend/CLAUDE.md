# backend/ — Spring Boot + MySQL 8, module `core`

Quy trình nhánh/PR ở [CLAUDE.md gốc](../CLAUDE.md). Thiết kế: [docs/architecture.md](../docs/architecture.md), [docs/algorithms.md](../docs/algorithms.md).

- **Cấu trúc:**
  - Một backend Spring Boot chia module.
  - Thuật toán (định vị, hướng mặt, gộp, điểm Beta, vòng đời) nằm trong module **`core` Java thuần, không phụ thuộc Spring**. Dịch vụ và CLI đánh giá cùng gọi `core`.
  - Không đặt logic thuật toán trong controller, service hay SQL.
- **Hợp đồng:**
  - Endpoint theo [api.md](../docs/contracts/api.md), bảng theo [db-schema.md](../docs/contracts/db-schema.md), tham số đọc từ [config/params.yaml](../config/params.yaml).
  - Không hard-code ngưỡng.
- **CSDL:**
  - Thay đổi schema chỉ qua migration Flyway mới.
  - **Không sửa** migration đã merge vào `dev`.
  - MySQL local dùng `docker compose up -d` với `.env` ở gốc repo.
- **Bí mật:** cấu hình kết nối và JWT secret đọc từ biến môi trường. Không ghi giá trị thật vào `application*.properties` hay `application*.yml`.
- **Test trước khi mở PR:** `./mvnw -B test` trong `backend/`. CI tự chạy khi đã có `backend/pom.xml`.
  - Test bắt buộc cho `core` liệt kê ở [docs/testing.md](../docs/testing.md).
  - Logic mới trong `core` phải có unit test kèm theo.
  - Test cần MySQL dùng Testcontainers (`MySqlTestcontainer`), tên lớp kết thúc bằng `Test` để `mvnw test` chạy. Không dùng H2.
- **Module:** `core/` (Java thuần) và `app/` (Spring Boot), xem [README.md](README.md).
