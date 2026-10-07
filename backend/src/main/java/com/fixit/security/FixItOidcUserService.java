package com.fixit.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import com.fixit.entity.User;
import com.fixit.exception.AccountNotAllowedException;
import com.fixit.service.UserProvisioningService;

/** Runs after Google authenticates the user: checks the smail rule and finds/creates the Fix It user. */
@Component
public class FixItOidcUserService extends OidcUserService {

    public static final String ERROR_CODE = "account_not_allowed";

    private final UserProvisioningService provisioning;

    public FixItOidcUserService(UserProvisioningService provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return toPrincipal(super.loadUser(userRequest));
    }

    public GoogleFixItPrincipal toPrincipal(OidcUser googleUser) {
        User user;
        try {
            user = provisioning.loginOrRegister(googleUser.getEmail(), googleUser.getEmailVerified());
        } catch (AccountNotAllowedException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ERROR_CODE, e.getMessage(), null), e);
        }
        return new GoogleFixItPrincipal(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                googleUser.getIdToken(), googleUser.getUserInfo(), user.getId());
    }
}
