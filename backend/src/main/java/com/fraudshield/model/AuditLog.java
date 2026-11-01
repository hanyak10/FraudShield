package com.fraudshield.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, length = 500)
    private String action;

    @Column
    private String ip;

    @Column
    private String icon;

    @Column(nullable = false)
    private boolean seeded;

    @Column(nullable = false)
    private Instant createdAt;

    public AuditLog() {
    }

    public AuditLog(String actor, String action, String ip, String icon, Instant createdAt) {
        this.actor = actor;
        this.action = action;
        this.ip = ip;
        this.icon = icon;
        this.createdAt = createdAt;
    }

    public AuditLog(String actor, String action, String ip, String icon, Instant createdAt, boolean seeded) {
        this.actor = actor;
        this.action = action;
        this.ip = ip;
        this.icon = icon;
        this.createdAt = createdAt;
        this.seeded = seeded;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public boolean isSeeded() {
        return seeded;
    }

    public void setSeeded(boolean seeded) {
        this.seeded = seeded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
