package com.fixit.security;

/** Whoever is logged in. Controllers only need the Fix It user id. */
public interface FixItPrincipal {

    Long getUserId();
}
