package com.fixit.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * The logged-in user after username/password login. Kept in the HTTP session, so it holds NO password or hash - only the
 * id and display names.
 */
public class PasswordFixItPrincipal implements FixItPrincipal, AuthenticatedPrincipal, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String name;

    public PasswordFixItPrincipal(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    /** username if the account has one, else the smail. */
    @Override
    public String getName() {
        return name;
    }

    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
