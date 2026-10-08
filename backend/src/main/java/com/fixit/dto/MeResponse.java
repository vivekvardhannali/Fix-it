package com.fixit.dto;

/** TESTING-CONVENIENCE (D23) for the perTabSessions field. The logged-in user. {@code perTabSessions} says whether the testing convenience (D23) is on, so the website can adapt. */
public record MeResponse(Long userId, String username, String smail, boolean perTabSessions) {
}
