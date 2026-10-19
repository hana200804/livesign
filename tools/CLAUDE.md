# tools/ — script Node lấy dữ liệu Mapillary

Quy trình nhánh/PR ở [CLAUDE.md gốc](../CLAUDE.md). Mô tả các script: [README.md](README.md).

- Node ≥ 18, **không thêm gói npm**. Chỉ dùng thư viện có sẵn và `fetch`.
- Token đọc từ `process.env.MAPILLARY_TOKEN`. Không in token, không ghi token vào file trong repo.
- Script ghi kết quả vào `out/` cạnh nó. Ảnh tải về và file `.zip` không commit, chỉ CSV nhỏ dùng làm bằng chứng.
- Đầu ra mà phần khác đọc phải theo [docs/contracts/](../docs/contracts/).
- Test trước khi mở PR: `node --check <file>` cho mọi file `.mjs` đã sửa (CI cũng chạy). Thêm dòng hướng dẫn chạy ở đầu file script mới.
