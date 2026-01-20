package com.balmerlawrie.balmerrestservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base service class for iBPS integration.
 * Provides common functionality shared across all iBPS services:
 * - Engine name retrieval
 * - RestTemplate access
 * - Common error handling
 * - Logging
 */
@Service
public class BaseIbpsService {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RestTemplate restTemplate;

    @Value("${ibps.server.host}")
    protected String ibpsServerHost;

    @Value("${ibps.cabinet.name}")
    protected String cabinetName;

    protected final ObjectMapper jsonMapper = new ObjectMapper();
    protected final XmlMapper xmlMapper = new XmlMapper();

    /**
     * Retrieves the engine name (cabinet name) from the database.
     * This method is shared by all services that need to call iBPS APIs.
     *
     * @return The engine name for the iBPS system, or null if not found
     */
    public String getEngineName() {
        log.debug("Retrieving engine name from database");

        try {
            SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                    .withProcedureName("ng_eoff_Services");

            Map<String, Object> inParams = new HashMap<>();
            inParams.put("query_type", "cabinetName");
            inParams.put("Processname", "");
            inParams.put("approvalstatus", "");
            inParams.put("processid", "");
            inParams.put("username", "");

            Map<String, Object> result = jdbcCall.execute(inParams);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");

            if (resultSet != null && !resultSet.isEmpty() && resultSet.get(0).get("cabinetname") != null) {
                String engineName = resultSet.get(0).get("cabinetname").toString();
                log.debug("Engine name retrieved: {}", engineName);
                return engineName;
            } else {
                log.warn("No engine name found in database, using configured cabinet name: {}", cabinetName);
                return cabinetName;
            }
        } catch (Exception e) {
            log.error("Error retrieving engine name from database: {}", e.getMessage(), e);
            return cabinetName; // Fallback to configured value
        }
    }

    /**
     * Creates a standardized error response JSON object.
     *
     * @param message The error message
     * @return JsonNode containing the error details
     */
    protected JsonNode createErrorResponse(String message) {
        log.error("Creating error response: {}", message);
        ObjectNode errorNode = jsonMapper.createObjectNode();
        errorNode.put("success", false);
        errorNode.put("error", message);
        return errorNode;
    }

    /**
     * Creates a standardized error response with additional details.
     *
     * @param message The error message
     * @param details Additional error details
     * @return JsonNode containing the error details
     */
    protected JsonNode createErrorResponse(String message, String details) {
        log.error("Creating error response: {} - {}", message, details);
        ObjectNode errorNode = jsonMapper.createObjectNode();
        errorNode.put("success", false);
        errorNode.put("error", message);
        errorNode.put("details", details);
        return errorNode;
    }

    /**
     * Parses response to JSON, handling both XML and JSON formats.
     * iBPS may return XML or JSON depending on the endpoint and error conditions.
     *
     * @param response The response string (XML or JSON)
     * @return JsonNode representation of the response
     */
    protected JsonNode parseXmlToJson(String response) {
        if (response == null || response.isEmpty()) {
            return createErrorResponse("Empty response received");
        }

        String trimmed = response.trim();

        try {
            // Check if response is JSON (starts with { or [)
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                log.debug("Detected JSON response, parsing as JSON");
                return jsonMapper.readTree(trimmed);
            }

            // Check if response is XML (starts with < or <?xml)
            if (trimmed.startsWith("<")) {
                log.debug("Detected XML response, parsing as XML");
                return xmlMapper.readTree(trimmed.getBytes());
            }

            // Unknown format
            log.warn("Unknown response format: {}", trimmed.substring(0, Math.min(100, trimmed.length())));
            return createErrorResponse("Unknown response format",
                    trimmed.substring(0, Math.min(200, trimmed.length())));

        } catch (Exception e) {
            log.error("Failed to parse response: {}", e.getMessage());
            return createErrorResponse("Failed to parse response", e.getMessage());
        }
    }
}
