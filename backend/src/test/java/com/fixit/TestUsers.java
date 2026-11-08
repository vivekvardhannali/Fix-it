package com.fixit;

import java.util.Locale;
import java.util.UUID;

import com.fixit.entity.User;

/** Unsaved users for tests. Every account needs a username and a password hash; these tests never log in with them. */
final class TestUsers {

    /** Not a real BCrypt hash, so no password can ever match it. */
    static final String NO_PASSWORD_HASH = "test-user-without-a-usable-password";

    private TestUsers() {
    }

    /**
     * A user with the given smail. The username is the smail's local part plus a random suffix, because the tests share
     * the development database and must not clash with real accounts such as "alice" (usernames are unique).
     */
    static User user(String smail) {
        String local = smail.substring(0, smail.indexOf('@')).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = (local.length() > 21 ? local.substring(0, 21) : local) + "_" + suffix;   // at most 30 chars
        return new User(smail, username, NO_PASSWORD_HASH);
    }
}
