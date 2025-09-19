# MATJOM Infra Guide

## 개요

- `infra/` 디렉터리는 로컬 개발용 의존 서비스(PostgreSQL, Redis, ELK, Prometheus/Grafana)를 Docker Compose로 제공한다.
- 각 스택은 독립 실행이 가능하도록 분리되어 있으며 필요 서비스만 선택적으로 올릴 수 있다.
- 애플리케이션 `dev` 프로필은 Postgres(`localhost:5432`)와 Redis(`localhost:6379`)를 기본으로 참조한다.

## 공통 실행법

```bash
# 예: Postgres와 Redis를 동시에 실행
docker compose -f infra/postgresql/docker-compose.yml up -d
docker compose -f infra/redis/docker-compose.yml up -d

# 종료
docker compose -f infra/postgresql/docker-compose.yml down
docker compose -f infra/redis/docker-compose.yml down
```

- 각 compose 파일은 디렉터리 기준 상대경로로 볼륨을 마운트하므로 프로젝트 루트에서 실행하는 것을 권장한다.
- 컨테이너 상태는 `docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'` 명령으로 확인한다.

## PostgreSQL (postgis)

- 파일: `infra/postgresql/docker-compose.yml`
- 이미지: `postgis/postgis:16-3.4`
- 기본 포트: `5432` (호스트에 그대로 노출)
- 환경 변수: `POSTGRES_USER=devuser`, `POSTGRES_PASSWORD=devpass`, `POSTGRES_DB=matjom_dev`
- 영속 볼륨: `pg-data` → `./infra/postgresql` 기준 `var/lib/postgresql/data`
- 헬스체크: `pg_isready -U devuser`

**연결 확인**

```bash
docker exec db-postgis pg_isready -U devuser
docker exec db-postgis psql -U devuser -d matjom_dev -c 'SELECT current_database(), current_user;'
```

## Redis

- 파일: `infra/redis/docker-compose.yml`
- 이미지: `redis:7-alpine`
- 기본 포트: `6379`
- 명령: AOF(`--appendonly yes`) 활성화
- 영속 볼륨: `redis-data` → `/data`
- 헬스체크: `redis-cli ping`

**연결 확인**

```bash
docker exec cache-redis redis-cli ping
docker exec cache-redis redis-cli set healthcheck ok
docker exec cache-redis redis-cli get healthcheck
```

## 모니터링 (Prometheus / Grafana)

- 파일: `infra/metric/docker-compose.yml`
- 구성: Prometheus(`9090`), Grafana(`3000`), 후자는 기본 admin/admin으로 로그인 가능
- Prometheus 설정: `infra/metric/prometheus.yml`
  - 기본적으로 `host.docker.internal:8080`의 `/actuator/prometheus`를 스크레이프
  - Linux에서는 호스트 IP를 직접 지정하거나 애플리케이션을 컨테이너로 띄워 동일 네트워크에 두어야 한다.

**실행 후 확인**

- Prometheus: `http://localhost:9090/targets`
- Grafana: `http://localhost:3000` (초기 로그인 후 데이터 소스로 Prometheus URL `http://prometheus:9090` 등록)

## 로깅 스택 (ELK)

- 파일: `infra/elk/docker-compose.yml`
- 구성: Elasticsearch(`9200`), Kibana(`5601`), Logstash(`5044`), Filebeat(호스트 로그 수집)
- Elasticsearch는 싱글 노드, 보안 비활성화. 데이터는 `es-data` 볼륨에 보존.
- Logstash 파이프라인: `infra/elk/logstash.conf`
- Filebeat 설정: `infra/elk/filebeat/filebeat.yml` (호스트 `../../logs/*.log`를 읽어 Logstash에 전송)
- Filebeat가 호스트 로그를 읽기 위해 `../../logs`가 존재하고 읽기 가능한 상태인지 확인 필요.

**실행 후 확인**

- Elasticsearch: `curl http://localhost:9200` → 클러스터 정보 응답 확인
- Kibana: `http://localhost:5601` 접속 후 인덱스 패턴 `matjom-*` 생성
- Filebeat → Logstash 파이프라인 로그는 Logstash 컨테이너(stdout)로도 출력된다.

## 트러블슈팅 메모

- 포트를 이미 다른 프로세스가 사용 중이면 compose 실행이 실패하므로 `lsof -i TCP:포트`로 충돌 프로세스를 종료하거나 compose 포트를 수정한다.
- 컨테이너에 들어가야 할 경우 `docker exec -it <container> sh`(혹은 `bash`)로 진입한다.
- 애플리케이션에서 DB/Redis 연결 오류가 발생하면 JVM 실행 환경 변수 `SPRING_PROFILES_ACTIVE`가 올바른지와 컨테이너 상태를 먼저 확인한다.
