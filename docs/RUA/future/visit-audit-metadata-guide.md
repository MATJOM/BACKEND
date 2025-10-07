# Visit 감사 로그 메타 설계 가이드 (주니어 개발자용 0→100)

## 1. 왜 이 작업이 필요한가?
- **문제 상황**: 현재 수동 도착(Manual Arrival) 같은 운영자 개입 이벤트는 `Visit` 엔터티의 상태만 변경하며, 누가·언제·왜 변경했는지에 대한 근거가 남지 않는다.
- **위험 요소**: 감사(Audit) 정보가 없으면 사용자가 항의할 때 재현이 불가능하고, 내부 운영 정책 준수 여부를 입증하기 어렵다.
- **목표**: 수동 도착 같은 운영 행위가 발생할 때, 관련 메타데이터를 `Visit` 엔터티의 `meta`(JSONB) 필드에 영구 저장해 감사 로그로 활용한다.

## 2. 현재 프로젝트 구조 이해하기
1. `Visit` 엔터티 (`src/main/java/com/matjom/matjom/visit/entity/Visit.java`)
   - `meta` 필드는 `JsonNode` 타입으로 선언되어 있으며, 현재 비어있거나 예약용 상태.
   - 수동 도착 API에서 `visit.arriveAt(...)`을 호출한 직후 meta를 업데이트하면 된다.
2. DTO 및 서비스 흐름
   - `VisitManualArrivalRequest`: `requestedBy`, `latitude`, `longitude`, `accuracyMeters` 제공.
   - `VisitSessionService.confirmManualArrival(...)`: 도착 처리를 담당하는 서비스 레이어.
   - 멱등 저장(IdempotencyStore)로 동일 요청을 60초 동안 재생.

## 3. 감사 메타에 무엇을 저장할까?
| 키 | 타입 | 예시 | 설명 |
| --- | --- | --- | --- |
| `manualArrival` | 객체 | `{ "requestedBy": "ops-admin", "requestedAt": "2025-02-27T06:12:00+09:00", "reason": "사용자 신고", "source": "ops-console", "location": { "lat": 37.5665, "lng": 126.9780, "accuracyMeters": 8.5 } }` | 최근 수동 도착 정보. |
| `history` | 배열 | `[ { "type": "manual_arrival", "requestedBy": "ops-admin", "requestedAt": "..." } ]` | 동일 Visit에 여러 운영 이벤트가 쌓일 경우.

> **권장 저장 방식**: `manualArrival`에 최신 정보, `history` 배열에 연대기 기록을 남기면 운영자가 추후 모든 변경을 추적 가능.

## 4. 구현 절차 (Step-by-Step)
### 4.1 매퍼/유틸 준비
1. `ObjectMapper`는 이미 `VisitSessionService`에 존재.
2. `JsonNode` 갱신을 위해 Jackson의 `ObjectNode`, `ArrayNode` 사용.

```java
ObjectNode meta = visit.getMeta() == null
        ? objectMapper.createObjectNode()
        : (ObjectNode) objectMapper.readTree(visit.getMeta().toString());
```

### 4.2 수동 도착 처리부 수정
1. `VisitSessionService.processManualArrival(...)`에서 도착 확정 직후 meta 업데이트.
2. `manualArrival` 객체 생성 → `requestedBy`, `requestedAt`, `reason`(추후 파라미터로 확장 가능), 위치 정보 세팅.
3. `history` 배열에 `manual_arrival` 이벤트 append.
4. `visit.setMeta(meta)` 메서드를 추가 구현(없다면 `Visit` 엔터티에 세터 생성).

### 4.3 Visit 엔터티에 세터 추가
```java
public void setMeta(JsonNode meta) {
    this.meta = meta;
}
```

### 4.4 트랜잭션 내 저장
- 기존 `visitRepository.save(visit)` 호출 전에 meta 세팅만 추가하면 된다.
- 멱등 재생 시에도 meta가 동일하게 내려오도록 IdempotencyStore가 저장한 응답(`VisitManualArrivalResponse`)에 `requestedBy`를 포함했으므로 추가 작업 불필요.

## 5. 테스트 전략
1. **단위 테스트**: `VisitSessionServiceTest.confirmManualArrivalTransitionsToArrived`에서 meta 작성 여부 검증 (ObjectMapper로 JsonNode 비교).
2. **경계 테스트**: history 배열 append 로직이 기존 데이터 유지하는지 확인.
3. **멱등 테스트**: 동일 멱등 키 재호출 시 meta가 덮어쓰이지 않고 그대로 유지되는지 점검.

## 6. 마이그레이션/배포 고려사항
- Postgres `jsonb` 필드는 스키마 변경 없이 사용 가능 → DB 마이그레이션 불필요.
- meta 구조 변경 시 Null-safe 접근을 위해 JSON Path fetch 시 `has` 체크 필수.
- 운영자 콘솔에서 meta를 읽는 API 혹은 대시보드가 필요할 수 있으므로, 공통 DTO로 변환하는 계층 준비.

## 7. 향후 확장 아이디어
1. **Reason 코드화**: `MANUAL_FIX`, `SUPPORT_CALL`, `SYSTEM_BACKFILL` 등 enum 도입.
2. **공통 감사 모듈**: Visit 외 다른 도메인에서도 동일 패턴 재사용.
3. **이벤트 스트림 연동**: meta 저장과 동시에 Kafka 등으로 이벤트 발행.
4. **운영 도구 노출**: meta에서 `manualArrival` 정보를 읽어 UI에 표시.

## 8. 작업 체크리스트
- [ ] `Visit` 엔터티에 meta 세터/헬퍼 추가
- [ ] `processManualArrival`에서 meta 작성 로직 추가
- [ ] 서비스/테스트 수정(정상/멱등/여러 이벤트)
- [ ] OpenAPI에서 meta 필드 예시 제공(선택)
- [ ] 운영 문서 업데이트 (어떤 값이 저장되는지)

> 위 순서를 차례로 수행하면, 주니어 개발자도 수동 도착 감사 로그를 Visit 메타에 안전하게 남길 수 있다.
