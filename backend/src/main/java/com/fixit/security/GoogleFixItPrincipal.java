package com.fixit.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

/** The logged-in Google identity (only used when Google sign-in is enabled), carrying the Fix It user id. */
public class GoogleFixItPrincipal extends DefaultOidcUser implements FixItPrincipal {

    private final Long userId;

    public GoogleFixItPrincipal(Collection<? extends GrantedAuthority> authorities, OidcIdToken idToken,
            OidcUserInfo userInfo, Long userId) {
        super(authorities, idToken, userInfo);
        this.userId = userId;
    }

    @Override
    public Long getUserId() {
        return userId;
    }
}
