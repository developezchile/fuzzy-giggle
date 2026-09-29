package org.viajeseventos.model;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/** A named bundle of {@link AppModule}s. Every user has exactly one. */
public final class Profile {

    /** Codes of the system profiles the code relies on — seeded in V1__init.sql, never deletable. */
    public static final String ADMIN = "ADMIN";
    public static final String CLIENT = "CLIENT";

    private Long id;
    private String code;
    private String name;
    private String description;
    private Set<AppModule> modules = EnumSet.noneOf(AppModule.class);
    private long userCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<AppModule> getModules() {
        return modules;
    }

    public void setModules(Set<AppModule> modules) {
        this.modules = modules;
    }

    public long getUserCount() {
        return userCount;
    }

    public void setUserCount(long userCount) {
        this.userCount = userCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** System profiles (ADMIN, CLIENT) can be renamed and — except ADMIN — have their modules edited, but never deleted. */
    public boolean isSystem() {
        return code != null;
    }

    public boolean isAdmin() {
        return ADMIN.equals(code);
    }
}
