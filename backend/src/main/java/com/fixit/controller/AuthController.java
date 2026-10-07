package com.fixit.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fixit.dto.AuthTokenResponse;
import com.fixit.dto.LoginRequest;
import com.fixit.dto.RegisterRequest;
import com.fixit.entity.User;
import com.fixit.security.PasswordFixItPrincipal;
import com.fixit.security.RateLimiter;
import com.fixit.security.TabSessionService;
import com.fixit.service.AccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Username/password sign-up and login. Both are public; both start a normal session (cookie) on success. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accounts;
    private final RateLimiter rateLimiter;
    private final TabSessionService tabSessions;     // TESTING-CONVENIENCE (per-tab sessions)
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AccountService accounts, RateLimiter rateLimiter, TabSessionService tabSessions) {
        this.accounts = accounts;
        this.rateLimiter = rateLimiter;
        this.tabSessions = tabSessions;
    }

    /** Creates the account and signs the new user in. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http,
            HttpServletResponse response) {
        // per address, so creating many accounts cannot get around the per-user limits (also slows probing for taken names)
        rateLimiter.check(RateLimiter.REGISTER, http.getRemoteAddr());
        User user = accounts.register(request.username(), request.smail(), request.password());
        startSession(user, http, response);
        return toResponse(user);
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {
        User user = accounts.authenticate(request.identifier(), request.password(), http.getRemoteAddr());
        startSession(user, http, response);
        return toResponse(user);
    }

    private void startSession(User user, HttpServletRequest request, HttpServletResponse response) {
        var principal = new PasswordFixItPrincipal(user.getId(), user.getUsername() != null ? user.getUsername() : user.getSmail());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        if (request.getSession(false) != null) {
            request.changeSessionId();                      // a new session id on login prevents session fixation
        }
        contextRepository.saveContext(context, request, response);   // explicit save: stores it in the session
        SecurityContextHolder.setContext(context);
    }

    /** The cookie session is always started; when per-tab sessions are on, a token for this tab is handed out as well. */
    private AuthTokenResponse toResponse(User user) {
        String name = user.getUsername() != null ? user.getUsername() : user.getSmail();
        return new AuthTokenResponse(user.getId(), user.getUsername(), user.getSmail(), tabSessions.issue(user.getId(), name));
    }
}
