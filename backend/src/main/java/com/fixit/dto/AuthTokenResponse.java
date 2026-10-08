package com.fixit.dto;

/**
 * Login / sign-up result. {@code sessionToken} is only filled when per-tab sessions are enabled (a testing convenience, see
 * NOTES_AND_OPEN_DECISIONS.md D23); otherwise it is null and the session cookie is the only login.
 */
public record AuthTokenResponse(Long userId, String username, String smail, String sessionToken) {
}
