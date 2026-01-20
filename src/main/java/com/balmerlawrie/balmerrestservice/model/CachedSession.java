package com.balmerlawrie.balmerrestservice.model;

import java.time.Instant;

/**
 * Represents a cached iBPS session with creation timestamp.
 * Used for session reuse to avoid calling WMConnect for every request.
 */
public class CachedSession {

    private final long sessionId;
    private final String userName;
    private final Instant createdAt;
    private final long timeoutMinutes;

    public CachedSession(long sessionId, String userName, long timeoutMinutes) {
        this.sessionId = sessionId;
        this.userName = userName;
        this.createdAt = Instant.now();
        this.timeoutMinutes = timeoutMinutes;
    }

    public long getSessionId() {
        return sessionId;
    }

    public String getUserName() {
        return userName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Checks if the session has expired based on the configured timeout.
     *
     * @return true if the session is still valid, false if expired
     */
    public boolean isValid() {
        Instant expiryTime = createdAt.plusSeconds(timeoutMinutes * 60);
        return Instant.now().isBefore(expiryTime);
    }

    /**
     * Gets the remaining time in minutes before session expiry.
     *
     * @return remaining minutes, or 0 if expired
     */
    public long getRemainingMinutes() {
        Instant expiryTime = createdAt.plusSeconds(timeoutMinutes * 60);
        long remainingSeconds = expiryTime.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remainingSeconds / 60);
    }

    @Override
    public String toString() {
        return "CachedSession{" +
                "sessionId=" + sessionId +
                ", userName='" + userName + '\'' +
                ", createdAt=" + createdAt +
                ", remainingMinutes=" + getRemainingMinutes() +
                ", isValid=" + isValid() +
                '}';
    }
}
