package com.balmerlawrie.balmerrestservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for authenticating users via the iBPS WMConnect API.
 */
@Service
public class WMConnectService extends BaseIbpsService {

    @Value("${ibps.wmconnect.url}")
    private String wmconnectUrlTemplate;

    /**
     * Retrieves user details from the database.
     *
     * @param userName The username to look up
     * @return Map containing user details, or empty map if not found
     */
    public Map<String, Object> getUserDetails(String userName) {
        log.debug("Getting user details for: {}", userName);

        try {
            var jdbcCall = new org.springframework.jdbc.core.simple.SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("ng_eoff_Services");

            Map<String, Object> inParams = new HashMap<>();
            inParams.put("query_type", "UserDetails");
            inParams.put("Processname", "");
            inParams.put("approvalstatus", "");
            inParams.put("processid", "");
            inParams.put("username", userName);

            Map<String, Object> result = jdbcCall.execute(inParams);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");

            if (resultSet != null && !resultSet.isEmpty()) {
                log.debug("User details found for: {}", userName);
                return resultSet.get(0);
            } else {
                log.warn("No user details found for: {}", userName);
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            log.error("Error retrieving user details for {}: {}", userName, e.getMessage(), e);
            return Collections.emptyMap();
        }
    }

    /**
     * Calls the iBPS WMConnect API to authenticate a user.
     *
     * @param userName The username for authentication
     * @param password The password for authentication
     * @return JsonNode containing the API response
     */
    public JsonNode callWMConnectApi(String userName, String password) {
        log.info("Attempting WMConnect authentication for user: {}", userName);

        String engineName = getEngineName();

        if (engineName == null) {
            log.error("Engine name is null, cannot proceed with authentication");
            return createErrorResponse("Failed to retrieve engine name");
        }

        String url = String.format(wmconnectUrlTemplate, engineName, userName);
        log.debug("WMConnect URL: {}", url);

        // Build XML payload - using ibpsServerHost from config (not hardcoded)
        String xmlPayload = buildWMConnectPayload(engineName, userName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_XML));
        headers.set("password", password);

        HttpEntity<String> request = new HttpEntity<>(xmlPayload, headers);

        try {
            log.debug("Sending WMConnect request");
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            JsonNode result = parseXmlToJson(response.getBody());

            // Check for success
            String mainCode = result.path("Exception").path("MainCode").asText("1");
            if ("0".equals(mainCode)) {
                log.info("Authentication successful for user: {}", userName);
            } else {
                log.warn("Authentication failed for user: {} with code: {}", userName, mainCode);
            }

            return result;

        } catch (HttpClientErrorException e) {
            log.error("HTTP client error during authentication: {} - {}", e.getStatusCode(), e.getMessage());
            return createErrorResponse("Authentication failed",
                    "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            log.error("HTTP server error during authentication: {} - {}", e.getStatusCode(), e.getMessage());
            return createErrorResponse("Server error during authentication",
                    "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.error("Connection error during authentication: {}", e.getMessage());
            return createErrorResponse("Connection error", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during authentication: {}", e.getMessage(), e);
            return createErrorResponse("Unexpected error", e.getMessage());
        }
    }

    /**
     * Builds the XML payload for WMConnect API request.
     * Uses configured server host instead of hardcoded IP.
     *
     * @param engineName The engine name
     * @param userName   The username
     * @return XML string for the request body
     */
    private String buildWMConnectPayload(String engineName, String userName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<WMConnect_Input>\n" +
                "    <Option>WMConnect</Option>\n" +
                "    <engineName>" + engineName + "</engineName>\n" +
                "    <ApplicationInfo>" + ibpsServerHost + "</ApplicationInfo>\n" + // Using config value
                "    <Participant>\n" +
                "        <userName>" + userName + "</userName>\n" +
                "        <Scope></Scope>\n" +
                "        <ParticipantType>U</ParticipantType>\n" +
                "        <UserExist>N</UserExist>\n" +
                "    </Participant>\n" +
                "</WMConnect_Input>";
    }
}
