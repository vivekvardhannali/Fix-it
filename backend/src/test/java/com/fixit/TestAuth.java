package com.fixit;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.fixit.entity.User;
import com.fixit.security.PasswordFixItPrincipal;

/** Builds the Authentication a successful login would produce, for tests that skip the login step itself. */
final class TestAuth {

    private TestAuth() {
    }

    /** A logged-in caller with the given id, for tests that need no database row. */
    static Authentication withId(long userId, String name) {
        var principal = new PasswordFixItPrincipal(userId, name);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    static Authentication as(User user) {
        var principal = new PasswordFixItPrincipal(user.getId(), user.getUsername() != null ? user.getUsername() : user.getSmail());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
