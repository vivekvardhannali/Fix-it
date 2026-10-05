package com.fixit.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String smail;

    /** Login name for username/password accounts; null for accounts created through Google sign-in. */
    @Column(length = 30)
    private String username;

    /** BCrypt hash; null for accounts without a password. Never returned by any API. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected User() {
    }

    public User(String smail) {
        this.smail = smail;
    }

    public User(String smail, String username, String passwordHash) {
        this.smail = smail;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getSmail() { return smail; }
    public String getUsername() { return username; }

    /**
     * The name shown next to content: the username, or {@code user<id>} for accounts that have none (e.g. created through
     * Google sign-in). The smail is never used as a display name.
     */
    public String getDisplayName() {
        return username != null ? username : "user" + id;
    }
    public String getPasswordHash() { return passwordHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
