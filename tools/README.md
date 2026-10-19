# tools — Script dữ liệu Mapillary

Node ≥ 18, không cần cài gói. Script cần token đọc biến `MAPILLARY_TOKEN`:

```powershell
$env:MAPILLARY_TOKEN = "MLY|..."
```

## mapillary-coverage/

| Script | Làm gì | Đầu ra |
| --- | --- | --- |
| `check-coverage.mjs [khu]` | Đo độ phủ ảnh theo từng phố ở 3 khu (PTIT, Cầu Giấy, Hoàn Kiếm) | `out/<khu>-streets.csv`, `out/<khu>-signs.csv`, `out/summary.json` |
| `trip-check.mjs` | Với mỗi biển: bao nhiêu chuỗi ảnh / người chụp đi qua trong 30 m, bao nhiêu thật sự thấy biển | `out/<khu>-trip-check.csv` |
| `rank-segments.mjs <khu>` | Xếp hạng ô ~200 m theo độ hợp làm đoạn demo (dùng cho N1) | `out/<khu>-segments.csv` |

## poc/ — PoC tuần 1

| File | Làm gì |
| --- | --- |
| `fetch-signs.mjs` | Chọn 500 m có nhiều biển nhất, lấy ảnh đi qua từng biển, tải thumbnail 1024px vào `out/images/` |
| `label-tool/server.mjs` | Công cụ gán tay 3 nhãn (thấy / không thấy / không xác định), mở http://localhost:8787, ghi `out/labels.csv` |
| `visibility_poc.ipynb` | Notebook đo tỉ lệ model thấy biển trên ảnh đi qua |
| `results/` | Kết quả PoC trên Trần Phú |

Ảnh trong `poc/out/images/` và file `.zip` không được commit. Chạy lại `fetch-signs.mjs` để tải về.
