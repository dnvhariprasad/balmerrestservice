package com.balmerlawrie.balmerrestservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Service for fetching work item attributes from iBPS.
 */
@Service
public class WorkItemAttributesService extends BaseIbpsService {

    @Value("${ibps.fetchAttributes.url}")
    private String fetchAttributesUrlTemplate;

    /**
     * Fetches work item attributes from iBPS using WMFetchWorkItemAttributes.
     */
    public JsonNode fetchWorkItemAttributes(String processInstanceId, String workitemId, long sessionId) {
        try {
            String engineName = getEngineName();
            int workItemIdInt = Integer.parseInt(workitemId);
            String url = String.format(fetchAttributesUrlTemplate, engineName, processInstanceId, workItemIdInt);

            String xmlPayload = buildFetchAttributesPayload(engineName, processInstanceId, workItemIdInt, sessionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_XML));
            headers.set("sessionId", String.valueOf(sessionId));

            HttpEntity<String> request = new HttpEntity<>(xmlPayload, headers);

            log.debug("Calling WMFetchWorkItemAttributes: url={}, sessionId={}", url, sessionId);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            JsonNode result = parseXmlToJson(response.getBody());

            JsonNode output = result.path("WMFetchWorkItemAttributes_Output");
            if (!output.isMissingNode()) {
                int status = output.path("Status").asInt(0);
                if (status != 0) {
                    String errorDesc = output.path("Error").path("Exception").path("Description")
                            .asText("Unknown error");
                    log.error("iBPS API error: Status={}, Description={}", status, errorDesc);
                    return createErrorResponse("iBPS error: " + errorDesc);
                }
            }

            log.debug("WMFetchWorkItemAttributes response OK");
            return result;

        } catch (org.springframework.web.client.HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("iBPS returned HTTP 500, parsing error body: {}",
                    responseBody.substring(0, Math.min(300, responseBody.length())));
            try {
                JsonNode errorJson;
                if (responseBody.trim().startsWith("[")) {
                    errorJson = jsonMapper.readTree(responseBody).get(0);
                } else if (responseBody.trim().startsWith("{")) {
                    errorJson = jsonMapper.readTree(responseBody);
                } else {
                    errorJson = parseXmlToJson(responseBody);
                }

                JsonNode output = errorJson.path("WMFetchWorkItemAttributes_Output");
                if (output.isMissingNode()) {
                    output = errorJson;
                }

                int status = output.path("Status").asInt(-1);
                JsonNode errorNode = output.path("Error").path("Exception");
                String errorDesc = errorNode.path("Description").asText("");
                if (errorDesc.isEmpty()) {
                    errorDesc = errorNode.path("Subject").asText("Unknown iBPS error");
                }

                log.error("iBPS API error: Status={}, Description={}", status, errorDesc);
                return createErrorResponse("iBPS error (Status " + status + "): " + errorDesc);
            } catch (Exception parseEx) {
                log.error("Failed to parse iBPS error response: {}", parseEx.getMessage());
                return createErrorResponse("Error fetching work item: " + e.getStatusCode());
            }
        } catch (NumberFormatException e) {
            return createErrorResponse("Invalid workitemId. Expected numeric value.");
        } catch (Exception e) {
            log.error("Error fetching work item attributes: {}", e.getMessage(), e);
            return createErrorResponse("Error fetching attributes", e.getMessage());
        }
    }

    private String buildFetchAttributesPayload(String engineName, String processInstanceId, int workitemId,
            long sessionId) {
        return String.format(
                "<?xml version=\"1.0\"?>" +
                        "<WMFetchWorkItemAttributes_Input>" +
                        "<Option>WMFetchWorkItemAttributes</Option>" +
                        "<EngineName>%s</EngineName>" +
                        "<SessionId>%d</SessionId>" +
                        "<ProcessInstanceId>%s</ProcessInstanceId>" +
                        "<WorkItemId>%d</WorkItemId>" +
                        "</WMFetchWorkItemAttributes_Input>",
                engineName, sessionId, processInstanceId, workitemId);
    }
}
