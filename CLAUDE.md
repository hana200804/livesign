# LiveSign — quy tắc cho Claude Code

Đồ án nhận diện biển báo và giữ bản đồ biển báo cập nhật từ ảnh Mapillary ở Hà Nội. Tài liệu gốc: [docs/README.md](docs/README.md). Phạm vi phải làm: [Phạm vi MVP](docs/requirements.md#phạm-vi-mvp).

## Quy trình git — BẮT BUỘC, không có ngoại lệ

Hai nhánh `main` và `dev` được bảo vệ. **Không bao giờ commit hay push thẳng lên `main` hoặc `dev`.**

1. **Trước khi sửa bất cứ file nào**, kiểm tra nhánh hiện tại bằng `git branch --show-current`. Nếu đang ở `main` hoặc `dev`, tạo nhánh mới từ `dev` mới nhất:
   ```bash
   git switch dev && git pull --ff-only && git switch -c <phần>/<mô-tả-ngắn>
   ```
   - `<phần>` là một trong: `ai`, `backend`, `android`, `tools`, `docs`.
   - Mô tả viết thường, không dấu, nối bằng gạch ngang. Ví dụ: `backend/nhap-lo-lan-thay`, `ai/loc-anh-trung`.
2. Mỗi nhánh chỉ làm **một chức năng**. Gặp việc khác thì để lại cho nhánh khác, không gộp vào.
3. Commit nhỏ, message tiếng Việt, mở đầu bằng phần: `backend: thêm API nhập lô`.
4. Xong chức năng thì chạy test của phần đó (xem CLAUDE.md trong thư mục), rồi push và mở PR **vào `dev`**:
   ```bash
   git push
   gh pr create --base dev --fill
   ```
   - Mô tả PR theo mẫu: làm gì, đã kiểm tra thế nào, phần nào chưa kiểm.
   - GitHub tự gửi yêu cầu duyệt cho chủ repo (CODEOWNERS).
5. **Không** tự merge, tự approve, hay dùng quyền bypass. Chờ chủ repo review. Có comment thì sửa trên chính nhánh đó rồi push lại.
6. Trước khi mở PR, nếu `dev` đã có commit mới thì cập nhật nhánh: `git fetch && git merge origin/dev`. Không dùng `git push --force`. Cần ghi đè nhánh của chính mình thì dùng `--force-with-lease`.

`dev` được đưa vào `main` bằng PR riêng, do chủ repo làm khi `dev` ổn định.

## Một lần trên mỗi máy

Nếu `git config core.hooksPath` không in ra `.githooks` thì chạy các lệnh cài đặt ở [CONTRIBUTING.md](CONTRIBUTING.md#cài-một-lần-sau-khi-clone). Hook chặn bí mật, file lớn và push thẳng lên `main`/`dev`. Hook báo lỗi thì sửa nguyên nhân. **Không dùng `--no-verify`.**

## Không bao giờ commit

- Token, API key, mật khẩu. Lấy từ biến môi trường hoặc `.env` (tên biến ở `.env.example`). Không in token ra log hay output.
- Ảnh, dataset, checkpoint, `.tflite`, `.zip`. Để trong `data/` (ngoài git) hoặc Drive chung.

## Hợp đồng và cấu hình dùng chung

- Định dạng giữa các phần nằm ở [docs/contracts/](docs/contracts/): `api.md`, `db-schema.md`, `observation.md`, `model.md`, `trip-trace.md`, `classes.md`, `ground-truth.md`. Code phải theo đúng hợp đồng.
- Muốn đổi hợp đồng thì sửa file hợp đồng trong **cùng PR**, và ghi rõ trong mô tả PR mục "Đổi hợp đồng" để người làm phần khác biết.
- [`ai/configs/classes.yaml`](ai/configs/classes.yaml) là nguồn duy nhất cho danh mục lớp. [`config/params.yaml`](config/params.yaml) chứa tham số. **Không tự đổi** giá trị có `status: "đã chốt"`. Giá trị "dự kiến" chỉ đổi khi PR giải thích lý do.
- Thuật toán lõi (định vị, gộp, điểm tin cậy, vòng đời) chỉ viết **một lần** trong module Java `core` (xem [architecture.md](docs/architecture.md)). Không viết lại bằng ngôn ngữ khác.

## Viết tài liệu

- Tiếng Việt, câu ngắn, số liệu có đơn vị.
- Không ghi tên người phụ trách, mã việc hay kế hoạch theo tuần.
- Phương án đối chứng gọi là "cách làm phổ biến hiện nay". Không so sánh với đồ án cụ thể nào khác.
- Số chưa đo thì ghi là "cần đo", không viết như kết quả đã đạt.
