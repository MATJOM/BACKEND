# MATJOM Project Architecture Overview

본 문서는 백엔드 프로젝트의 전반적인 구조와 주요 모듈을 설명합니다.

## 1. 주요 디렉터리 구조

```
BACKEND/
├── build.gradle / settings.gradle
├── src/
│   ├── main/java/com/matjom/matjom
│   │   ├── MatjomApplication.java        # Spring Boot entry point
│   │   ├── common/                       # 공통 인프라 (config, response, security 등)
│   │   ├── place/                        # Place 도메인 엔티티/추후 서비스
│   │   ├── user/                         # User 도메인 엔티티/추후 서비스
│   │   └── visit/                        # 방문 세션 관련 엔티티
│   └── main/resources
│       ├── application.yml               # 공통 설정 (local/dev/prod 프로필 사용)
│       ├── application-local.yml         # H2 기반 로컬 실행
│       ├── application-dev.yml           # Postgres + Redis 개발 환경
│       ├── application-prod.yml          # 운영 프로필 (Postgres + Redis)
│       └── schema-postgres.sql           # Postgres 초기 스키마
├── scripts/                              # 보조 스크립트 (CSV import 등)
├── sql/                                  # SQL 스니펫/배치용 스크립트
├── docs/                                 # 개발/운영 문서
└── infra/                                # Docker Compose 인프라 구성(postgres, redis, elk 등)
```

## 2. 공통 구성요소

### 2.1 공통 응답/예외 처리
- `common/response` 패키지: `ApiResponse`, `ApiResponseBodyAdvice` 등 REST 응답 래핑.
- `common/exception`: `DomainException` 계층, `GlobalExceptionHandler`.
- `common/security/SecurityConfig`: 기본 permitAll 설정(향후 인증 구현 시 확장 예정).

### 2.2 JPA & DB
- `common/config/JpaConfig`: `@EnableJpaAuditing` 활성화.
- `common/entity/BaseEntity`: 모든 엔티티가 상속하여 `created_at`, `updated_at`, `deleted_at` 관리.
- 엔티티별 패키지(`user`, `place`, `visit`)에서 JPA 매핑을 정의.

## 3. 인프라 구성
- `infra/postgresql/docker-compose.yml`: PostGIS(16-3.4) 컨테이너, 개발용 계정(devuser/devpass).
- `infra/redis/docker-compose.yml`: Redis 개발용.
- `infra/metric`, `infra/elk`: Prometheus/Grafana, Elasticsearch/Logstash/Kibana 스택.
- 개발자는 필요한 서비스만 선택적으로 실행 가능.

## 4. 데이터 적재/자동화
- `scripts/import_initial_places.sh`: CSV 초기 데이터를 PostGIS에 적재하는 자동화 스크립트.
- `docs/데이터_로드_가이드.md`: CSV 정제 및 로드 절차 설명.

## 5. 프로필 및 실행
- `local`: H2 기반 실행, 인프라 없이 빠른 테스트.
- `dev`: Docker PostGIS + Redis 연동, `schema-postgres.sql` 자동적용, `ddl-auto=validate`.
- `prod`: 운영 환경용, Swagger 비활성화, Redis/DB 연결은 환경 변수.

## 6. 문서
- `docs/원스톱_개발_가이드.md`: 신규 개발자용 원클릭 설정 가이드.
- `docs/공통응답_가이드.md`: 응답/예외 패턴 정리.
- `docs/데이터모델_개발가이드.md`, `docs/데이터모델_리뷰.md`: 엔티티/DB 설계 근거.
- `docs/데이터_로드_가이드.md`: CSV 수집 데이터 적재 방법.
- `docs/architecture/db_schema_overview.md`: 현재 스키마 요약.

## 7. 테스트/CI
- 테스트: Testcontainers(PostGIS 16-3.4)를 사용 (`application-test.yml`).
- GitHub Actions 워크플로(`.github/workflows/ci.yml`): dev 프로필로 `./gradlew test` 실행.

---

향후 서비스 레이어/컨트롤러/Repository가 추가되면 도메인별 패키지를 확장하고, 문서/스키마를 함께 업데이트합니다.
