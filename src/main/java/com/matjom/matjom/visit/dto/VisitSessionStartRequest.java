package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.ClientMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class VisitSessionStartRequest {

    @NotNull(message = "userId는 필수입니다.")
    private UUID userId;

    @NotNull(message = "placeId는 필수입니다.")
    private Long placeId;

    @Size(max = 50, message = "clientNote는 50자 이하여야 합니다.")
    private String clientNote;

    private ClientMode clientMode;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public void setPlaceId(Long placeId) {
        this.placeId = placeId;
    }

    public String getClientNote() {
        return clientNote;
    }

    public void setClientNote(String clientNote) {
        this.clientNote = clientNote;
    }

    public ClientMode getClientMode() {
        return clientMode;
    }

    public void setClientMode(ClientMode clientMode) {
        this.clientMode = clientMode;
    }

    public ClientMode clientModeOrDefault() {
        if (clientMode == null) {
            return ClientMode.NAVIGATION;
        }
        return clientMode;
    }
}
