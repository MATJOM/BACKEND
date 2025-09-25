package com.matjom.matjom.moderation.profanity;

public interface ProfanityFilter {
    boolean contains(String text);
    void validate(String text);
}
