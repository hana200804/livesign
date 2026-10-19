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
  - `pre-push` chặn push thẳng lên `main` hoặc `dev`, và quét lại các commit sắp push.
- Đăng nhập GitHub bằng `gh auth login`, để token nằm trong Windows Credential Manager. **Không** dán token vào URL remote.
- Email commit dùng địa chỉ noreply của GitHub (Settings → Emails → *Keep my email addresses private*), ví dụ:
  `git config user.email "<id>+<username>@users.noreply.github.com"`.

## Trên GitHub (đã cài sẵn)

- `main` và `dev` được bảo vệ:
  - Chỉ merge qua PR, cần 1 người duyệt, các comment phải được giải quyết.
  - 3 kiểm tra CI phải qua: *Chặn bí mật và file lớn*, *AI (pytest)*, *Tools (cú pháp Node)*.
  - PR vào `dev` cần chủ repo duyệt (khai báo trong [`.github/CODEOWNERS`](.github/CODEOWNERS)).
  - Không force-push, không xóa được hai nhánh này.
- Chỉ cho **Squash and merge**. Nhánh tự xóa sau khi merge.
- CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) chạy mỗi khi mở hoặc cập nhật PR. Job Backend và Android tự bật khi đã có `backend/pom.xml` và `android/gradlew`.

## Nhánh

```
main  ← chỉ nhận PR từ dev, khi dev ổn định
 └─ dev  ← nhánh phát triển chung, nhận PR từ các nhánh chức năng
     ├─ ai/loc-anh-trung
     ├─ backend/nhap-lo-lan-thay
     └─ android/ban-do-vietmap
```

- Làm chức năng mới: `git switch dev && git pull --ff-only && git switch -c <phần>/<mô-tả-ngắn>`.
- `<phần>` là một trong: `ai`, `backend`, `android`, `tools`, `docs`. Mô tả viết thường, không dấu, nối bằng gạch ngang.
- Mỗi nhánh chỉ làm một chức năng. Không push thẳng lên `dev` hay `main`.
- Dùng Claude Code thì Claude đọc các file `CLAUDE.md` (ở gốc repo và trong từng thư mục), trong đó có quy trình này.

## Commit

Viết ngắn, tiếng Việt hoặc tiếng Anh đều được, nói rõ thay đổi gì:

```
backend: thêm bảng sign, observation
ai: gộp nhãn Zalo + Roboflow về 12 loại
```

## Pull Request

1. Chạy test của phần mình (xem `CLAUDE.md` trong thư mục đó).
2. Cập nhật nhánh từ `dev` (`git fetch && git merge origin/dev`), rồi `git push`.
3. Mở PR **vào `dev`**: `gh pr create --base dev --fill`. Nếu PR giải quyết một issue, ghi `Closes #<số issue>`.
4. Chờ CI xanh và chủ repo duyệt. Chủ repo bấm **Squash and merge**. Người mở PR không tự merge.
5. Không dùng `git push --force` trên nhánh có người khác đang làm. Cần ghi đè nhánh của chính mình thì dùng `git push --force-with-lease`.

## Không bao giờ commit

- Token, API key, mật khẩu (Mapillary, Vietmap, MySQL). Dùng biến môi trường hoặc file `.env` (đã nằm trong `.gitignore`); ghi tên biến cần có vào `.env.example`.
- Ảnh tải về, dataset, file model (`*.pt`, `*.tflite`, `*.onnx`). Để trong `data/` hoặc Google Drive chung, ghi đường dẫn vào README của phần đó.

Lỡ commit token: báo cả nhóm, **thu hồi token ngay** trên trang developer của dịch vụ rồi mới xóa khỏi git. Xóa khỏi git thôi là chưa đủ.
