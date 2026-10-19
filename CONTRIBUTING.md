# Quy ước làm việc

## Nhánh

- `main` luôn chạy được. Không push thẳng lên `main`, mọi thay đổi đi qua Pull Request.
- Tên nhánh: `<phần>/<mã việc>-<mô tả ngắn>`, ví dụ `ai/A2-yolo-quick-test`, `backend/B1-schema`, `android/M1-vietmap-map`.
- `<phần>` là một trong: `ai`, `backend`, `android`, `tools`, `docs`.

## Commit

Viết ngắn, tiếng Việt hoặc tiếng Anh đều được, có mã việc ở đầu:

```
B1: thêm bảng sign, observation
A3: gộp nhãn Zalo + Roboflow về 12 loại
```

## Pull Request

1. Mở PR vào `main`, ghi `Closes #<số issue>` trong mô tả để issue tự đóng khi merge.
2. Ít nhất một bạn khác xem trước khi merge.
3. Merge kiểu **Squash and merge** để lịch sử `main` gọn.

## Issue

- Mỗi việc trong bảng chia việc là một issue, gắn nhãn phần (`ai`, `backend`, `android`, `team`) và milestone theo tuần.
- Lỗi hoặc việc phát sinh: mở issue mới bằng mẫu có sẵn.

## Không bao giờ commit

- Token, API key, mật khẩu (Mapillary, Vietmap, MySQL). Dùng biến môi trường hoặc file `.env` (đã nằm trong `.gitignore`); ghi tên biến cần có vào `.env.example`.
- Ảnh tải về, dataset, file model (`*.pt`, `*.tflite`, `*.onnx`). Để trong `data/` hoặc Google Drive chung, ghi đường dẫn vào README của phần đó.

Lỡ commit token: báo cả nhóm, **thu hồi token ngay** trên trang developer của dịch vụ rồi mới xóa khỏi git. Xóa khỏi git thôi là chưa đủ.
