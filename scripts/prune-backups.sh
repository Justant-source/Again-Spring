#!/usr/bin/env bash
#
# scripts/prune-backups.sh — prod DB 백업 보관 정책 집행
#
# 사용법:
#   scripts/prune-backups.sh            # 압축 + 정리 실행
#   scripts/prune-backups.sh --dry-run  # 무엇이 지워질지만 출력 (삭제 안 함)
#
# 두 곳에서 호출된다:
#   1) scripts/deploy.sh prod — 백업을 새로 뜬 직후
#   2) 야간 cron — 배포가 없는 날에도 정리가 돌게 하기 위함
#      30 4 * * * /home/justant/Data/Again-Spring/scripts/prune-backups.sh >> \
#        /home/justant/Data/Again-Spring/env/logs/prune-backups.cron.log 2>&1
#
# 정책:
#   - 압축: 최상위의 비압축 *.sql 을 gzip 으로 만든다 (수동 덤프 대비)
#   - 보관: BACKUP_RETAIN_DAYS 초과분 삭제
#   - 바닥값: 나이와 무관하게 최신 BACKUP_MIN_KEEP 개는 남긴다.
#     prod 배포가 오래 없으면 보관 기간만으로는 전량 삭제될 수 있기 때문이다.
#   - 범위: BACKUP_DIR 최상위 파일만 (-maxdepth 1).
#     green-forest/ 하위는 Green-Forest/scripts/backup-prod.sh 가 자체 정책(14일)으로 관리한다.
#
# 배경: docs/env/60-runtime/deployment.md — "백업 보관 정책"

set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/home/justant/backups}"
BACKUP_RETAIN_DAYS="${BACKUP_RETAIN_DAYS:-30}"
BACKUP_MIN_KEEP="${BACKUP_MIN_KEEP:-5}"

DRY_RUN=""
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN="1" ;;
    *) echo "🚨 알 수 없는 인자: $arg" >&2; exit 1 ;;
  esac
done

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"; }

if [[ ! -d "$BACKUP_DIR" ]]; then
  log "백업 디렉토리가 없다: $BACKUP_DIR — 할 일 없음"
  exit 0
fi

log "===== 백업 정리 시작 (보관 ${BACKUP_RETAIN_DAYS}일 / 최소 ${BACKUP_MIN_KEEP}개${DRY_RUN:+ / DRY-RUN}) ====="

# ===== 1. 비압축 덤프 압축 =====
# 수동으로 뜬 .sql 이 섞여 들어와도 저장 공간을 잡아먹지 않게 한다.
COMPRESSED=0
while IFS= read -r -d '' SQL_FILE; do
  if [[ -n "$DRY_RUN" ]]; then
    log "  [dry-run] 압축 예정: $(basename "$SQL_FILE")"
    COMPRESSED=$(( COMPRESSED + 1 ))
    continue
  fi
  if nice -n 19 gzip -f "$SQL_FILE"; then
    log "  압축: $(basename "$SQL_FILE") → $(basename "$SQL_FILE").gz"
    COMPRESSED=$(( COMPRESSED + 1 ))
  fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -name '*.sql' -print0)

# ===== 2. 보관 기간 초과분 삭제 =====
# 최신순 정렬 후 앞의 BACKUP_MIN_KEEP 개를 "무조건 유지" 집합으로 잡는다.
mapfile -t BACKUP_ALL < <(
  find "$BACKUP_DIR" -maxdepth 1 -type f -printf '%T@\t%p\n' | sort -rn | cut -f2-
)
declare -A BACKUP_KEEP=()
for (( i = 0; i < BACKUP_MIN_KEEP && i < ${#BACKUP_ALL[@]}; i++ )); do
  BACKUP_KEEP["${BACKUP_ALL[i]}"]=1
done

PRUNED=0
FREED=0
while IFS= read -r -d '' OLD_FILE; do
  [[ -n "${BACKUP_KEEP[$OLD_FILE]:-}" ]] && continue
  OLD_SIZE=$(stat -c %s "$OLD_FILE")
  if [[ -n "$DRY_RUN" ]]; then
    log "  [dry-run] 삭제 예정: $(basename "$OLD_FILE") ($(numfmt --to=iec "$OLD_SIZE"))"
    PRUNED=$(( PRUNED + 1 )); FREED=$(( FREED + OLD_SIZE ))
    continue
  fi
  if rm -f -- "$OLD_FILE"; then
    log "  삭제: $(basename "$OLD_FILE") ($(numfmt --to=iec "$OLD_SIZE"))"
    PRUNED=$(( PRUNED + 1 )); FREED=$(( FREED + OLD_SIZE ))
  fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -mtime +"$BACKUP_RETAIN_DAYS" -print0)

REMAIN=$(find "$BACKUP_DIR" -maxdepth 1 -type f | wc -l)
log "===== 완료: 압축 ${COMPRESSED}개 · 삭제 ${PRUNED}개 ($(numfmt --to=iec "$FREED") 회수) · 잔여 ${REMAIN}개 / $(du -sh "$BACKUP_DIR" | cut -f1) ====="
