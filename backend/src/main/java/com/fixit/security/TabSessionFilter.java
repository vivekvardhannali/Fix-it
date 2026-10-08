package com.fixit.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * TESTING-CONVENIENCE (per-tab sessions) - see {@link TabSessionService}.
 * If a request carries "Authorization: Bearer ...", that token decides who the caller is - and it ALWAYS wins over the shared
 * session cookie: a valid token logs the request in as its owner, an unknown/expired one means "not logged in" (so a stale tab
 * can never silently act as whoever last logged in through the cookie). Requests without the header use the cookie as before.
 * Created in SecurityConfig (not a @Component, so it is not also registered as a plain servlet filter).
 */
public class TabSessionFilter extends OncePerRequestFilter {

    private final TabSessionService tabSessions;

    public TabSessionFilter(TabSessionService tabSessions) {
        this.tabSessions = tabSessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = tabSessions.enabled() ? TabSessionService.bearer(request) : null;
        if (token != null) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            tabSessions.resolve(token).ifPresent(who -> {
                var principal = new PasswordFixItPrincipal(who.userId(), who.name());
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
            });
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
