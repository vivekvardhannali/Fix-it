package com.fixit.service;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.User;
import com.fixit.exception.AccountNotAllowedException;
import com.fixit.repository.UserRepository;

@Service
public class UserProvisioningService {

    private final UserRepository users;
    private final String allowedDomain;

    public UserProvisioningService(UserRepository users,
            @Value("${app.auth.allowed-email-domain}") String allowedDomain) {
        this.users = users;
        this.allowedDomain = allowedDomain.toLowerCase(Locale.ROOT);
    }

    /** Validates the Google identity, then returns the existing Fix It user or creates a new one. */
    @Transactional
    public User loginOrRegister(String email, Boolean emailVerified) {
        if (email == null || email.isBlank()) {
            throw new AccountNotAllowedException("Google account has no email");
        }
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new AccountNotAllowedException("Google email is not verified");
        }
        String smail = normalizeAllowedSmail(email);
        return users.findBySmail(smail).orElseGet(() -> users.save(new User(smail)));
    }

    /** Lower-cases/trims and checks the domain. Shared by Google login and registration. */
    public String normalizeAllowedSmail(String email) {
        String smail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = smail.lastIndexOf('@');
        if (at <= 0 || !smail.substring(at + 1).equals(allowedDomain)) {
            throw new AccountNotAllowedException("Only @" + allowedDomain + " accounts can use Fix It");
        }
        return smail;
    }
}
