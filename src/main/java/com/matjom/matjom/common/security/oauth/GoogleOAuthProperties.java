package com.matjom.matjom.common.security.oauth;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Holds Google OAuth related configuration (client IDs, token info endpoint).
 */
@Getter
@Component
public class GoogleOAuthProperties {

    private final List<String> allowedClientIds;
    private final String tokenInfoEndpoint;

    public GoogleOAuthProperties(
            @Value("${oauth.google.client-ids:${GOOGLE_CLIENT_IDS:}}") String clientIds,
            @Value("${oauth.google.token-info-endpoint:https://oauth2.googleapis.com/tokeninfo}") String tokenInfoEndpoint
    ) {
        if (StringUtils.hasText(clientIds)) {
            this.allowedClientIds = Arrays.stream(clientIds.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        } else {
            this.allowedClientIds = Collections.emptyList();
        }
        this.tokenInfoEndpoint = tokenInfoEndpoint;
    }

    public boolean hasClientIds() {
        return !allowedClientIds.isEmpty();
    }
}
