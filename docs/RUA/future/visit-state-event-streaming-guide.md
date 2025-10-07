# Visit 상태 전이 이벤트 스트리밍 설계 가이드 (주니어 개발자용 0→100)

## 1. 배경
- 현재 상태 전이 이벤트는 DB(`visit_events`)에 저장되지만, 운영 알림·실시간 분석을 위해 외부 시스템(Kafka 등)으로도 발행할 필요가 있다.
- 이벤트 스트림을 활용하면 실시간 대시보드, 알림, 데이터 파이프라인과 연동할 수 있다.

## 2. 목표
1. Visit 상태 전이 시 Kafka 토픽(`visit.state-events`)에 JSON 이벤트를 발행한다.
2. 이벤트는 멱등해야 하며, 실패 시 재시도 로직을 갖춰야 한다.
3. 소비자가 이벤트를 수신해 운영/BI 요구에 활용할 수 있도록 schema를 명확히 정의한다.

## 3. 아키텍처 개요
- **Producer**: `VisitStateTransitionRecorder` → Kafka Template
- **Topic**: `visit.state-events`
- **Schema** 예시 (JSON):
```json
{
  "eventId": 12345,
  "sessionId": 1001,
  "userId": "0f15a3c8-7a1d-4c91-8a0c-458c64be8f10",
  "placeId": 321,
  "eventType": "ARRIVED",
  "fromState": "ACTIVE",
  "toState": "ARRIVED",
  "source": "AUTO_ARRIVAL",
  "occurredAt": "2025-02-27T06:32:00+09:00",
  "meta": {}
}
```

## 4. 구현 단계
### 4.1 Kafka 의존성 및 설정
1. `build.gradle`에 Spring Kafka 의존성 추가.
2. `application.yml`에 Kafka 연결 정보(bootstrap servers, topic 이름, producer config)를 정의.

### 4.2 Producer 컴포넌트 작성
1. `VisitEventPublisher` 클래스를 생성해 `KafkaTemplate<String, String>` 주입.
2. `publish(VisitEvent event)` 메서드에서 JSON 직렬화 후 `kafkaTemplate.send(topic, key, payload)` 호출.
   - key: `sessionId` 또는 `userId`
   - payload: ObjectMapper → JSON
3. 전송 결과는 콜백으로 성공/실패 로깅.

### 4.3 Recorder와 연동
- `VisitStateTransitionRecorder.record`에서 이벤트 저장 후 `VisitEventPublisher.publish(event)` 호출.
- 실패 시 이벤트 저장 트랜잭션과 함께 롤백할지, 별도 재시도 큐에 넣을지 정책을 결정.

## 5. 실패 처리 & 재시도 전략
1. **동기 전송 + 예외 전파**: Kafka 전송 실패 시 RuntimeException 발생 → 상태 전이 자체 롤백. 간단하지만 가용성 저하 가능.
2. **비동기 전송 + 재시도 큐**: 전송 실패 시 Dead-letter queue 혹은 재시도 토픽에 적재 → 백그라운드 워커가 재전송.
3. **추적 인덱스**: VisitEvent 테이블에 `published` 플래그와 `published_at`을 남겨 누락 이벤트를 주기적으로 재전송.

## 6. 소비자 설계 (권장안)
- 별도 서비스나 Stream Processor(예: Flink, Spark)에서 `visit.state-events` 토픽을 구독.
- 처리 결과 예시: 운영 알림(Slack), 실시간 도착율 대시보드, 사용자 행동 분석 파이프라인.

## 7. 테스트 전략
1. **단위 테스트**: `VisitEventPublisherTest`에서 KafkaTemplate mock을 사용해 payload가 올바른지 검증.
2. **통합 테스트**: Testcontainers-Kafka로 실제 토픽 발행 여부를 확인.
3. **회귀 테스트**: `VisitStateTransitionRecorder`가 이벤트 저장 후 publish를 호출하는지 Mockito로 검증.

## 8. 배포 & 모니터링
- Kafka topic 생성, retention, partition 수 결정.
- Grafana/Prometheus로 producer 전송 성공/실패 metric 수집.
- Dead-letter 토픽을 모니터링하고 경고 발송.

> 이 가이드는 방문 세션 상태 전이를 실시간 이벤트로 스트리밍하기 위한 설계/구현 지침을 제공한다. 주니어 개발자도 순서대로 진행하면 Kafka 연동을 완성할 수 있다.
