#!/usr/bin/env bash
# 用法: ./benchmark.sh [并发数] [总请求数] [秒杀库存]
# 例:   ./benchmark.sh 200 2000 100
set -euo pipefail

BASE=${BASE:-http://127.0.0.1:8080}
VOUCHER=${VOUCHER:-1}
CONCURRENCY=${1:-200}
TOTAL=${2:-2000}
STOCK=${3:-100}

echo "== 重置活动 $VOUCHER 库存为 $STOCK =="
curl -s -X POST "$BASE/api/admin/reset/$VOUCHER?stock=$STOCK"
echo

echo "== 开始压测: $TOTAL 请求, $CONCURRENCY 并发 =="
TMP=$(mktemp)
START=$(date +%s%3N)
seq 1 "$TOTAL" | xargs -P "$CONCURRENCY" -I{} curl -s -w '\n' \
    -X POST "$BASE/api/seckill/$VOUCHER" -H "X-User-Id: {}" >> "$TMP"
END=$(date +%s%3N)
echo "== 全部请求完成，耗时 $((END - START)) ms =="
echo "== 业务返回码分布 =="
grep -o '"code":[0-9]*' "$TMP" | sort | uniq -c
rm -f "$TMP"

echo "== 等待 MQ 异步下单完成 =="
for _ in $(seq 1 30); do
    STATS=$(curl -s "$BASE/api/admin/stats/$VOUCHER")
    ORDER_COUNT=$(echo "$STATS" | grep -o '"dbOrderCount":[0-9]*' | cut -d: -f2)
    if [ "$ORDER_COUNT" = "$STOCK" ]; then
        break
    fi
    sleep 1
done

echo "== 最终对账 =="
echo "$STATS"
