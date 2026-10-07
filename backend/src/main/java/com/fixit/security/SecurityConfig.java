package com.fixit.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fixit.exception.ApiError;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, FixItOidcUserService oidcUserService, ObjectMapper mapper,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.auth.google-enabled:false}") boolean googleEnabled, TabSessionService tabSessions) throws Exception {
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
                    if (googleEnabled) {
                        auth.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();
                    }
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

        // Google sign-in is kept in the code but OFF by default (app.auth.google-enabled / GOOGLE_AUTH_ENABLED).
        if (googleEnabled) {
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(info -> info.oidcUserService(oidcUserService))
                    .defaultSuccessUrl(frontendUrl, true)
                    .failureHandler(loginFailureHandler(frontendUrl)));
        }
        return http.build();
    }

    /** Sends the browser back to the frontend login page with a short error code. */
    public static AuthenticationFailureHandler loginFailureHandler(String frontendUrl) {
        return (request, response, exception) -> {
            String code = exception instanceof OAuth2AuthenticationException oauth
                    ? oauth.getError().getErrorCode() : "authentication_failed";
            response.sendRedirect(frontendUrl + "/login?error=" + URLEncoder.encode(code, StandardCharsets.UTF_8));
        };
    }
}
