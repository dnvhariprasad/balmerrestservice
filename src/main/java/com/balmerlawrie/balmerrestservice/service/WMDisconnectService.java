package com.balmerlawrie.balmerrestservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Service for disconnecting users from iBPS sessions.
 */
@Service
public class WMDisconnectService extends BaseIbpsService {

    @Value("${ibps.disconnect.url}")
    private String disconnectUrlTemplate;

    /**
     * Disconnects a user session from iBPS.
     *
     * @param name      The username to disconnect
     * @param sessionId The session ID to disconnect
     * @return JSON string containing the result
     */
    public String callWMDisconnect(String name, long sessionId) {
        log.info("Disconnecting user: {} with session: {}", name, sessionId);

        String engineName = getEngineName();
        if (engineName == null) {
            return createErrorResponse("Failed to retrieve engine name").toPrettyString();
        }

        String url = String.format(disconnectUrlTemplate, engineName, name);
        log.debug("Disconnect URL: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("sessionId", String.valueOf(sessionId));
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            JsonNode node = parseXmlToJson(response.getBody());
            log.info("Successfully disconnected user: {}", name);
            return node.toPrettyString();

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            log.error("API error during disconnect: {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            try {
                JsonNode errorNode = parseXmlToJson(ex.getResponseBodyAsString());
                return errorNode.toPrettyString();
            } catch (Exception parseEx) {
                return "API error: " + ex.getStatusCode() + " - " + ex.getResponseBodyAsString();
            }
        } catch (Exception ex) {
            log.error("Unexpected error during disconnect: {}", ex.getMessage(), ex);
            return "Unexpected error: " + ex.getMessage();
        }
    }
}
