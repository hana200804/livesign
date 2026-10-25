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
3. Build từ `backend/`: `./mvnw -B package -DskipTests`.
4. `java -jar app/target/app-0.1.0-SNAPSHOT.jar`.

Không ghi mật khẩu hay khóa vào `application*.yml`.

## Cấp tài khoản

Không có tài khoản mặc định và không có API tự đăng ký. Tạo từng tài khoản bằng lệnh sau
(mật khẩu ≥12 ký tự, đọc từ biến môi trường để không nằm trong lịch sử lệnh):

```bash
LIVESIGN_NEW_USER_PASSWORD='<nhập trên máy>' java -jar app/target/app-0.1.0-SNAPSHOT.jar --create-user=demo_admin --role=admin
```

`--role` là `user`, `importer` hoặc `admin`. Lệnh tạo xong thì thoát.
Đăng nhập: `POST /api/v1/auth/login`, JWT HS256 ký bằng `LIVESIGN_JWT_SECRET` (≥32 byte), hạn 3600 s.

## Luồng backend đã triển khai

- `core`: gom track, giao tia có kiểm điều kiện nghiệm/phần dư, fallback feature
  → camera_proxy, hướng mặt, gộp có neo/cannot-link, điểm Beta/vòng đời và dấu hiệu thay loại.
- Nhập lô: kiểm envelope/dòng, hash chuẩn hóa, chống trùng ID và ảnh/model/bbox,
  metadata-only, đếm từng dòng, lấy người gửi từ JWT, giữ creator/time gốc.
- Snapshot đồng bộ 2023–2025: đóng cấu hình/danh mục theo run, công bố nguyên tử
  từng năm, giữ run cũ, retry an toàn và khôi phục sau gián đoạn. GET chỉ đọc.
- API bbox, chi tiết/history, ahead; chuyến, trace NDJSON/manifest, ảnh và biển
  quanh trace. Cursor khóa theo bộ lọc/run, ETag kiểm sau quyền/cutoff.
- Admin: hàng chờ/chi tiết, ảnh bằng chứng, mở ca thủ công, quyết định có version,
  transaction sign/review/history; remove/replace cần ảnh trước/sau.

Endpoint ở [hợp đồng API](../docs/contracts/api.md). `POST /admin/signs` thuộc
Nên có, chưa nằm trong backend MVP này. Web admin/Android là client riêng.

## Cấu hình và kho replay

- `LIVESIGN_DATA_ROOT`: đường dẫn tuyệt đối kho ngoài git. Ref tương đối trong
  kho; chặn đường dẫn tuyệt đối, URL, `..` và symlink ra ngoài kho.
- `LIVESIGN_PARAMS`: mặc định `classpath:livesign/params.yaml`; có thể đặt
  `file:/đường/dẫn/params.yaml`. Không lấy `proposed` thay `value=null`.
- `LIVESIGN_TRACE_MAX_BYTES`: mặc định 67108864 bytes, là giới hạn triển khai,
  chưa phải tải đã đo. Một lô ≤500 dòng, nhập ≤60 request/phút/tài khoản.

Importer chuẩn bị trace/manifest/ảnh trong kho rồi khai `trace_ref`,
`trace_manifest_ref`, `trace_schema_version`, `trace_sha256`, `data_kind`,
`data_revision`, `replay_enabled` trong trip của **lô đầu tiên**. Metadata đã khai
là bất biến; đổi bytes dùng ref/version/trip mới. User không khai ref kho replay.
Trace cần schema 1.1/timestamp thật; GeoJSON cũ không tự đổi sang JSONL.
Manifest ảnh theo [trip-trace.md](../docs/contracts/trip-trace.md).

N/θ/θ_thấp và bật âm vẫn theo cấu hình nhóm duyệt. Lưu được seen khi N/θ null
nhưng không tự xác nhận; `not_seen` bị từ chối nếu chưa bật. Quyết định làm năm
2026 không đổi bản đồ 2023–2025. Test mô phỏng dùng CSDL Testcontainers riêng.

### Demo quyết định lịch sử

Chạy jar với `--spring.profiles.active=simulation`. Profile bắt buộc các biến
`LIVESIGN_SIMULATION_MYSQL_URL/USER/PASSWORD`, `LIVESIGN_SCENARIO_ID`,
`LIVESIGN_SIMULATION_NOW` (UTC trong năm demo), `LIVESIGN_SIMULATION_PARAMS`
(file YAML thử riêng). CSDL phải là `livesign_simulation` hoặc
`livesign_simulation_<tên>`; không dùng MYSQL_URL thật. JSON có scenario_id và
data_kind=simulation; ảnh/trace có header tương ứng. Client giữ nhãn mô phỏng.
Đồng hồ giả chỉ ảnh hưởng ngày quyết định admin; JWT vẫn dùng thời gian thật.

## Xác minh

`./mvnw -B verify` chạy test, đóng gói jar và xuất JaCoCo tại
`core/target/site/jacoco/`, `app/target/site/jacoco/`. Test MySQL cần Docker;
đọc số skipped để biết môi trường có thực sự chạy tích hợp hay không.
