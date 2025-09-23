# 주니어 개발자용 작업 지시서 (점심 추천 MVP)

이 문서는 `docs/RUA/tasks-prd-v1-점심-추천-mvp.md`의 태스크를 처음 수행하는 주니어 개발자를 위해 작성되었습니다. 목표는 **0 → 100**까지 필요한 배경지식, 준비 절차, 작업 방법, 검증 방법을 모두 안내하는 것입니다. 반드시 `docs/RUA/process-task-list.md`와 함께 사용하세요.

---

## 1. 사전 읽기 자료 (Why: 시스템 전체를 이해해야 올바른 설계/테스트가 가능)

1. `docs/architecture/project_structure.md` – 프로젝트 디렉터리와 모듈 관계를 이해합니다.
2. `docs/architecture/db_schema_overview.md` – 현재 DB 테이블 구조와 제약 조건을 숙지합니다.
3. `docs/데이터모델_개발가이드.md` & `docs/데이터모델_리뷰.md` – 엔티티 ↔ DDL 매핑 방식과 설계 근거를 파악합니다.
4. `docs/데이터_로드_가이드.md` – 실데이터가 어떤 과정을 거쳐 DB에 들어오는지 이해합니다.
5. `docs/공통응답_가이드.md` – API 응답 포맷/예외 처리 정책을 익혀 컨트롤러 구현 시 혼선을 방지합니다.
6. `docs/원스톱_개발_가이드.md` – 로컬 개발 환경을 셋업하고 실행하는 절차를 정리합니다.

> 읽을 때는 “이 기능이 왜 필요한가?”를 스스로 질문하고, 문서에서 답을 찾았는지 확인하세요. 이해가 되지 않은 부분은 별도 메모(예: `docs/RUA/questions.md`)에 기록하고 멘토에게 질문합니다.

---

## 2. 필수 배경지식 (Why: 구현 중 만나는 기술 요소를 미리 이해해야 시행착오를 줄임)

| 주제 | 학습 포인트 | 추천 자료 |
| --- | --- | --- |
| PostGIS/공간 쿼리 | EPSG 좌표계, `GEOGRAPHY(Point,4326)`, `ST_DWithin`, `ST_Distance` | PostGIS 공식 문서 Chapter 4, "PostGIS in Action" 2장 |
| Redis & Rate Limiting | bucket4j, Redis Key 설계, TTL | bucket4j docs + Redis 키스페이스 가이드 |
| Spring Data JPA & Querydsl | Native Query vs Querydsl, 커서 기반 조회 | Spring Data Reference + Querydsl docs |
| Spring Security (JWT) | RS256 검증, JWKS, 필터 체인 | Spring Security Reference + jose4j docs |
| Micrometer/Observability | MeterRegistry, Prometheus Exporter | Micrometer Reference + Spring Boot Actuator 가이드 |

모든 지식을 한 번에 마스터할 필요는 없지만, 각 기능을 시작하기 전에 **왜 이 기술이 필요한지**를 명확히 정리하세요. 예: “지오펜스 판정에 PostGIS가 필요한 이유 → 거리/반경 계산을 DB에서 정확히 처리하기 위해서.”

---

## 3. 개발 환경 준비 (Why: 테스트 가능한 일관된 환경이 있어야 결과를 재현하고 검증할 수 있음)

1. `./scripts/import_initial_places.sh docs/initial_data_set_need_change_provider_id.csv` 실행 – 좌표 변환/JSON 정제/UNIQUE 보정 자동 처리.
2. `docker compose -f infra/postgresql/docker-compose.yml up -d`
3. `docker compose -f infra/redis/docker-compose.yml up -d`
4. (옵션) 모니터링/ELK가 필요하면 `infra/metric`, `infra/elk`도 기동.
5. 애플리케이션을 `SPRING_PROFILES_ACTIVE=dev`로 실행 후 `/actuator/health`가 `UP`인지 확인.

> 왜? 데이터가 준비돼 있어야 검색/도착 기능을 구현하면서 즉시 테스트할 수 있고, Redis/PostGIS가 실행 중이어야 레이트리밋/멱등/지오펜스 로직이 정상 동작하기 때문입니다.

---

## 4. 작업 진행 방식 (process-task-list 규칙 적용)

1. 항상 `docs/RUA/process-task-list.md`를 열어 현재 진행 중인 태스크와 체크박스를 확인합니다.
2. **한 번에 하나의 서브태스크만** 진행합니다. (Why: 컨텍스트 스위칭을 줄이고 리뷰 품질을 높이기 위해)
3. 작업 시작 전, 다음을 수행하세요.
   - 관련 문서를 다시 훑고 필요한 API/SQL을 메모합니다.
   - “이 서브태스크가 해결하려는 문제는 무엇인가?”를 한 문장으로 정리하여 노트(예: `notes/current-task.md`)에 기록합니다.
4. 구현 중 지켜야 할 공통 원칙:
   - **테스트 우선**: 서비스/레포 로직을 작성하기 전에 테스트의 성공 조건을 정의합니다.
   - **파라미터 외부화**: PRD에서 언급한 상수(반경, dwell 시간 등)는 `application-*.yml`에 프로퍼티로 추가합니다.
   - **응답 표준 준수**: 컨트롤러는 DTO 또는 도메인 객체만 반환하고, `ApiResponseBodyAdvice`가 자동으로 감싸도록 합니다.
   - **예외 처리**: 도메인 오류는 `DomainException` 하위 클래스로 표현하고 적절한 코드/메시지를 제공합니다.
5. 서브태스크 완료 후
   1. 해당 테스트를 실행 (`./gradlew test`)
   2. `process-task-list.md`에서 체크박스를 `[x]`로 변경
   3. 사용자(멘토)에게 진행 상황을 공유하고 다음 단계 진행 여부 승인 받기
6. 부모 태스크의 서브태스크가 모두 완료되면 프로토콜에 따라 전체 테스트 → 정리 → 커밋(`feat:` 형식) → 부모 태스크 `[x]` → 다음 작업 순으로 진행합니다.

---

## 5. 검증 & 품질 체크 (Why: 기능이 의도대로 동작하는지, 성능/정합성이 확보됐는지 증명)

1. **기능 테스트**: API 엔드포인트마다 OpenAPI 스펙과 비교하여 필수 필드/예외 응답을 확인합니다.
2. **공간 연산 검증**: `ST_DWithin`, `ST_Distance`가 미터 단위로 작동하는지 샘플 쿼리 실행.
3. **성능 확인**: `EXPLAIN ANALYZE`를 통해 인덱스가 활용되고 있는지 확인합니다.
4. **레이트리밋/멱등**: Redis 키 TTL이 의도와 일치하는지 `redis-cli`로 조회.
5. **보안**: JWT 필터/JWKS 엔드포인트에 실패 케이스 테스트.
6. **관측성**: Micrometer 메트릭이 `/actuator/prometheus`에 노출되는지 확인하고, 대시보드 JSON을 Grafana에 임포트하여 시각화.

---

## 6. 학습 기록 & 회고

1. 작업 중 새로 알게 된 지식/문제점/질문은 `docs/RUA/notes/` 디렉터리에 기록합니다.
2. 작업이 끝나면 PRD 태스크 리스트에 느낀 점, 추가로 필요한 태스크를 보완합니다.
3. 멘토 리뷰 시 참고하도록 테스트 로그, SQL 결과, 스크린샷 등을 PR 코멘트에 첨부합니다.

---

### Quick Checklist Before Starting

- [ ] 관련 문서를 읽고 핵심 포인트 메모했는가?
- [ ] PostGIS/Redis/관측성 등 필요한 기초 개념을 정리했는가?
- [ ] Docker 인프라와 초기 데이터가 준비되었는가?
- [ ] 다음 수행할 서브태스크와 “왜 이걸 하는지”를 한 문장으로 정리했는가?
- [ ] 테스트 전략(무엇을 검증할지)이 준비되었는가?

위 항목이 모두 `[x]`라면 `process-task-list.md`의 첫 번째 서브태스크부터 순차적으로 진행합니다. 막히는 부분이 생기면 “왜 막혔는지, 어떤 상황인지, 지금까지 시도한 방법”을 정리해 질문하세요.
