package com.fixit.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fixit.entity.User;
import com.fixit.exception.AccountNotAllowedException;
import com.fixit.exception.BadRequestException;
import com.fixit.exception.ConflictException;
import com.fixit.exception.InvalidCredentialsException;
import com.fixit.repository.UserRepository;
import com.fixit.security.LoginThrottle;

/**
 * Username + password accounts (temporary alternative to Google sign-in).
 * Passwords are stored only as BCrypt hashes. Login failures all look the same (unknown name, wrong password, or an
 * account without a password) and cost the same time, and repeated failures are throttled.
 *
 * NOTE: the smail given at registration is NOT verified (no email is sent), so anyone can register any
 * @smail.iitm.ac.in address that is not yet taken. Acceptable for development/testing; Google sign-in is the intended
 * way to prove the smail belongs to the person.
 */
@Service
public class AccountService {

    static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_.-]{2,29}$");
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_BYTES = 72;                      // BCrypt ignores anything beyond 72 bytes
    private static final Set<String> COMMON_PASSWORDS = Set.of("password", "password1", "password123", "12345678",
            "123456789", "1234567890", "qwertyui", "qwerty123", "iloveyou", "admin123", "letmein1", "welcome1",
            "abcd1234", "11111111", "00000000");

    private final UserRepository users;
    private final UserProvisioningService provisioning;
    private final PasswordEncoder encoder;
    private final LoginThrottle throttle;
    private final String dummyHash;                                // compared against when the account does not exist

    public AccountService(UserRepository users, UserProvisioningService provisioning, PasswordEncoder encoder,
            LoginThrottle throttle) {
        this.users = users;
        this.provisioning = provisioning;
        this.encoder = encoder;
        this.throttle = throttle;
        this.dummyHash = encoder.encode("not-a-real-password-used-only-for-timing");
    }

    @Transactional
    public User register(String rawUsername, String rawSmail, String password) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new BadRequestException("Username must be 3-30 characters: letters, digits, '.', '_' or '-' "
                    + "(starting with a letter or digit)");
        }
        String smail;
        try {
            smail = provisioning.normalizeAllowedSmail(rawSmail);
        } catch (AccountNotAllowedException e) {
            throw new BadRequestException(e.getMessage());
        }
        checkPassword(password, username, smail);

        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("That username is already taken");
        }
        if (users.existsBySmail(smail)) {
            throw new ConflictException("An account with that smail already exists");
        }
        return users.save(new User(smail, username, encoder.encode(password)));
    }

    /** @param ip the caller's address, used only for throttling */
    @Transactional(readOnly = true)
    public User authenticate(String rawIdentifier, String password, String ip) {
        String identifier = rawIdentifier.trim();
        throttle.checkAllowed(identifier, ip);

        User user = users.findByUsernameIgnoreCase(identifier)
                .or(() -> users.findBySmail(identifier.toLowerCase(Locale.ROOT))).orElse(null);
        boolean ok;
        if (user == null || user.getPasswordHash() == null) {
            encoder.matches(password, dummyHash);                  // same work as a real check
            ok = false;
        } else {
            ok = encoder.matches(password, user.getPasswordHash());
        }
        if (!ok) {
            throttle.recordFailure(identifier, ip);
            throw new InvalidCredentialsException("Invalid username or password");
        }
        throttle.recordSuccess(identifier);
        return user;
    }

    private static void checkPassword(String password, String username, String smail) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("Password is too long (at most " + MAX_PASSWORD_BYTES + " bytes)");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        String smailName = smail.substring(0, smail.indexOf('@'));
        if (COMMON_PASSWORDS.contains(lower) || lower.equals(username.toLowerCase(Locale.ROOT)) || lower.equals(smailName)
                || lower.equals(smail)) {
            throw new BadRequestException("That password is too easy to guess; choose another");
        }
    }
}
