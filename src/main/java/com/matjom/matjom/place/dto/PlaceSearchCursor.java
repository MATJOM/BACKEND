package com.matjom.matjom.place.dto;

import com.matjom.matjom.common.exception.base.SearchException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public record PlaceSearchCursor(double distanceMeters, long lastPlaceId) {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?:[0-9]+$");
    private static final Locale FORMAT_LOCALE = Locale.US;
    private static final String TOKEN_FORMAT = "%.5f:%d";

    public static PlaceSearchCursor from(String rawCursor) {
        if (!StringUtils.hasText(rawCursor)) {
            return null;
        }
        String token = rawCursor.trim();
        if (!TOKEN_PATTERN.matcher(token).matches()) {
            throw new SearchException(ErrorCode.INVALID_REQUEST_PARAM, "cursor 형식이 올바르지 않습니다.");
        }
        String[] parts = token.split(":", 2);
        try {
            double distance = Double.parseDouble(parts[0]);
            long lastId = Long.parseLong(parts[1]);
            return new PlaceSearchCursor(distance, lastId);
        } catch (NumberFormatException ex) {
            throw new SearchException(ErrorCode.INVALID_REQUEST_PARAM, "cursor 형식이 올바르지 않습니다.");
        }
    }

    public static String toToken(double distanceMeters, long lastPlaceId) {
        return String.format(FORMAT_LOCALE, TOKEN_FORMAT, distanceMeters, lastPlaceId);
    }

    public String toToken() {
        return toToken(distanceMeters, lastPlaceId);
    }
}
