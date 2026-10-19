# Quy ước làm việc

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

1. Mở PR vào `main`. Nếu PR giải quyết một issue, ghi `Closes #<số issue>` để issue tự đóng khi merge.
2. Ít nhất một bạn khác xem trước khi merge.
3. Merge kiểu **Squash and merge** để lịch sử `main` gọn.

## Không bao giờ commit

- Token, API key, mật khẩu (Mapillary, Vietmap, MySQL). Dùng biến môi trường hoặc file `.env` (đã nằm trong `.gitignore`); ghi tên biến cần có vào `.env.example`.
- Ảnh tải về, dataset, file model (`*.pt`, `*.tflite`, `*.onnx`). Để trong `data/` hoặc Google Drive chung, ghi đường dẫn vào README của phần đó.

Lỡ commit token: báo cả nhóm, **thu hồi token ngay** trên trang developer của dịch vụ rồi mới xóa khỏi git. Xóa khỏi git thôi là chưa đủ.
