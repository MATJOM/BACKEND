#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <csv-path>" >&2
  exit 1
fi

CSV_PATH="$1"
CONTAINER=${POSTGIS_CONTAINER:-db-postgis}
DB_NAME=${POSTGIS_DB:-matjom_dev}
DB_USER=${POSTGIS_USER:-devuser}
STAGING_CSV=/tmp/initial_places_clean.csv
SCRIPT_DIR="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/../sql/import_initial_places.sql"

if [[ ! -f "$SQL_FILE" ]]; then
  echo "SQL file not found: $SQL_FILE" >&2
  exit 1
fi

TMP_UTF8=$(mktemp)
TMP_CLEAN=$(mktemp)
trap 'rm -f "$TMP_UTF8" "$TMP_CLEAN"' EXIT

echo "[1/5] Converting encoding to UTF-8"
iconv -f CP949 -t UTF-8 "$CSV_PATH" > "$TMP_UTF8"

echo "[2/5] Normalizing JSON columns"
python3 - "$TMP_UTF8" "$TMP_CLEAN" <<'PY'
import csv, json, sys
utf8_path, clean_path = sys.argv[1], sys.argv[2]
with open(utf8_path, newline='', encoding='utf-8') as src:
    reader = csv.DictReader(src)
    fieldnames = reader.fieldnames
    with open(clean_path, 'w', newline='', encoding='utf-8') as dst:
        writer = csv.DictWriter(dst, fieldnames=fieldnames)
        writer.writeheader()
        for row in reader:
            for key in ("working_hours", "break_time"):
                raw = row.get(key)
                if raw:
                    row[key] = json.dumps(json.loads(raw))
            writer.writerow(row)
PY

chmod 644 "$TMP_CLEAN"
echo "Successfully converted $(du -h "$TMP_CLEAN" | cut -f1) to UTF-8"

echo "[3/5] Copying CSV into container $CONTAINER"
docker cp "$TMP_CLEAN" "$CONTAINER":"$STAGING_CSV"

echo "[4/5] Preparing staging table"
docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" <<'SQL'
DROP TABLE IF EXISTS staging_initial_places;
CREATE TABLE staging_initial_places (
    provider_id TEXT,
    opened_at DATE,
    biz_status TEXT,
    phone_number TEXT,
    name TEXT,
    updated_at DATE,
    category TEXT,
    lat NUMERIC,
    lng NUMERIC,
    working_hours JSONB,
    break_time JSONB,
    addr_sido TEXT,
    addr_sigungu TEXT,
    addr_road TEXT,
    addr_bnum TEXT,
    addr_detail TEXT,
    addr_dong TEXT
);
SQL

docker exec -i "$CONTAINER" \
  psql -U "$DB_USER" -d "$DB_NAME" \
  -c "\\copy staging_initial_places FROM '$STAGING_CSV' WITH (FORMAT csv, HEADER true)"

# Ensure unique constraint and column length
docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" <<'SQL'
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_places_provider_id'
    ) THEN
        ALTER TABLE places ADD CONSTRAINT uq_places_provider_id UNIQUE (provider_id);
    END IF;
END $$;
ALTER TABLE places ALTER COLUMN provider_id TYPE VARCHAR(100);
ALTER TABLE places ALTER COLUMN addr_eupmyeondong TYPE VARCHAR(80);
SQL

echo "[5/5] Applying transformation SQL"
cat "$SQL_FILE" | docker exec -i "$CONTAINER" \
  psql -U "$DB_USER" -d "$DB_NAME" -f -

docker exec -i "$CONTAINER" \
  psql -U "$DB_USER" -d "$DB_NAME" -c "ANALYZE places;"

echo "Import completed successfully."
