#!/usr/bin/env bash
# Chặn bí mật, file cấm và file lớn trước khi vào git.
#   .githooks/check.sh staged            # dùng trong pre-commit: kiểm tra phần đã `git add`
#   .githooks/check.sh range BASE HEAD   # dùng trong CI: kiểm tra mọi thay đổi của PR
set -euo pipefail

MAX_KB=5120 # file lớn hơn 5 MB phải để ngoài git (data/ hoặc Drive)

mode=${1:-staged}
if [ "$mode" = staged ]; then
  files=$(git -c core.quotePath=false diff --cached --name-only --diff-filter=ACMR)
  added=$(git -c core.quotePath=false diff --cached -U0 --diff-filter=ACMR | grep -E '^\+[^+]' || true)
  blob() { echo ":$1"; }
elif [ "$mode" = range ]; then
  base=$2 head=$3
  files=$(git -c core.quotePath=false diff --name-only --diff-filter=ACMR "$base...$head")
  added=$(git -c core.quotePath=false diff -U0 --diff-filter=ACMR "$base...$head" | grep -E '^\+[^+]' || true)
  blob() { echo "$head:$1"; }
else
  echo "Cách dùng: $0 staged | range BASE HEAD" >&2; exit 2
fi
[ -z "$files" ] && exit 0

fail=0
report() { echo "  ✗ $1" >&2; fail=1; }

# 1. File không bao giờ được commit, kể cả khi lỡ `git add -f`.
while IFS= read -r f; do
  name=${f##*/}
  case "$name" in
    .env.example) ;;
    .env|.env.*|*.token|.token|*.pem|*.key|*.p12|*.jks|*.keystore|local.properties|settings.local.json)
      report "file bí mật: $f" ;;
    *.pt|*.pth|*.onnx|*.tflite|*.engine|*.zip|*.7z|*.rar|*.apk|*.aab)
      report "file model / nén / bản build: $f (để trong data/ hoặc Drive)" ;;
  esac
  size=$(git cat-file -s "$(blob "$f")" 2>/dev/null || echo 0)
  if [ "$size" -gt $((MAX_KB * 1024)) ]; then
    report "file lớn $((size / 1024 / 1024)) MB: $f"
  fi
done <<< "$files"

# 2. Chuỗi trông như token / khóa thật trong nội dung thêm mới.
patterns=(
  'MLY\|[0-9]{8,}\|[0-9a-f]{16,}'                 # Mapillary
  'gh[pousr]_[A-Za-z0-9]{30,}'                     # GitHub token
  'github_pat_[A-Za-z0-9_]{40,}'                   # GitHub fine-grained token
  'AIza[0-9A-Za-z_-]{35}'                          # Google API key
  '-----BEGIN [A-Z ]*PRIVATE KEY-----'             # khóa riêng
  '(MAPILLARY_TOKEN|VIETMAP_API_KEY|GITHUB_TOKEN|MYSQL_PASSWORD|MYSQL_ROOT_PASSWORD)[[:space:]]*[=:][[:space:]]*["'\'']?[A-Za-z0-9|_/+-]{6,}'
  'spring\.datasource\.password[[:space:]]*=[[:space:]]*[^$[:space:]]+'
)
for p in "${patterns[@]}"; do
  if hit=$(grep -nE -- "$p" <<< "$added"); then
    report "có chuỗi giống bí mật (mẫu: $p):"
    sed -E 's/(.{12}).*/      \1…/' <<< "$hit" >&2
  fi
done

if [ "$fail" -ne 0 ]; then
  cat >&2 <<'EOF'

Đã chặn. Gỡ file khỏi phần đã add bằng `git restore --staged <file>`, chuyển bí mật sang .env.
Nếu chắc chắn là báo nhầm, sửa mẫu trong .githooks/check.sh thay vì bỏ qua kiểm tra.
EOF
  exit 1
fi
