package com.balmerlawrie.balmerrestservice.service;

import com.balmerlawrie.balmerrestservice.model.CachedSession;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.balmerlawrie.balmerrestservice.util.EncryptionUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages iBPS session caching to avoid calling WMConnect for every request.
 * 
 * Sessions are cached per user and reused until they expire.
 * Default timeout is 25 minutes (iBPS default is 30 min, with 5 min safety
 * buffer).
 */
@Service
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    @Autowired
    private WMConnectService wmConnectService;

    @Value("${session.cache.timeout.minutes:25}")
    private long sessionTimeoutMinutes;

    @Value("${service.account.username:}")
    private String serviceAccountUsername;

    @Value("${service.account.password:}")
    private String serviceAccountPassword;

    /**
     * Cache of sessions keyed by username.
     */
    private final Map<String, CachedSession> sessionCache = new ConcurrentHashMap<>();

    /**
     * Gets a session using the service account credentials from config.
     * This is used for endpoints that don't require user-specific authentication.
     *
     * @return The session ID, or null if authentication fails
     */
    public Long getServiceSession() {
        if (serviceAccountUsername == null || serviceAccountUsername.isEmpty() ||
                serviceAccountPassword == null || serviceAccountPassword.isEmpty()) {
            log.error("Service account credentials not configured in application.properties");
            return null;
        }

        // Decrypt the password before using it
        String decryptedPassword = EncryptionUtil.decrypt(serviceAccountPassword);
        return getSession(serviceAccountUsername, decryptedPassword);
    }

    /**
     * Gets a fresh session for the service account, bypassing cache.
     *
     * @return The new session ID, or null if authentication fails
     */
    public Long getFreshServiceSession() {
        if (serviceAccountUsername == null || serviceAccountUsername.isEmpty() ||
                serviceAccountPassword == null || serviceAccountPassword.isEmpty()) {
            log.error("Service account credentials not configured in application.properties");
            return null;
        }

        // Decrypt the password before using it
        String decryptedPassword = EncryptionUtil.decrypt(serviceAccountPassword);

        // Invalidate any cached session for the service account before creating a new one
        sessionCache.remove(serviceAccountUsername);
        return createNewSession(serviceAccountUsername, decryptedPassword);
    }

    /**
     * Gets a valid session for the given user.
     * 
     * If a valid cached session exists, it is returned.
     * Otherwise, a new session is created via WMConnect.
     *
     * @param userName The username
     * @param password The password (only used if new session is needed)
     * @return The session ID, or null if authentication fails
     */
    public Long getSession(String userName, String password) {
        // Check for cached session
        CachedSession cached = sessionCache.get(userName);

        if (cached != null && cached.isValid()) {
            log.info("Reusing cached session for user: {} (remaining: {} min)",
                    userName, cached.getRemainingMinutes());
            return cached.getSessionId();
        }

        // Clear expired session
        if (cached != null) {
            log.info("Session expired for user: {}, creating new session", userName);
            sessionCache.remove(userName);
        }

        // Create new session
        return createNewSession(userName, password);
    }

    /**
     * Creates a new session via WMConnect and caches it.
     *
     * @param userName The username
     * @param password The password
     * @return The new session ID, or null if authentication fails
     */
    private Long createNewSession(String userName, String password) {
        log.info("Creating new session for user: {}", userName);

        JsonNode response = wmConnectService.callWMConnectApi(userName, password);

        // Check for success
        String mainCode = response.path("Exception").path("MainCode").asText("1");
        if (!"0".equals(mainCode)) {
            log.error("Authentication failed for user: {}, code: {}", userName, mainCode);
            return null;
        }

        // Extract session ID
        long sessionId = response.path("Participant").path("SessionId").asLong(0);
        if (sessionId == 0) {
            log.error("No session ID in response for user: {}", userName);
            return null;
        }

        // Cache the session
        CachedSession newSession = new CachedSession(sessionId, userName, sessionTimeoutMinutes);
        sessionCache.put(userName, newSession);

        log.info("New session created for user: {}, sessionId: {}, expires in: {} min",
                userName, sessionId, sessionTimeoutMinutes);

        return sessionId;
    }

    /**
     * Invalidates a cached session for the given user.
     * Use this when you receive WM_INVALID_SESSION_HANDLE error.
     *
     * @param userName The username whose session should be invalidated
     */
    public void invalidateSession(String userName) {
        CachedSession removed = sessionCache.remove(userName);
        if (removed != null) {
            log.info("Invalidated session for user: {}", userName);
        }
    }

    /**
     * Invalidates a cached session by session ID.
     * Use this when you receive WM_INVALID_SESSION_HANDLE error.
     *
     * @param sessionId The session ID to invalidate
     */
    public void invalidateSessionById(long sessionId) {
        sessionCache.entrySet().removeIf(entry -> {
            if (entry.getValue().getSessionId() == sessionId) {
                log.info("Invalidated session {} for user: {}", sessionId, entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Gets the cached session for a user without creating a new one.
     *
     * @param userName The username
     * @return The cached session, or null if not found or expired
     */
    public CachedSession getCachedSession(String userName) {
        CachedSession cached = sessionCache.get(userName);
        if (cached != null && cached.isValid()) {
            return cached;
        }
        return null;
    }

    /**
     * Clears all cached sessions.
     */
    public void clearAllSessions() {
        int count = sessionCache.size();
        sessionCache.clear();
        log.info("Cleared {} cached sessions", count);
    }

    /**
     * Gets the configured session timeout in minutes.
     *
     * @return The timeout in minutes
     */
    public long getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    /**
     * Gets the number of currently cached sessions.
     *
     * @return The cache size
     */
    public int getCacheSize() {
        return sessionCache.size();
    }
}
