package com.fixit.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.exception.ApiError;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper mapper, TabSessionService tabSessions) throws Exception {
        http
                // TESTING-CONVENIENCE (per-tab sessions, D23): a Bearer token identifies the caller and wins over the shared cookie
                .addFilterAfter(new TabSessionFilter(tabSessions), SecurityContextHolderFilter.class)
                // Auth is a session cookie (SameSite=Lax, HttpOnly) with JSON-only APIs, so cross-site
                // form posts can't carry the session. Revisit if the frontend is hosted on another site.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> {
                    // only the bare liveness check is public; /api/health/* reveal configuration state, so they need a login
                    auth.requestMatchers("/api/ping", "/error").permitAll();
                    // The website itself: the HTML/CSS/JS files carry no user data (all data comes from /api/**, which stays
                    // protected), and the login page must load before anyone has a session.
                    auth.requestMatchers(HttpMethod.GET, "/", "/*.html", "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll();
                    // username/password sign-up and login must be reachable without a session
                    auth.requestMatchers("/api/auth/register", "/api/auth/login").permitAll();
                    auth.anyRequest().authenticated();      // must stay last
                })
                // Defence in depth for the page code: nothing but our own files may run or load (no inline scripts, no CDN).
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                ApiError.write(res, mapper, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((req, res, e) ->
                                ApiError.write(res, mapper, HttpStatus.FORBIDDEN, "Access denied")))
                .logout(logout -> logout
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/logout"))
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .addLogoutHandler((request, response, authentication) -> tabSessions.revoke(TabSessionService.bearer(request)))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"));
        return http.build();
    }
}
