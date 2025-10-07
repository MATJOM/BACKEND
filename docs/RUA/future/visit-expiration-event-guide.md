# Visit 만료 이벤트 기록/노출 가이드 (주니어 개발자용 0→100)

## 1. 배경
- 타임아웃 스케줄러가 세션을 EXPIRED로 전환하지만, 운영자가 어떤 세션이 언제 만료되었는지 추적할 근거가 부족하다.
- 수동 도착과 마찬가지로 만료 이벤트를 메타 데이터 혹은 별도 이벤트 스트림으로 남기면 감사, 대시보드, CS 대응에 활용할 수 있다.

## 2. 목표
1. 만료된 세션에 대해 `Visit.meta` 혹은 별도 테이블에 이벤트 레코드를 남긴다.
2. 필요한 경우 운영 도구/API에서 해당 정보를 조회할 수 있게 한다.
3. 향후 Kafka 등 외부 시스템으로 이벤트를 발행할 수 있는 확장 포인트를 마련한다.

## 3. 저장 방식 비교
| 방식 | 장점 | 단점 | 권장 사용 |
| --- | --- | --- | --- |
| Visit.meta(`history`) | 구현 간단, 트랜잭션 일관성 | JSON 크기 증가, 쿼리 어려움 | MVP/로컬 감사 용도 |
| VisitEvent 테이블 | SQL 조회 용이, 인덱스 가능 | 테이블 추가 필요 | 대량 데이터/운영 보고 |
| 이벤트 브로커(Kafka) | 실시간 분석/알림 가능 | 인프라 요구 높음 | 향후 확장 |

> 첫 단계로 meta.history에 기록하고, 필요 시 VisitEvent 테이블을 도입하는 방식을 추천.

## 4. meta.history에 만료 기록 추가하기
1. `VisitTimeoutScheduler`에서 상태 전환 직후 `Visit.meta` JSON을 갱신.
2. 구조 예시:
```json
{
  "history": [
    {
      "type": "expired",
      "occurredAt": "2025-02-27T06:32:00+09:00",
      "source": "timeout_scheduler"
    },
    {
      "type": "manual_arrival",
      "occurredAt": "2025-02-26T18:10:00+09:00",
      "requestedBy": "ops-admin"
    }
  ]
}
```
3. 구현 절차
   - `VisitSessionService`에서 사용한 `ObjectMapper`를 재사용하거나 공통 유틸을 만든다.
   - meta가 null이면 새 `ObjectNode` 생성.
   - `ArrayNode history = meta.withArray("history")`로 배열 확보 후 append.
   - `visit.setMeta(meta)` 호출.
4. 저장 후 `visitRepository.saveAll`로 영속화.

## 5. VisitEvent 테이블 도입 (선택)
1. 테이블 스키마
```
CREATE TABLE visit_events (
    event_id BIGSERIAL PRIMARY KEY,
    visit_id BIGINT NOT NULL REFERENCES visits(visit_id),
    event_type VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload JSONB
);
CREATE INDEX idx_visit_events_visit ON visit_events(visit_id, occurred_at);
```
2. JPA 엔터티/리포지토리 생성 → 스케줄러에서 `visitEventRepository.save` 호출.
3. payload에는 필요 시 추가 데이터(예: timeoutMinutes, 상태 이전/이후)를 넣는다.

## 6. 운영 도구/노출 방안
- `/api/v1/sessions/{id}` 응답에 `history` 배열을 포함시키거나 별도 `/events` 엔드포인트를 제공.
- Admin UI에서 만료 시간, 발생 원인(자동/수동)을 표시.
- 메트릭 시스템(Grafana)과 연동해 만료 건수를 모니터링.

## 7. 테스트 전략
1. **단위**: meta 업데이트 유틸 테스트 → 기존 meta에 append 되는지 확인.
2. **통합**: 만료 스케줄러 실행 후 Visit.meta에 `expired` 이벤트가 추가됐는지 검증.
3. **API 계약**: OpenAPI 문서에 history 스키마 반영, API 테스트로 응답 구조 확인.

## 8. 확장 아이디어
- Kafka/Cloud PubSub 등 메시지 큐로 이벤트 발행 → 실시간 알림, 데이터 파이프라인 연계.
- 이벤트 소비자가 Slack/Email 알림을 전송하도록 연동.
- meta/history에 기본 키나 버전을 두어 중복 기록 방지.

> 이 가이드를 따르면 세션 만료 이벤트를 체계적으로 기록·노출하여 운영/감사 요구에 대응할 수 있다.
