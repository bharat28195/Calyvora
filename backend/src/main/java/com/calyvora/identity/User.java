package com.calyvora.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A person within exactly one company (SD-3: email is globally unique). Kept anemic — business
 * rules live in services (docs/Sprint1 D1) to avoid Hibernate footguns.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Role role;

    /**
     * The company role this person holds (PD-54), when it is not simply the built-in their {@link #role}
     * names. Null means "the built-in for my role", which is what everyone had before custom roles.
     */
    @Column(name = "company_role_id")
    private java.util.UUID companyRoleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private UserStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** Set when somebody else chose this password (a company's first admin, V67); cleared when they change it. */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** The language the app is shown in; null follows the company (V68). */
    @Column(length = 8)
    private String language;

    /** This person's own clock when they have no employee record to hold one; null follows the company. */
    @Column(length = 64)
    private String timezone;

    /** DMY, MDY or YMD; null writes dates the way the language does. */
    @Column(name = "date_format", length = 8)
    private String dateFormat;

    /** H12 or H24; null follows the language. */
    @Column(name = "time_format", length = 8)
    private String timeFormat;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(UUID id, UUID companyId, String email, String firstName, String lastName,
                Role role, UserStatus status) {
        this.id = id;
        this.companyId = companyId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public java.util.UUID getCompanyRoleId() {
        return companyRoleId;
    }

    public void setCompanyRoleId(java.util.UUID companyRoleId) {
        this.companyRoleId = companyRoleId;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public String getLanguage() {
        return language;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public void setPreferences(String language, String timezone, String dateFormat, String timeFormat) {
        this.language = language;
        this.timezone = timezone;
        this.dateFormat = dateFormat;
        this.timeFormat = timeFormat;
    }

    public void setEmailVerifiedAt(Instant emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}
