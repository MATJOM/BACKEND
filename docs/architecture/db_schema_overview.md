# MATJOM DB Schema Overview

본 문서는 현재 프로젝트에 정의된 핵심 테이블의 구조와 주요 제약을 요약합니다. 실제 DDL은 `src/main/resources/schema-postgres.sql`에서 관리되며, 개발 환경에서는 `schema-postgres.sql`이 자동 적용됩니다.

## Users (`users`)

| 컬럼 | 타입 | 제약/설명 |
| --- | --- | --- |
| id | UUID | PK, 코드에서 `@UuidGenerator` 사용 |
| email | TEXT | NOT NULL, provider와 복합 UNIQUE |
| name | TEXT | NOT NULL |
| password | TEXT | LOCAL 계정만 사용 |
| provider | VARCHAR(20) | NOT NULL, `AuthProvider` enum |
| created_at / updated_at / deleted_at | TIMESTAMPTZ | `BaseEntity` 공통 필드 |
| 제약 | `uq_user_email_provider`, `chk_password_required` |

## Places (`places`)

| 컬럼 | 타입 | 제약/설명 |
| --- | --- | --- |
| place_id | BIGSERIAL | PK |
| name | VARCHAR(100) | NOT NULL |
| lat / lng | NUMERIC(9,6) | 실수 좌표, WGS84 |
| category | TEXT[] | NOT NULL, Hibernate `@JdbcTypeCode(SqlTypes.ARRAY)` |
| provider_id | VARCHAR(100) | NOT NULL, `uq_places_provider_id` UNIQUE |
| phone_number | VARCHAR(20) | NOT NULL |
| working_hours / break_time | JSONB | NOT NULL, `@JdbcTypeCode(SqlTypes.JSON)` |
| opened_at | DATE | NOT NULL |
| opened_at_source | JSONB | NOT NULL, 출처 정보 |
| biz_status | VARCHAR(20) | NOT NULL |
| addr_sido | VARCHAR(20) | NOT NULL |
| addr_sigungu | VARCHAR(30) | NOT NULL |
| addr_eupmyeondong | VARCHAR(80) | NOT NULL |
| addr_street | VARCHAR(100) | NOT NULL |
| addr_detail | VARCHAR(100) | NOT NULL |
| location | geometry(Point,4326) | PostGIS 공간 컬럼 |
| created_at / updated_at / deleted_at | TIMESTAMPTZ | `BaseEntity` |
| 인덱스 | lower(name), GIN(category), GIST(location), 주소별 인덱스 |

## Visits (`visits`)

| 컬럼 | 타입 | 제약/설명 |
| --- | --- | --- |
| visit_id | BIGSERIAL | PK |
| user_id | UUID | FK → users(id) |
| place_id | BIGINT | FK → places(place_id) |
| state | VARCHAR(20) | `VisitState` enum, CHECK 제약 |
| client_mode | VARCHAR(20) | `ClientMode` enum, CHECK 제약 |
| started_at / arrived_at / cancelled_at / expired_at | TIMESTAMPTZ | 세션 상태 이력 |
| last_pos_at | TIMESTAMPTZ | 마지막 위치 수신 시간 |
| dwell_started_at | TIMESTAMPTZ | 180초 dwell 시작점 |
| last_lat / last_lng | NUMERIC(9,6) | 마지막 좌표 |
| last_accuracy_m | NUMERIC(6,2) | 위치 정확도 |
| meta | JSONB | 확장 메타 |
| created_at / updated_at / deleted_at | TIMESTAMPTZ | `BaseEntity` |
| 인덱스 | user_id, place_id, started_at, partial UNIQUE(state='ACTIVE') |

## VisitPositions (`visit_positions`)

| 컬럼 | 타입 | 제약/설명 |
| --- | --- | --- |
| pos_id | BIGSERIAL | PK |
| visit_id | BIGINT | FK → visits(visit_id) |
| lat / lng | NUMERIC(9,6) | 위치 이벤트 좌표 |
| accuracy_m | NUMERIC(6,2) | 위치 정확도 |
| mode | VARCHAR(20) | `ClientMode` enum |
| received_at | TIMESTAMPTZ | 이벤트 수신 시각 |
| created_at / updated_at / deleted_at | TIMESTAMPTZ | `BaseEntity` |
| 인덱스 | visit_id, (visit_id, received_at) |

## UserPlaceFirstArrival (`user_place_first_arrivals`)

| 컬럼 | 타입 | 제약/설명 |
| --- | --- | --- |
| user_id | UUID | PK, FK → users |
| place_id | BIGINT | PK, FK → places |
| first_arrived_at | TIMESTAMPTZ | 첫 방문 시각 |
| created_at / updated_at / deleted_at | TIMESTAMPTZ | `BaseEntity` |

## 스크립트/자동화 요약

- `scripts/import_initial_places.sh`: 원본 CSV를 인코딩 변환(EUC-KR→UTF-8), JSON 정제, Docker PostGIS 컨테이너로 복사, 스테이징 테이블 생성/적재, 좌표계(EPSG 5174→4326) 변환 및 `places` 업데이트까지 자동 수행합니다.
- `sql/import_initial_places.sql`: 스테이징에서 본 테이블로 데이터를 upsert하는 SQL. `provider_id` 기준으로 중복을 처리합니다.

---

추가 테이블이 생기면 이 문서를 업데이트하고, DDL 변경은 `schema-postgres.sql`과 엔티티 매핑(`Place.java` 등)에 반영하세요.
