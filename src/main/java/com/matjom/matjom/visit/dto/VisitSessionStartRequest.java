package com.matjom.matjom.visit.dto;

import com.matjom.matjom.visit.entity.ClientMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VisitSessionStartRequest {

    @NotNull(message = "userId는 필수입니다.")
    private UUID userId;

    @NotNull(message = "placeId는 필수입니다.")
    private Long placeId;

    @Size(max = 50, message = "clientNote는 50자 이하여야 합니다.")
    private String clientNote;

    private ClientMode clientMode;

    public ClientMode clientModeOrDefault() {
        if (clientMode == null) {
            return ClientMode.NAVIGATION;
        }
        return clientMode;
    }
}
