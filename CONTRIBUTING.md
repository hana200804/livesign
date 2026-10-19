# Quy ước làm việc

## Cài một lần sau khi clone

Chạy ở gốc repo (Git Bash hoặc PowerShell):

```bash
git config core.hooksPath .githooks
git config push.autoSetupRemote true
git config fetch.prune true
git config pull.ff only
```

- `core.hooksPath` bật hook của repo:
  - `pre-commit` chặn token, mật khẩu, file `.env`, file model, file nén và file trên 5 MB.
  - `pre-push` chặn push thẳng lên `main` và quét lại các commit sắp push.
- Đăng nhập GitHub bằng `gh auth login`, để token nằm trong Windows Credential Manager. **Không** dán token vào URL remote.
- Email commit dùng địa chỉ noreply của GitHub (Settings → Emails → *Keep my email addresses private*), ví dụ:
  `git config user.email "<id>+<username>@users.noreply.github.com"`.

## Trên GitHub (đã cài sẵn)

- `main` được bảo vệ. Chỉ merge qua PR, cần 1 người duyệt, các comment phải được giải quyết, và 3 kiểm tra CI phải qua: *Chặn bí mật và file lớn*, *AI (pytest)*, *Tools (cú pháp Node)*. Không force-push, không xóa được `main`.
- Chỉ cho **Squash and merge**. Nhánh tự xóa sau khi merge.
- CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) chạy mỗi khi mở hoặc cập nhật PR. Job Backend và Android tự bật khi đã có `backend/pom.xml` và `android/gradlew`.

## Nhánh

- `main` luôn chạy được. Không push thẳng lên `main`, mọi thay đổi đi qua Pull Request.
- Tên nhánh: `<phần>/<mô tả ngắn>`, ví dụ `ai/yolo-quick-test`, `backend/schema`, `android/vietmap-map`.
- `<phần>` là một trong: `ai`, `backend`, `android`, `tools`, `docs`.

## Commit

Viết ngắn, tiếng Việt hoặc tiếng Anh đều được, nói rõ thay đổi gì:

```
backend: thêm bảng sign, observation
ai: gộp nhãn Zalo + Roboflow về 12 loại
```

## Pull Request

1. Cập nhật nhánh từ `main` (`git switch main && git pull && git switch - && git merge main`), rồi `git push`.
2. Mở PR vào `main` (`gh pr create`). Nếu PR giải quyết một issue, ghi `Closes #<số issue>` để issue tự đóng khi merge.
3. Chờ CI xanh và một bạn khác duyệt, sau đó bấm **Squash and merge**.
4. Không dùng `git push --force` trên nhánh có người khác đang làm. Cần ghi đè nhánh của chính mình thì dùng `git push --force-with-lease`.

## Không bao giờ commit

- Token, API key, mật khẩu (Mapillary, Vietmap, MySQL). Dùng biến môi trường hoặc file `.env` (đã nằm trong `.gitignore`); ghi tên biến cần có vào `.env.example`.
- Ảnh tải về, dataset, file model (`*.pt`, `*.tflite`, `*.onnx`). Để trong `data/` hoặc Google Drive chung, ghi đường dẫn vào README của phần đó.

Lỡ commit token: báo cả nhóm, **thu hồi token ngay** trên trang developer của dịch vụ rồi mới xóa khỏi git. Xóa khỏi git thôi là chưa đủ.
