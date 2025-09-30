package com.matjom.matjom.statistics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.statistics.repository.PlaceDailyStatsBatchRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStatsBatchService {

    private final PlaceDailyStatsBatchRepository batchRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private final ReentrantLock runLock = new ReentrantLock();
    private final AtomicReference<BatchStatus> lastStatus = new AtomicReference<>(BatchStatus.idle());

    // 자정 배치를 실행해 일별 통계를 모으고 저장한다.
    @Transactional
    public BatchResult runAggregation(LocalDate targetDate) {
        runLock.lock();
        try {
            lastStatus.set(BatchStatus.running(targetDate));
            OffsetDateTime aggregatedAt = OffsetDateTime.now(clock);
            Map<Long, Aggregate> aggregates = collectAggregates(targetDate);

            for (Map.Entry<Long, Aggregate> entry : aggregates.entrySet()) {
                Long placeId = entry.getKey();
                Aggregate aggregate = entry.getValue();

                String hourlyArrives = toJson(aggregate.hourlyArrives);
                String hourlyStarts = toJson(aggregate.hourlyStarts);

                batchRepository.upsertDailyStats(
                        targetDate,
                        placeId,
                        aggregate.starts,
                        aggregate.arrives,
                        aggregate.reviews,
                        aggregate.likes,
                        hourlyArrives,
                        hourlyStarts,
                        aggregatedAt
                );
            }

            BatchResult result = new BatchResult(targetDate, aggregatedAt, aggregates.size());
            lastStatus.set(BatchStatus.completed(result));
            return result;
        } catch (Exception ex) {
            log.error("Failed to run daily stats aggregation for date {}", targetDate, ex);
            OffsetDateTime failedAt = OffsetDateTime.now(clock);
            lastStatus.set(BatchStatus.failed(targetDate, failedAt, ex.getMessage()));
            throw ex;
        } finally {
            runLock.unlock();
        }
    }

    // 가장 최근 배치 실행 상태를 모니터링 용도로 외부에 노출한다.
    public BatchStatus getLastStatus() {
        return lastStatus.get();
    }

    // 여러 네이티브 쿼리 결과를 모아 장소별 집계 스냅샷을 구성한다.
    private Map<Long, Aggregate> collectAggregates(LocalDate targetDate) {
        Map<Long, Aggregate> aggregates = new HashMap<>();

        batchRepository.fetchVisitStats(targetDate).forEach(row -> {
            Long placeId = ((Number) row[0]).longValue();
            Aggregate aggregate = aggregates.computeIfAbsent(placeId, k -> new Aggregate());
            aggregate.starts = ((Number) row[1]).longValue();
            aggregate.arrives = ((Number) row[2]).longValue();
        });

        batchRepository.fetchHourlyStarts(targetDate).forEach(row -> {
            Long placeId = ((Number) row[0]).longValue();
            Aggregate aggregate = aggregates.computeIfAbsent(placeId, k -> new Aggregate());
            int hour = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            aggregate.hourlyStarts.put(hour, count);
        });

        batchRepository.fetchHourlyArrives(targetDate).forEach(row -> {
            Long placeId = ((Number) row[0]).longValue();
            Aggregate aggregate = aggregates.computeIfAbsent(placeId, k -> new Aggregate());
            int hour = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            aggregate.hourlyArrives.put(hour, count);
        });

        batchRepository.fetchReviewStats(targetDate).forEach(row -> {
            Long placeId = ((Number) row[0]).longValue();
            Aggregate aggregate = aggregates.computeIfAbsent(placeId, k -> new Aggregate());
            aggregate.reviews = ((Number) row[1]).longValue();
        });

        batchRepository.fetchLikeStats(targetDate).forEach(row -> {
            Long placeId = ((Number) row[0]).longValue();
            Aggregate aggregate = aggregates.computeIfAbsent(placeId, k -> new Aggregate());
            aggregate.likes = ((Number) row[1]).longValue();
        });

        return aggregates;
    }

    // 시간대별 카운트를 JSON 문자열로 직렬화하며 실패 시 빈 객체로 대체한다.
    private String toJson(Map<Integer, Long> hourlyMap) {
        if (hourlyMap.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(hourlyMap);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize hourly stats to JSON, falling back to empty object", ex);
            return "{}";
        }
    }

    private static class Aggregate {
        long starts;
        long arrives;
        long reviews;
        long likes;
        Map<Integer, Long> hourlyArrives = new TreeMap<>();
        Map<Integer, Long> hourlyStarts = new TreeMap<>();
    }

    @Getter
    public static class BatchResult {
        private final LocalDate targetDate;
        private final OffsetDateTime executedAt;
        private final int processedPlaces;

        public BatchResult(LocalDate targetDate, OffsetDateTime executedAt, int processedPlaces) {
            this.targetDate = targetDate;
            this.executedAt = executedAt;
            this.processedPlaces = processedPlaces;
        }
    }

    @Getter
    public static class BatchStatus {
        private final boolean running;
        private final boolean success;
        private final LocalDate targetDate;
        private final OffsetDateTime executedAt;
        private final String message;

        private BatchStatus(boolean running,
                            boolean success,
                            LocalDate targetDate,
                            OffsetDateTime executedAt,
                            String message) {
            this.running = running;
            this.success = success;
            this.targetDate = targetDate;
            this.executedAt = executedAt;
            this.message = message;
        }

        // 아직 배치를 실행하지 않은 초기 유휴 상태를 나타낸다.
        public static BatchStatus idle() {
            return new BatchStatus(
                    false,
                    true,
                    null,
                    null,
                    "IDLE");
        }

        // 성공적으로 집계가 끝났음을 실행 시각과 함께 기록한다.
        public static BatchStatus completed(BatchResult result) {
            return new BatchStatus(
                    false,
                    true,
                    result.getTargetDate(),
                    result.getExecutedAt(),
                    "COMPLETED");
        }

        // 지정한 대상 일자에 대한 배치가 진행 중임을 알린다.
        public static BatchStatus running(LocalDate targetDate) {
            return new BatchStatus(
                    true,
                    false,
                    targetDate,
                    null,
                    "RUNNING");
        }

        // 실패한 배치의 대상 일자와 메시지를 담아 디버깅에 활용한다.
        public static BatchStatus failed(LocalDate targetDate, OffsetDateTime executedAt, String message) {
            return new BatchStatus(
                    false,
                    false,
                    targetDate,
                    executedAt,
                    message);
        }
    }

}
