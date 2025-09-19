# MATJOM BACKEND

MATJOM 프로젝트 백엔드 저장소입니다. 현재는 스켈레톤 단계로 기본적인 레이어와 실행 환경을 구성하고 있습니다.

## 필수 요구 사항

- Java 21 (Gradle Toolchain이 자동으로 다운로드하므로 JVM만 설치되어 있으면 됩니다)
- 로컬 실행 시 추가 인프라 없이도 동작하도록 `H2` 인메모리 DB 프로필을 기본값으로 설정했습니다.

## 실행 방법

```bash
# 프로젝트 루트에서 실행
GRADLE_USER_HOME=.gradle ./gradlew bootRun
```

- 별도의 `SPRING_PROFILES_ACTIVE` 값을 지정하지 않으면 `local` 프로필이 적용되어 H2 DB와 함께 서버가 바로 기동됩니다.
- Postgres/Redis 등을 사용해 실제 개발 환경을 구성하려면 `SPRING_PROFILES_ACTIVE=dev` 로 실행하고, `infra/` 디렉터리의 도커 컴포즈 파일을 통해 의존 서비스를 띄우면 됩니다.

## 주요 디렉터리 구조

- `src/main/java/com/matjom/matjom` : 스프링 부트 애플리케이션 시작점 및 추후 도메인 코드 위치
- `src/main/resources` : 공통 설정(`application.yml`)과 환경별 설정(`application-*.yml`)
- `infra/` : Postgres, Redis, ELK, Prometheus/Grafana 등을 위한 도커 컴포즈 템플릿
- `docs/` : 설계 및 협업 문서 정리 예정

## API 응답 규약

- `com.matjom.matjom.common.response.ApiResponse` 클래스를 통해 모든 REST 응답을 `{ success, data, error, timestamp }` 형태로 통일했습니다.
- 컨트롤러에서 객체/DTO를 그대로 반환하면 `ApiResponseBodyAdvice`가 자동으로 감싸며, 직접 제어가 필요하면 `ApiResponse.ok(...)`, `ApiResponse.error(...)`를 사용하세요.
- 예외는 `GlobalExceptionHandler`가 수신해 공통 에러 포맷으로 내려보냅니다. 도메인 오류를 표현하려면 `DomainException` 계층(예: `UserException`)을 사용하거나 확장하고, 필수로 `ErrorCode`를 지정합니다.

## 엔티티 공통 규약

- 모든 JPA 엔티티는 `com.matjom.matjom.common.entity.BaseEntity`를 상속해 `created_at`, `updated_at`, `deleted_at`을 공통으로 사용합니다.
- `created_at`과 `updated_at`은 Spring Data JPA Auditing으로 자동 업데이트됩니다. 소프트 딜리트를 적용할 때는 `markDeleted()`/`restore()`를 사용해 `deleted_at`을 관리하세요.

### 예시

성공 응답 (`GET /example`):

```json
{
  "success": true,
  "data": {
    "name": "MATJOM"
  },
  "timestamp": "2025-09-19T04:00:00Z"
}
```

에러 응답 (유효성 실패):

```json
{
  "success": false,
  "error": {
    "code": "INVALID_REQUEST_PARAM",
    "message": "필수 입력값이 누락되었습니다."
  },
  "timestamp": "2025-09-19T04:00:00Z"
}
```

## 인프라 연결 테스트 기록

도커로 띄운 Postgres(`db-postgis`)와 Redis(`cache-redis`) 컨테이너가 애플리케이션(dev 프로필)이 기대하는 호스트/포트에서 정상적으로 동작하는지 아래와 같이 확인했습니다.

```bash
# 컨테이너 상태 확인
docker ps --format '{{.Names}}	{{.Status}}	{{.Ports}}'

# Postgres 헬스체크 및 접속 검증
docker exec db-postgis pg_isready -U devuser
docker exec db-postgis psql -U devuser -d matjom_dev -c 'SELECT current_database(), current_user;'

# Redis 핑/쓰기/조회 검증
docker exec cache-redis redis-cli ping
docker exec cache-redis redis-cli set healthcheck ok
docker exec cache-redis redis-cli get healthcheck
```

위 명령 결과 `db-postgis`는 `matjom_dev` 데이터베이스에 `devuser` 계정으로 접속 가능했고, Redis는 키/값 쓰기와 `PING` 응답이 모두 정상적으로 반환되었습니다. 따라서 `application-dev.yml`이 가리키는 로컬 포트(`5432`, `6379`)에서 두 서비스가 준비 완료 상태임을 확인했습니다.
