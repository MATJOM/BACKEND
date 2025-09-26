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
import com.matjom.matjom.visit.dto.VisitSessionStartRequest;
import com.matjom.matjom.visit.dto.VisitSessionStartResponse;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.repository.VisitRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final long TIMEOUT_MINUTES = 30L;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");

    private final VisitRepository visitRepository;
    private final PlaceJpaRepository placeRepository;
    private final UserRepository userRepository;
    private final IdempotencyStore idempotencyStore;
    private final ObjectMapper objectMapper;

    public VisitSessionService(VisitRepository visitRepository,
                               PlaceJpaRepository placeRepository,
                               UserRepository userRepository,
                               IdempotencyStore idempotencyStore,
                               ObjectMapper objectMapper) {
        this.visitRepository = visitRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.idempotencyStore = idempotencyStore;
        this.objectMapper = objectMapper;
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
        Visit saved = visitRepository.save(visit);

        OffsetDateTime expiresAt = startedAt.plusMinutes(TIMEOUT_MINUTES);
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

    private String computeRequestHash(VisitSessionStartRequest request) {
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

    private byte[] toJsonBytes(VisitSessionStartRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException ex) {
            throw new SessionException(ErrorCode.INTERNAL_SERVER_ERROR, "세션 시작 요청 직렬화에 실패했습니다.");
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
