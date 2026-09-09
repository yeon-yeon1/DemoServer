#!/usr/bin/env bash
# MySQL 일일 백업. crontab 등록 예시(매일 새벽 4시):
#   0 4 * * * /home/ubuntu/duksungmap/scripts/backup-mysql.sh >> /home/ubuntu/duksungmap/backups/backup.log 2>&1
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

BACKUP_DIR="$APP_DIR/backups"
RETENTION_DAYS=14
STAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"

# 비밀번호는 컨테이너 안의 환경변수를 그대로 쓴다. 스크립트나 crontab,
# ps 출력 어디에도 평문이 남지 않는다.
docker compose -f docker-compose.prod.yml exec -T mysql sh -c \
  'exec mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --events "$MYSQL_DATABASE"' \
  | gzip > "$BACKUP_DIR/db-$STAMP.sql.gz"

# 덤프가 비었으면(=실패) 빈 파일을 남기지 않고 즉시 실패시킨다.
if [ ! -s "$BACKUP_DIR/db-$STAMP.sql.gz" ]; then
  rm -f "$BACKUP_DIR/db-$STAMP.sql.gz"
  echo "[$(date -Is)] 백업 실패: 덤프가 비어 있음" >&2
  exit 1
fi

find "$BACKUP_DIR" -name 'db-*.sql.gz' -mtime "+$RETENTION_DAYS" -delete
echo "[$(date -Is)] 백업 완료: db-$STAMP.sql.gz ($(du -h "$BACKUP_DIR/db-$STAMP.sql.gz" | cut -f1))"
