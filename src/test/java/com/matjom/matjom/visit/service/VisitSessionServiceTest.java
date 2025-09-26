package com.matjom.matjom.visit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.idempotency.IdempotencyCallback;
import com.matjom.matjom.common.idempotency.IdempotencyResult;
import com.matjom.matjom.common.idempotency.IdempotencyStore;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.place.repository.PlaceJpaRepository;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import com.matjom.matjom.visit.dto.VisitSessionStartRequest;
import com.matjom.matjom.visit.dto.VisitSessionStartResponse;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.repository.VisitRepository;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitSessionServiceTest {

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private PlaceJpaRepository placeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IdempotencyStore idempotencyStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private VisitSessionService visitSessionService;

    @BeforeEach
    void setUp() {
        visitSessionService = new VisitSessionService(visitRepository, placeRepository, userRepository, idempotencyStore, objectMapper);
    }

    @Test
    void startSessionCreatesNewVisit() {
        UUID userId = UUID.randomUUID();
        VisitSessionStartRequest request = new VisitSessionStartRequest();
        request.setUserId(userId);
        request.setPlaceId(10L);
        request.setClientMode(ClientMode.NAVIGATION);

        User user = new User("user@test.com", "tester", "password", AuthProvider.LOCAL);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Place place = org.mockito.Mockito.mock(Place.class);
        when(placeRepository.findById(10L)).thenReturn(Optional.of(place));

        when(visitRepository.existsByUser_IdAndState(userId, VisitState.ACTIVE)).thenReturn(false);
        when(visitRepository.save(any(Visit.class))).thenAnswer(new Answer<Visit>() {
            @Override
            public Visit answer(InvocationOnMock invocation) {
                Visit visit = invocation.getArgument(0);
                setVisitId(visit, 100L);
                return visit;
            }
        });

        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitSessionStartResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<VisitSessionStartResponse>>() {
                    @Override
                    public IdempotencyResult<VisitSessionStartResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<VisitSessionStartResponse> callback = invocation.getArgument(3);
                        VisitSessionStartResponse response = callback.execute();
                        return new IdempotencyResult<>(response, false);
                    }
                });

        VisitSessionStartResponse response = visitSessionService.startSession(request, "start-key");

        assertThat(response.sessionId()).isEqualTo(100L);
        assertThat(response.state()).isEqualTo(VisitState.ACTIVE);
        assertThat(response.replayed()).isFalse();
        verify(visitRepository).save(ArgumentMatchers.any(Visit.class));
    }

    @Test
    void startSessionThrowsWhenActiveExists() {
        UUID userId = UUID.randomUUID();
        VisitSessionStartRequest request = new VisitSessionStartRequest();
        request.setUserId(userId);
        request.setPlaceId(20L);

        User user = new User("user2@test.com", "tester", "password", AuthProvider.LOCAL);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        Place place = org.mockito.Mockito.mock(Place.class);
        when(placeRepository.findById(20L)).thenReturn(Optional.of(place));
        when(visitRepository.existsByUser_IdAndState(userId, VisitState.ACTIVE)).thenReturn(true);

        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitSessionStartResponse.class), any()))
                .thenAnswer(new Answer<IdempotencyResult<VisitSessionStartResponse>>() {
                    @Override
                    public IdempotencyResult<VisitSessionStartResponse> answer(InvocationOnMock invocation) {
                        IdempotencyCallback<VisitSessionStartResponse> callback = invocation.getArgument(3);
                        callback.execute();
                        return null;
                    }
                });

        boolean thrown = false;
        try {
            visitSessionService.startSession(request, "dup-key");
        } catch (SessionException expected) {
            thrown = true;
            assertThat(expected.getErrorCode()).isEqualTo(ErrorCode.SESSION_ALREADY_EXISTS);
        }
        assertThat(thrown).isTrue();
    }

    @Test
    void startSessionReplayedResponseMarksFlag() {
        UUID userId = UUID.randomUUID();
        VisitSessionStartRequest request = new VisitSessionStartRequest();
        request.setUserId(userId);
        request.setPlaceId(30L);

        OffsetDateTime startedAt = OffsetDateTime.now();
        VisitSessionStartResponse cached = new VisitSessionStartResponse(5L, VisitState.ACTIVE, startedAt, startedAt.plusMinutes(30L), false);

        when(idempotencyStore.replayOrRun(anyString(), anyString(), eq(VisitSessionStartResponse.class), any()))
                .thenReturn(new IdempotencyResult<>(cached, true));

        VisitSessionStartResponse response = visitSessionService.startSession(request, "replay-key");

        assertThat(response.sessionId()).isEqualTo(5L);
        assertThat(response.replayed()).isTrue();
        verify(visitRepository, never()).save(any(Visit.class));
    }

    private void setVisitId(Visit visit, Long id) {
        try {
            Field field = Visit.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(visit, id);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new IllegalStateException("visit id 설정에 실패했습니다.", ex);
        }
    }
}
