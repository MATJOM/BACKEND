# Visit 상태 전이 이벤트 소비자 설계 가이드 (주니어 개발자용 0→100)

## 1. 목적
- `visit.state-events` 토픽으로 발행된 상태 전이 이벤트를 소비해 운영/분석 시스템에서 활용할 수 있는 소비자 애플리케이션을 설계한다.
- 예시 시나리오: 실시간 도착율 대시보드, Slack 알림, 사용자 보상 시스템 연동.

## 2. 목표
1. Kafka Consumer 그룹을 구성해 이벤트를 안정적으로 수신한다.
2. 이벤트 payload를 검증하고, 필요한 후속 처리(알림, 집계)를 수행한다.
3. 중복 이벤트 여부를 판단해 멱등성을 유지한다.

## 3. 소비자 아키텍처
- **입력**: Kafka 토픽 `visit.state-events`
- **처리 흐름**:
  1. JSON 디코딩 → DTO 매핑
  2. 이벤트 타입별 분기(ARRIVED/EXPIRED/CANCELLED)
  3. 알림/집계/데이터 저장 로직 수행
- **출력**: Slack, Database, Metrics 등

## 4. 구현 단계
### 4.1 프로젝트 설정
- Spring Boot 기반 소비자 서비스 생성 (별도 모듈 or 기존 서비스 내).
- `spring-kafka` 의존성 추가.
- `application.yml`에 consumer 설정: `bootstrap-servers`, `group-id`, `auto-offset-reset`, `enable-auto-commit=false`.

### 4.2 DTO 정의
```java
public class VisitStateEventPayload {
    private Long eventId;
    private Long sessionId;
    private UUID userId;
    private Long placeId;
    private String eventType;
    private String fromState;
    private String toState;
    private String source;
    private OffsetDateTime occurredAt;
    private JsonNode meta;
    // getters/setters
}
```

### 4.3 Consumer 리스너 작성
```java
@Component
public class VisitStateEventConsumer {

    @KafkaListener(topics = "visit.state-events", groupId = "visit-state-consumer")
    public void onMessage(String payload) {
        VisitStateEventPayload event = objectMapper.readValue(payload, VisitStateEventPayload.class);
        handleEvent(event);
    }

    private void handleEvent(VisitStateEventPayload event) {
        switch (event.getEventType()) {
            case "ARRIVED":
                arrivalHandler.process(event);
                break;
            case "EXPIRED":
                expiredHandler.process(event);
                break;
            default:
                auditHandler.process(event);
        }
    }
}
```

## 5. 멱등성 전략
- `eventId` 또는 `(sessionId,eventType,occurredAt)`를 기반으로 idempotent 저장.
- Redis/DB를 활용해 최근 처리 이벤트 ID를 캐싱하거나, VisitEvent 테이블에 `processed` 플래그 저장.

## 6. 예시 후속 처리
1. **알림**: `ARRIVED_MANUAL` → Slack 채널로 운영자 알림 전송.
2. **집계**: 도착율/만료율을 InfluxDB 혹은 TimescaleDB에 기록.
3. **운영 데이터 갱신**: 사용자 보상/배지 시스템에 도착 상태 전달.

## 7. 테스트 전략
- **단위**: KafkaListener를 분리해 이벤트 파서와 핸들러를 Mock 검증.
- **통합**: Testcontainers Kafka를 사용해 실제 메시지 수신 후 후속 처리 호출 확인.
- **부하**: 다량 이벤트 처리 시 소비자가 backlog 없이 따라가는지 부하 테스트.

## 8. 운영 고려사항
- Consumer Lag 모니터링 (Prometheus → Grafana).
- Dead-letter 토픽 구성: JSON 파싱 실패 등 오류 이벤트 이동.
- 재처리를 위한 manual seek 기능 적용.

> 이 가이드는 상태 전이 이벤트를 활용한 소비자 애플리케이션을 설계/구현하기 위한 로드맵을 제공한다. 주니어 개발자도 단계별로 진행하면 실시간 운영 파이프라인을 구축할 수 있다.
