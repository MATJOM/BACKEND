# Visit 타임아웃 스케줄러 분산 락 적용 가이드 (주니어 개발자용 0→100)

## 1. 배경
- 현재 타임아웃 스케줄러는 단일 인스턴스 전제로 구현되어 있어 다중 서버에서 동시에 실행되면 동일 세션이 여러 번 만료될 수 있다.
- 운영 환경에 여러 애플리케이션 인스턴스가 떠 있는 경우 중복 실행을 방지하는 분산 락이 필요하다.

## 2. 목표
1. 스케줄러 실행 전 분산 락을 획득한 프로세스만 만료 작업을 수행하게 한다.
2. 일정 시간이 지나면 락이 자동으로 해제되어 장애 상황에서도 복구가 가능해야 한다.
3. 락 구현은 Redis를 활용하며, 기존 프로젝트에 이미 도입된 Lettuce/RedisTemplate를 재사용한다.

## 3. 준비 사항
- Redis 연결이 설정되어 있어야 한다. (현재 Idempotency, RateLimit 구성에서 사용 중)
- 스케줄러가 락 획득을 실패했을 때 graceful 하게 빠져나가야 함.
- Spring Bean으로 RedisTemplate 혹은 Lettuce `StatefulRedisConnection`를 주입받을 수 있어야 함.

## 4. 설계 개요
1. **락 키 정의**: `visit:timeout:lock`
2. **획득 방법**: `SET key value NX PX ttl` (Lettuce → `setIfAbsent` + `expire` 조합)
3. **TTL 설정**: 스케줄러 실행 주기(예: 60초)보다 길고, 만료 배치 최대 소요 시간보다 약간 크게 설정(예: 120초)
4. **락 해제**: 실행 완료 후 `DEL key` (단, 실행 중 인스턴스만 삭제하도록 value 비교 후 삭제)

## 5. 구현 단계 (Step-by-Step)
### 5.1 LockManager 컴포넌트 작성
```java
@Component
public class RedisLockManager {

    private final StringRedisTemplate redisTemplate;

    public RedisLockManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquire(String key, String value, Duration ttl) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(success);
    }

    public void release(String key, String value) {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            byte[] keyBytes = redisTemplate.getStringSerializer().serialize(key);
            byte[] valueBytes = redisTemplate.getStringSerializer().serialize(value);
            byte[] stored = connection.stringCommands().get(keyBytes);
            if (stored != null && java.util.Arrays.equals(stored, valueBytes)) {
                connection.keyCommands().del(keyBytes);
            }
            return null;
        });
    }
}
```

### 5.2 스케줄러에 락 적용
1. `VisitTimeoutScheduler`에 `RedisLockManager` 주입.
2. `expireTimedOutVisits()` 시작 시 고유 value(예: UUID) 생성 → `acquire` 시도.
3. 실패하면 즉시 return.
4. try/finally 블록에서 실행 후 `release` 호출.

### 5.3 예외 처리
- 락 획득 후 만료 작업에서 예외 발생 시에도 finally에서 해제되도록 보장.
- 예외 로그에 락 정보(value) 포함.

## 6. 테스트 전략
1. **단위 테스트**: LockManager가 `setIfAbsent` 호출하는지 Mockito로 검증.
2. **통합 테스트**: 임베디드 Redis (Testcontainers)로 두 개의 스케줄러 인스턴스 시뮬레이션 → 하나만 실행됨 확인.
3. **회귀**: `./gradlew test` 전체 실행.

## 7. 배포 체크리스트
- Redis 커넥션 타임아웃/재시도 설정 확인.
- 운영 모니터링에 락 획득 실패율 등을 추가할 수 있도록 로그 레벨 점검.
- 장애 시 락이 해제되지 않으면 TTL 만료 후 재시도되므로 TTL 값을 반드시 설정.

## 8. 향후 확장
- Redisson과 같은 고수준 분산락 라이브러리 도입 → Watchdog 자동 연장.
- 락 획득 실패 시 지표를 발행해 이상 징후 감지.

> 이 가이드를 따르면 Redis 분산 락을 적용해 다중 인스턴스 상황에서도 타임아웃 스케줄러가 안전하게 동작하도록 개선할 수 있다.
