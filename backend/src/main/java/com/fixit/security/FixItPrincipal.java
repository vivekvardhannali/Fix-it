package com.fixit.security;

/** Whoever is logged in, however they logged in (password or Google). Controllers only need the Fix It user id. */
public interface FixItPrincipal {

    Long getUserId();
}
