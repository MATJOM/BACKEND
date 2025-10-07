package com.matjom.matjom.visit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.base.UserException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.idempotency.IdempotencyCallback;
import com.matjom.matjom.common.idempotency.IdempotencyResult;
import com.matjom.matjom.common.idempotency.IdempotencyStore;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.place.repository.PlaceJpaRepository;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import com.matjom.matjom.visit.dto.VisitManualArrivalRequest;
import com.matjom.matjom.visit.dto.VisitManualArrivalResponse;
import com.matjom.matjom.visit.dto.VisitSessionStartRequest;
import com.matjom.matjom.visit.dto.VisitSessionStartResponse;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import com.matjom.matjom.visit.repository.VisitRepository;
import com.matjom.matjom.visit.util.GeoDistanceCalculator;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitSessionService {

    private static final String IDEMPOTENCY_PREFIX = "idemp:sessions:start:";
    private static final String ARRIVAL_IDEMPOTENCY_PREFIX = "idemp:sessions:arrival:";
    private static final long TIMEOUT_MINUTES = 30L;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final long MANUAL_ARRIVAL_MIN_SECONDS = 600L;
    private static final long MANUAL_ARRIVAL_MAX_SECONDS = 3600L;
    private static final double MANUAL_ARRIVAL_MAX_DISTANCE_METERS = 30.0;

    private final VisitRepository visitRepository;
    private final PlaceJpaRepository placeRepository;
    private final UserRepository userRepository;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;
    private final VisitStateTransitionRecorder stateTransitionRecorder;

    public VisitSessionService(VisitRepository visitRepository,
                               PlaceJpaRepository placeRepository,
                               UserRepository userRepository,
                               IdempotencyStore idempotencyStore,
                               ObjectMapper objectMapper,
                               VisitStateTransitionRecorder stateTransitionRecorder) {
        this.visitRepository = visitRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
        this.stateTransitionRecorder = Objects.requireNonNull(stateTransitionRecorder, "stateTransitionRecorder");
    }

    @Transactional
    public VisitSessionStartResponse startSession(final VisitSessionStartRequest request, String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;
        String requestHash = computeRequestHash(request);

        IdempotencyResult<VisitSessionStartResponse> result = idempotencyStore.replayOrRun(
                redisKey,
                requestHash,
                VisitSessionStartResponse.class,
                new IdempotencyCallback<VisitSessionStartResponse>() {
                    @Override
                    public VisitSessionStartResponse execute() {
                        return createSession(request);
                    }
                }
        );

        if (result.isReplayed()) {
            return markReplayed(result.getValue());
        }
        return result.getValue();
    }

    @Transactional
    public VisitManualArrivalResponse confirmManualArrival(Long sessionId,
                                                           VisitManualArrivalRequest request,
                                                           String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        String redisKey = ARRIVAL_IDEMPOTENCY_PREFIX + sessionId + ':' + idempotencyKey;
        String requestHash = computeRequestHash(request);

        IdempotencyResult<VisitManualArrivalResponse> result = idempotencyStore.replayOrRun(
                redisKey,
                requestHash,
                VisitManualArrivalResponse.class,
                new IdempotencyCallback<VisitManualArrivalResponse>() {
                    @Override
                    public VisitManualArrivalResponse execute() {
                        return processManualArrival(sessionId, request);
                    }
                }
        );

        if (result.isReplayed()) {
            return markManualReplayed(result.getValue());
        }
        return result.getValue();
    }

    private VisitSessionStartResponse createSession(VisitSessionStartRequest request) {
        UUID userId = request.getUserId();
        Long placeId = request.getPlaceId();

        Optional<User> optionalUser = userRepository.findById(userId);
        if (!optionalUser.isPresent()) {
            throw new UserException(ErrorCode.USER_NOT_FOUND);
        }
        User user = optionalUser.get();

        Optional<Place> optionalPlace = placeRepository.findById(placeId);
        if (!optionalPlace.isPresent()) {
            throw new PlaceException(ErrorCode.PLACE_NOT_FOUND);
        }
        Place place = optionalPlace.get();

        boolean activeExists = visitRepository.existsByUser_IdAndState(userId, VisitState.ACTIVE);
        if (activeExists) {
            throw new SessionException(ErrorCode.SESSION_ALREADY_EXISTS);
        }

        ClientMode mode = request.clientModeOrDefault();
        OffsetDateTime startedAt = OffsetDateTime.now(DEFAULT_ZONE);

        Visit visit = new Visit(user, place, mode, startedAt);
        OffsetDateTime expiresAt = startedAt.plusMinutes(TIMEOUT_MINUTES);
        visit.setExpiredAt(expiresAt);
        Visit saved = visitRepository.save(visit);

        return new VisitSessionStartResponse(saved.getId(), saved.getState(), saved.getStartedAt(), expiresAt, false);
    }

    private VisitSessionStartResponse markReplayed(VisitSessionStartResponse original) {
        return new VisitSessionStartResponse(
                original.sessionId(),
                original.state(),
                original.startedAt(),
                original.expiresAt(),
                true
        );
    }

    private VisitManualArrivalResponse processManualArrival(Long sessionId, VisitManualArrivalRequest request) {
        Optional<Visit> optionalVisit = visitRepository.findById(sessionId);
        if (optionalVisit.isEmpty()) {
            throw new SessionException(ErrorCode.SESSION_NOT_FOUND);
        }
        Visit visit = optionalVisit.get();
        VisitState previousState = visit.getState();
        if (visit.getState() != VisitState.ACTIVE) {
            throw new SessionException(ErrorCode.SESSION_ALREADY_INACTIVE);
        }

        OffsetDateTime startedAt = visit.getStartedAt();
        OffsetDateTime now = OffsetDateTime.now(DEFAULT_ZONE);
        long elapsedSeconds = Duration.between(startedAt, now).getSeconds();
        if (elapsedSeconds < MANUAL_ARRIVAL_MIN_SECONDS || elapsedSeconds > MANUAL_ARRIVAL_MAX_SECONDS) {
            throw new SessionException(ErrorCode.ARRIVAL_TIME_INVALID);
        }

        Place place = visit.getPlace();
        BigDecimal placeLat = place.getLatitude();
        BigDecimal placeLng = place.getLongitude();
        double distanceMeters = GeoDistanceCalculator.distanceMeters(
                placeLat,
                placeLng,
                request.getLatitude(),
                request.getLongitude());
        if (distanceMeters > MANUAL_ARRIVAL_MAX_DISTANCE_METERS) {
            throw new SessionException(ErrorCode.ARRIVAL_DISTANCE_EXCEEDED);
        }

        visit.updateLastPosition(request.getLatitude(), request.getLongitude(), request.getAccuracyMeters(), now);
        visit.arriveAt(now);
        visitRepository.save(visit);

        stateTransitionRecorder.record(visit, previousState, visit.getState(), VisitStateEventSource.MANUAL_ARRIVAL, now);

        return new VisitManualArrivalResponse(
                visit.getId(),
                visit.getState(),
                visit.getArrivedAt(),
                request.getRequestedBy(),
                false
        );
    }

    private VisitManualArrivalResponse markManualReplayed(VisitManualArrivalResponse original) {
        return new VisitManualArrivalResponse(
                original.sessionId(),
                original.state(),
                original.arrivedAt(),
                original.requestedBy(),
                true
        );
    }

    private String computeRequestHash(Object request) {
        byte[] jsonBytes = toJsonBytes(request);
        MessageDigest digest = messageDigest();
        byte[] hashed = digest.digest(jsonBytes);
        StringBuilder builder = new StringBuilder(hashed.length * 2);
        for (byte value : hashed) {
            int unsigned = value & 0xFF;
            String hex = Integer.toHexString(unsigned);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    private byte[] toJsonBytes(Object request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException ex) {
            throw new SessionException(ErrorCode.INTERNAL_SERVER_ERROR, "세션 요청 직렬화에 실패했습니다.");
        }
    }

    private MessageDigest messageDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 해시 함수를 사용할 수 없습니다.", ex);
        }
    }
}
