package com.balmerlawrie.balmerrestservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for notesheet document operations.
 * Handles retrieval of original notesheet from work item attachments.
 */
@Service
public class NoteSheetService extends BaseIbpsService {

    @Value("${ibps.fetchAttributes.url}")
    private String fetchAttributesUrlTemplate;

    @Value("${omnidocs.api.url}")
    private String omniDocsApiUrl;

    @Value("${ibps.getWorkItem.url}")
    private String getWorkItemUrlTemplate;

    @Value("${omnigetdocument.api.url}")
    private String getDocumentUrl;

    @Value("${notesheet.document.class:Notesheet Original}")
    private String noteSheetDocumentClass;

    @Value("${notesheet.temp.directory:${java.io.tmpdir}/notesheets}")
    private String tempDirectory;

    @Value("${docs.viewer.base.url:}")
    private String docsViewerBaseUrl;

    /**
     * Main method to retrieve the original notesheet document.
     * Uses the notesheet_original work item attribute which contains:
     * FolderIndex#VersionNo#DocumentIndex
     * 
     * @param processInstanceId Process instance ID
     * @param workitemId        Work item ID
     * @param sessionId         Session ID for authentication
     * @return JSON response with file path or not found status
     */
    public JsonNode getOriginalNotesheet(String processInstanceId, String workitemId, long sessionId) {
        log.info("Getting original notesheet for processInstanceId: {}, workitemId: {}", processInstanceId, workitemId);

        try {
            // Step 1: Get work item attributes
            JsonNode attributes = fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
            if (attributes == null || attributes.has("error")) {
                return createNotFoundResponse("Failed to fetch work item attributes");
            }

            // Step 2: Extract notesheet_original attribute (format:
            // FolderIndex#VersionNo#DocumentIndex)
            String notesheetValue = extractNotesheetOriginal(attributes);
            if (notesheetValue == null || notesheetValue.isEmpty()) {
                return createNotFoundResponse("No notesheet_original attribute found in work item");
            }

            log.info("Found notesheet_original attribute: {}", notesheetValue);

            // Step 3: Parse the notesheet value
            String[] parts = notesheetValue.split("#");
            if (parts.length < 3) {
                return createNotFoundResponse("Invalid notesheet_original format: " + notesheetValue);
            }

            String folderIndex = parts[0];
            String versionNo = parts[1];
            String documentIndex = parts[2];

            log.info("Parsed notesheet: folderIndex={}, versionNo={}, documentIndex={}", folderIndex, versionNo,
                    documentIndex);

            // Step 4: Download the document using documentIndex
            byte[] documentContent = downloadDocument(documentIndex, sessionId);
            if (documentContent == null || documentContent.length == 0) {
                return createNotFoundResponse("Failed to download document content");
            }

            // Step 5: Save to temp file
            String documentName = "notesheet_original_" + processInstanceId;
            String filePath = saveToTempFile(documentContent, documentName);

            // Build success response
            ObjectNode response = jsonMapper.createObjectNode();
            response.put("success", true);
            response.put("found", true);
            response.put("filePath", filePath);
            response.put("documentName", documentName);
            response.put("documentIndex", documentIndex);
            response.put("folderIndex", folderIndex);
            response.put("versionNo", versionNo);
            response.put("fileSize", documentContent.length);

            log.info("Successfully retrieved notesheet to: {}", filePath);
            return response;

        } catch (Exception e) {
            log.error("Error retrieving original notesheet: {}", e.getMessage(), e);
            return createErrorResponse("Error retrieving notesheet", e.getMessage());
        }
    }

    /**
     * Retrieves the notesheet document ID from work item attributes.
     * Parses the 'notesheet' attribute which contains: FolderIndex#DocumentIndex
     * 
     * @param processInstanceId Process Instance ID
     * @param workitemId        Work Item ID
     * @param sessionId         Session ID for authentication
     * @return JSON with documentIndex or error
     */
    public JsonNode getNotesheet(String processInstanceId, String workitemId, long sessionId) {
        log.info("Getting notesheet for processInstanceId: {}, workitemId: {}", processInstanceId, workitemId);

        try {
            // Step 1: Get work item attributes
            JsonNode attributes = fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
            if (attributes == null || attributes.has("error")) {
                return createNotFoundResponse("Failed to fetch work item attributes");
            }

            // Step 2: Extract 'notesheet' attribute (format: FolderIndex#DocumentIndex)
            String notesheetValue = extractNotesheetAttribute(attributes);
            if (notesheetValue == null || notesheetValue.isEmpty()) {
                return createNotFoundResponse("No 'notesheet' attribute found in work item");
            }

            log.info("Found notesheet attribute: {}", notesheetValue);

            // Step 3: Parse the notesheet value (format: folder#version#docid)
            String[] parts = notesheetValue.split("#");
            if (parts.length < 3) {
                return createNotFoundResponse(
                        "Invalid notesheet format: " + notesheetValue + ". Expected: folder#version#docid");
            }

            String folderIndex = parts[0];
            String versionNo = parts[1];
            String documentIndex = parts[2];

            log.info("Parsed notesheet: folderIndex={}, versionNo={}, documentIndex={}", folderIndex, versionNo,
                    documentIndex);

            // Build success response
            ObjectNode response = jsonMapper.createObjectNode();
            response.put("success", true);
            response.put("found", true);
            response.put("documentIndex", documentIndex);
            response.put("folderIndex", folderIndex);
            response.put("versionNo", versionNo);

            return response;

        } catch (Exception e) {
            log.error("Error retrieving notesheet: {}", e.getMessage(), e);
            return createErrorResponse("Error retrieving notesheet", e.getMessage());
        }
    }

    /**
     * Extracts the 'notesheet' attribute value from work item attributes.
     * The attribute contains: FolderIndex#DocumentIndex
     */
    private String extractNotesheetAttribute(JsonNode attributes) {
        // Navigate through the response structure
        JsonNode output = attributes.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) {
            output = attributes;
        }

        JsonNode attrs = output.path("Attributes");
        if (attrs.isMissingNode()) {
            attrs = output;
        }

        // Look for 'notesheet' attribute (various case variants)
        for (String key : new String[] { "notesheet", "Notesheet", "NOTESHEET" }) {
            JsonNode node = attrs.path(key);
            if (!node.isMissingNode()) {
                String value = node.asText();
                if (value != null && !value.isEmpty() && !value.equals("null")) {
                    return value;
                }
                // Try empty key (iBPS JSON format)
                value = node.path("").asText();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        log.warn("notesheet attribute not found in response");
        return null;
    }

    /**
     * Extracts the notesheet_original attribute value from work item attributes.
     * The attribute contains: FolderIndex#VersionNo#DocumentIndex
     */
    private String extractNotesheetOriginal(JsonNode attributes) {
        // Navigate through the response structure
        JsonNode output = attributes.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) {
            output = attributes;
        }

        JsonNode attrs = output.path("Attributes");
        if (attrs.isMissingNode()) {
            attrs = output;
        }

        // Look for notesheet_original attribute
        JsonNode notesheetAttr = attrs.path("notesheet_original");
        if (!notesheetAttr.isMissingNode()) {
            // Value can be directly in the node text or in a "content" or "" key
            String value = notesheetAttr.asText();
            if (value != null && !value.isEmpty() && !value.equals("null")) {
                return value;
            }
            // Try empty key (iBPS JSON format)
            value = notesheetAttr.path("").asText();
            if (value != null && !value.isEmpty()) {
                return value;
            }
            // Try "content" key
            value = notesheetAttr.path("content").asText();
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // Also try lowercase/uppercase variants
        for (String key : new String[] { "notesheet_original", "NoteSheet_Original", "NOTESHEET_ORIGINAL" }) {
            JsonNode node = attrs.path(key);
            if (!node.isMissingNode()) {
                String value = node.asText();
                if (value != null && !value.isEmpty() && !value.equals("null")) {
                    return value;
                }
                value = node.path("").asText();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        log.warn("notesheet_original attribute not found in response");
        return null;
    }

    /**
     * Retrieves annotations for a specific document.
     * Uses NGOGetAnnotationGroupList API via executeAPIJSON.
     * 
     * @param documentIndex Document Index
     * @param sessionId     Session ID for authentication
     * @return JSON response with annotations and file path
     */
    public JsonNode getAnnotations(String documentIndex, long sessionId) {
        log.info("Getting annotations for documentIndex: {}", documentIndex);

        try {
            // Step 1: Build NGOGetAnnotationGroupList Input
            ObjectNode input = jsonMapper.createObjectNode();
            input.put("Option", "NGOGetAnnotationGroupList");
            input.put("CabinetName", cabinetName);
            input.put("UserDBId", String.valueOf(sessionId));
            input.put("DocumentIndex", documentIndex);
            input.put("PageNo", "1"); // Default to page 1
            input.put("PreviousAnnotationIndex", "0");
            // Omit VersionNo to get annotations for current/latest version
            input.put("SortOrder", "A");
            input.put("NoOfRecordsToFetch", "100");

            // Step 2: Wrap in NGOExecuteAPIBDO structure for executeAPIJSON
            ObjectNode inputData = jsonMapper.createObjectNode();
            inputData.set("NGOGetAnnotationGroupList_Input", input);

            ObjectNode ngoExecuteBDO = jsonMapper.createObjectNode();
            ngoExecuteBDO.set("inputData", inputData);
            ngoExecuteBDO.put("base64Encoded", "N");
            ngoExecuteBDO.put("locale", "en_US");

            ObjectNode payload = jsonMapper.createObjectNode();
            payload.set("NGOExecuteAPIBDO", ngoExecuteBDO);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

            log.info("Calling OmniDocs NGOGetAnnotationGroupList: docIndex={}, url={}", documentIndex, omniDocsApiUrl);
            log.debug("Payload: {}", payload.toString());

            ResponseEntity<String> response = restTemplate.exchange(omniDocsApiUrl, HttpMethod.POST, request,
                    String.class);

            // Step 3: Parse response
            JsonNode responseJson = parseXmlToJson(response.getBody());
            JsonNode output = responseJson.path("NGOExecuteAPIResponseBDO").path("outputData")
                    .path("NGOGetAnnotationGroupList_Output");

            if (output.isMissingNode() || output.path("Status").asInt() != 0) {
                String error = output.path("Error").asText(output.path("Status").asText("Unknown Error"));
                log.error("OmniDocs Error: {}", error);
                return createErrorResponse("Failed to get annotations", error);
            }

            JsonNode annotations = output.path("AnnotationGroups");
            log.info("Retrieved annotations count/status: {}", annotations.size());

            // Step 4: Save to temp file
            String fileName = "annotations_" + documentIndex + ".json";
            Path tempDir = Paths.get(tempDirectory.replace("notesheets", "annotations")); // Use distinct folder
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            Path filePath = tempDir.resolve(uniqueFileName);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(output.toPrettyString().getBytes());
            }

            // Step 5: Build success response
            ObjectNode result = jsonMapper.createObjectNode();
            result.put("success", true);
            result.put("found", true);
            result.put("documentIndex", documentIndex);
            result.put("filePath", filePath.toAbsolutePath().toString());
            result.set("annotations", annotations);

            return result;

        } catch (Exception e) {
            log.error("Error retrieving annotations: {}", e.getMessage(), e);
            return createErrorResponse("Error retrieving annotations", e.getMessage());
        }
    }

    /**
     * Sets annotations on a specific document.
     * Uses NGOAddAnnotation API via executeAPIJSON.
     * 
     * @param documentIndex Target Document Index
     * @param annotations   JSON node containing "AnnotationGroup" (object or array)
     * @param sessionId     Session ID for authentication
     * @return Result of the operation
     */
    public JsonNode setAnnotations(String documentIndex, JsonNode annotations, long sessionId) {
        log.info("Setting annotations for documentIndex: {}", documentIndex);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode results = result.putArray("results");

        try {
            JsonNode groupsNode = annotations.path("AnnotationGroup");
            if (groupsNode.isMissingNode()) {
                // Try if root is array or just the object
                if (annotations.isArray() || annotations.has("AnnotGroupName")) {
                    groupsNode = annotations;
                } else {
                    return createErrorResponse("No annotations found in input", "Missing AnnotationGroup");
                }
            }

            List<JsonNode> groupList = new ArrayList<>();
            if (groupsNode.isArray()) {
                for (JsonNode g : groupsNode) {
                    groupList.add(g);
                }
            } else {
                groupList.add(groupsNode);
            }

            log.info("Found {} annotation groups to apply", groupList.size());

            for (JsonNode g : groupList) {
                String groupName = g.path("AnnotGroupName").asText("Unknown");
                try {
                    // Extract fields for NGOAddAnnotation
                    String annotType = g.path("AnnotationType").asText("");
                    String pageNo = g.path("PageNo").asText("1");
                    String accessType = g.path("AccessType").asText("I"); // Default to I if missing
                    String buffer = g.path("AnnotationBuffer").asText("");

                    // Build Input
                    ObjectNode annotationGroup = mapper.createObjectNode();
                    annotationGroup.put("AnnotationType", annotType);
                    annotationGroup.put("PageNo", pageNo);
                    annotationGroup.put("AnnotGroupName", groupName);
                    annotationGroup.put("AccessType", accessType);
                    annotationGroup.put("AnnotationBuffer", buffer);

                    ObjectNode input = mapper.createObjectNode();
                    input.put("Option", "NGOAddAnnotation");
                    input.put("CabinetName", cabinetName);
                    input.put("UserDBId", String.valueOf(sessionId));
                    input.put("DocumentIndex", documentIndex);
                    input.set("AnnotationGroup", annotationGroup);
                    input.put("MajorVersion", "N"); // Do not force major version

                    // Wrap in NGOExecuteAPIBDO
                    ObjectNode inputData = mapper.createObjectNode();
                    inputData.set("NGOAddAnnotation_Input", input);

                    ObjectNode ngoExecuteBDO = mapper.createObjectNode();
                    ngoExecuteBDO.set("inputData", inputData);
                    ngoExecuteBDO.put("base64Encoded", "N");
                    ngoExecuteBDO.put("locale", "en_US");

                    ObjectNode payload = mapper.createObjectNode();
                    payload.set("NGOExecuteAPIBDO", ngoExecuteBDO);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

                    log.info("Calling NGOAddAnnotation for group: {}", groupName);
                    ResponseEntity<String> response = restTemplate.exchange(omniDocsApiUrl, HttpMethod.POST, request,
                            String.class);

                    JsonNode responseJson = parseXmlToJson(response.getBody());
                    JsonNode output = responseJson.path("NGOExecuteAPIResponseBDO").path("outputData")
                            .path("NGOAddAnnotation_Output");

                    ObjectNode groupResult = mapper.createObjectNode();
                    groupResult.put("groupName", groupName);

                    int status = output.path("Status").asInt();
                    if (status == 0) {
                        groupResult.put("status", "Success");
                    } else if (status == -50090) {
                        groupResult.put("status", "Skipped (Already Exists)");
                    } else {
                        groupResult.put("status", "Failed");
                        groupResult.put("error", output.path("Error").asText("Unknown Error"));
                        groupResult.put("omiDocsStatus", status);
                    }
                    results.add(groupResult);

                } catch (Exception e) {
                    log.error("Failed to add annotation group {}: {}", groupName, e.getMessage());
                    ObjectNode groupResult = mapper.createObjectNode();
                    groupResult.put("groupName", groupName);
                    groupResult.put("status", "Exception");
                    groupResult.put("error", e.getMessage());
                    results.add(groupResult);
                }
            }

            result.put("success", true);
            result.put("processedCount", groupList.size());
            return result;

        } catch (Exception e) {
            log.error("Error setting annotations: {}", e.getMessage(), e);
            return createErrorResponse("Error setting annotations", e.getMessage());
        }
    }

    /**
     * Retrieves comments for a work item.
     * Extracts 'Q_Eoffice_Commentshistory' from attributes.
     */
    public JsonNode getComments(String processInstanceId, String workitemId, long sessionId) {
        log.info("Getting comments for work item: {} / {}", processInstanceId, workitemId);

        // Fetch attributes
        JsonNode attributesResponse = fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);

        // Navigate to attributes
        JsonNode output = attributesResponse.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode())
            output = attributesResponse; // Handle unwrapped

        JsonNode attributes = output.path("Attributes");
        if (attributes.isMissingNode()) {
            return createErrorResponse("No attributes found", "Failed to fetch work item attributes");
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode commentsList = result.putArray("comments");

        // internal helper to find attribute (recursive or case-insensitive)
        JsonNode historyAttr = attributes.path("Q_Eoffice_Commentshistory");

        // If it's an array (Complex Array in iBPS)
        if (historyAttr.isArray()) {
            for (JsonNode item : historyAttr) {
                ObjectNode comment = mapper.createObjectNode();

                // Extract useful fields
                comment.put("userName", getValue(item, "username"));
                comment.put("userId", getValue(item, "userid"));
                comment.put("dateTime", getValue(item, "datetime")); // 2025-12-12 14:46:03.0
                comment.put("comments", getValue(item, "comments"));
                comment.put("stage", getValue(item, "stagename"));
                commentsList.add(comment);
            }
        } else if (!historyAttr.isMissingNode()) {
            // Single object?
            ObjectNode comment = mapper.createObjectNode();
            comment.put("userName", getValue(historyAttr, "username"));
            comment.put("userId", getValue(historyAttr, "userid"));
            comment.put("dateTime", getValue(historyAttr, "datetime"));
            comment.put("comments", getValue(historyAttr, "comments"));
            comment.put("stage", getValue(historyAttr, "stagename"));
            comment.put("status", getValue(historyAttr, "email"));
            commentsList.add(comment);
        }

        result.put("success", true);
        result.put("count", commentsList.size());

        return result;
    }

    private String getValue(JsonNode parent, String key) {
        if (parent.has(key)) {
            // iBPS attributes often have text in the node itself or empty key ""
            JsonNode node = parent.get(key);
            if (node.has(""))
                return node.get("").asText();
            // If it's just text
            if (node.isTextual())
                return node.asText();
            // If it is object but no "" key, try to stringify or return empty
            return "";
        }
        return "";
    }

    /**
     * Dumps work item details to a temp file for debugging.
     * Aggregates WMFetchWorkItemAttributes and WMGetWorkItem.
     */
    public String dumpWorkItemDetails(String processInstanceId, String workitemId, long sessionId) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode debugData = mapper.createObjectNode();

        try {
            // 1. Fetch Attributes
            try {
                debugData.set("WMFetchWorkItemAttributes",
                        fetchWorkItemAttributes(processInstanceId, workitemId, sessionId));
            } catch (Exception e) {
                debugData.put("WMFetchWorkItemAttributes_Error", e.getMessage());
            }

            // 2. Fetch Work Item Details (WMGetWorkItem)
            try {
                String engineName = getEngineName();
                int workItemIdInt = Integer.parseInt(workitemId);
                String url = String.format(getWorkItemUrlTemplate, engineName, processInstanceId, workItemIdInt);

                log.info("Calling WMGetWorkItem: {}", url);

                HttpHeaders headers = new HttpHeaders();
                headers.set("sessionId", String.valueOf(sessionId));
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Void> request = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
                JsonNode getWorkItemJson = parseXmlToJson(response.getBody());
                debugData.set("WMGetWorkItem", getWorkItemJson);

            } catch (Exception e) {
                debugData.put("WMGetWorkItem_Error", e.getMessage());
            }

            // Save to file
            Path tempDir = Paths.get(tempDirectory).getParent(); // Save in tmp root or specific debug folder
            if (tempDir == null)
                tempDir = Paths.get("tmp");
            if (!Files.exists(tempDir))
                Files.createDirectories(tempDir);

            String fileName = "workitem_dump_" + processInstanceId + "_" + workitemId + ".json";
            Path filePath = tempDir.resolve(fileName);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(debugData.toPrettyString().getBytes());
            }

            return filePath.toAbsolutePath().toString();

        } catch (Exception e) {
            log.error("Error dumping work item details: {}", e.getMessage(), e);
            throw new RuntimeException("Dump failed: " + e.getMessage());
        }
    }

    /**
     * Fetches work item attributes from iBPS.
     */
    private JsonNode fetchWorkItemAttributes(String processInstanceId, String workitemId, long sessionId) {
        try {
            String engineName = getEngineName();
            int workItemIdInt = Integer.parseInt(workitemId);
            String url = String.format(fetchAttributesUrlTemplate, engineName, processInstanceId, workItemIdInt);

            // Build XML payload
            String xmlPayload = buildFetchAttributesPayload(engineName, processInstanceId, workItemIdInt, sessionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);
            headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_XML));
            headers.set("sessionId", String.valueOf(sessionId)); // Session ID must be in header!

            HttpEntity<String> request = new HttpEntity<>(xmlPayload, headers);

            log.debug("Calling WMFetchWorkItemAttributes: url={}, sessionId={}", url, sessionId);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            JsonNode result = parseXmlToJson(response.getBody());

            // Check for API-level errors
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
            // iBPS returns HTTP 500 with JSON or XML error body - parse it
            String responseBody = e.getResponseBodyAsString();
            log.warn("iBPS returned HTTP 500, parsing error body: {}",
                    responseBody.substring(0, Math.min(300, responseBody.length())));
            try {
                JsonNode errorJson;

                // Check if response is JSON array (starts with [)
                if (responseBody.trim().startsWith("[")) {
                    errorJson = jsonMapper.readTree(responseBody).get(0);
                } else if (responseBody.trim().startsWith("{")) {
                    errorJson = jsonMapper.readTree(responseBody);
                } else {
                    // XML response - parse it (parseXmlToJson handles <?xml...)
                    errorJson = parseXmlToJson(responseBody);
                }

                log.debug("Parsed error response: {}", errorJson);

                // Try to extract error from WMFetchWorkItemAttributes_Output
                JsonNode output = errorJson.path("WMFetchWorkItemAttributes_Output");
                if (output.isMissingNode()) {
                    // Try root level if not wrapped
                    output = errorJson;
                }

                int status = output.path("Status").asInt(-1);
                JsonNode errorNode = output.path("Error").path("Exception");
                String errorDesc = errorNode.path("Description").asText("");
                if (errorDesc.isEmpty()) {
                    errorDesc = errorNode.path("Subject").asText("Unknown iBPS error");
                }

                log.error("iBPS API error: Status={}, Description={}", status, errorDesc);
                return createNotFoundResponse("iBPS error (Status " + status + "): " + errorDesc);
            } catch (Exception parseEx) {
                log.error("Failed to parse iBPS error response: {}", parseEx.getMessage());
                return createNotFoundResponse("Error fetching work item: " + e.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching work item attributes: {}", e.getMessage(), e);
            return createErrorResponse("Error fetching attributes", e.getMessage());
        }
    }

    /**
     * Builds XML payload for WMFetchWorkItemAttributes API.
     */
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

    /**
     * Extracts folder index from work item attributes.
     * The folder index is stored in the 'itemindex' attribute.
     */
    private String extractFolderIndex(JsonNode attributes) {
        // Check if itemtype is 'F' (folder)
        JsonNode output = attributes.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) {
            output = attributes;
        }

        JsonNode attrs = output.path("Attributes");
        if (!attrs.isMissingNode() && attrs.isObject()) {
            // Look for itemindex attribute
            JsonNode itemIndexAttr = attrs.path("itemindex");
            if (!itemIndexAttr.isMissingNode()) {
                // Value is in empty key "" in iBPS response structure
                String folderIndex = itemIndexAttr.path("").asText();
                if (folderIndex != null && !folderIndex.isEmpty()) {
                    log.debug("Found itemindex: {}", folderIndex);
                    return folderIndex;
                }
            }

            // Try FolderIndex as fallback
            JsonNode folderIndexAttr = attrs.path("FolderIndex");
            if (!folderIndexAttr.isMissingNode()) {
                String folderIndex = folderIndexAttr.path("").asText();
                if (folderIndex != null && !folderIndex.isEmpty()) {
                    return folderIndex;
                }
            }

            // Try AttachmentFolderId as fallback
            JsonNode attachmentAttr = attrs.path("AttachmentFolderId");
            if (!attachmentAttr.isMissingNode()) {
                String folderIndex = attachmentAttr.path("").asText();
                if (folderIndex != null && !folderIndex.isEmpty()) {
                    return folderIndex;
                }
            }
        }

        // Try array format
        if (attrs.isArray()) {
            for (JsonNode attr : attrs) {
                String name = attr.path("Name").asText("");
                if ("itemindex".equalsIgnoreCase(name) || "FolderIndex".equalsIgnoreCase(name)) {
                    return attr.path("Value").asText();
                }
            }
        }

        log.warn("Could not find folder index in attributes. Response: {}",
                attributes.toString().substring(0, Math.min(500, attributes.toString().length())));
        return null;
    }

    /**
     * Gets folder contents from OmniDocs.
     */
    private JsonNode getFolderContents(String folderIndex, long sessionId) {
        try {
            // Build XML request for getFolderContents
            String xmlInput = String.format(
                    "<?xml version=\"1.0\"?>" +
                            "<NGOGetFolderContents_Input>" +
                            "<Option>NGOGetFolderContents</Option>" +
                            "<SessionId>%d</SessionId>" +
                            "<CabinetName>%s</CabinetName>" +
                            "<FolderIndex>%s</FolderIndex>" +
                            "<ListingFilter>B</ListingFilter>" + // B=Both (folders and documents)
                            "<NoOfRecords>100</NoOfRecords>" +
                            "<StartIndex>0</StartIndex>" +
                            "<SortOrder>DESC</SortOrder>" +
                            "</NGOGetFolderContents_Input>",
                    sessionId, cabinetName, folderIndex);

            // Wrap XML in JSON for executeAPIJSON
            ObjectNode payload = jsonMapper.createObjectNode();
            payload.put("inputData", xmlInput);

            String url = omniDocsApiUrl + "?apiName=getFolderContents";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

            log.info("Calling OmniDocs getFolderContents: folderIndex={}, xmlPayload={}", folderIndex,
                    xmlInput.substring(0, Math.min(200, xmlInput.length())));
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
            return jsonMapper.readTree(response.getBody());

        } catch (Exception e) {
            log.error("Error getting folder contents: {}", e.getMessage(), e);
            return createErrorResponse("Error getting folder contents", e.getMessage());
        }
    }

    /**
     * Downloads document content from OmniDocs using getDocumentStreamJSON API.
     * Uses sessionId as userDBId for authentication (per reference project
     * pattern).
     */
    private byte[] downloadDocument(String documentIndex, long sessionId) {
        try {
            // Build NGOGetDocumentBDO payload (matches reference project pattern)
            ObjectNode ngoGetDocumentBDO = jsonMapper.createObjectNode();
            ngoGetDocumentBDO.put("cabinetName", cabinetName);
            ngoGetDocumentBDO.put("docIndex", documentIndex);
            ngoGetDocumentBDO.put("versionNo", "");  // Empty string = get latest version
            // Use sessionId as userDBId for authentication
            ngoGetDocumentBDO.put("userDBId", String.valueOf(sessionId));
            ngoGetDocumentBDO.put("userName", "");
            ngoGetDocumentBDO.put("userPassword", "");
            ngoGetDocumentBDO.put("authToken", "");
            ngoGetDocumentBDO.put("authTokenType", "");
            ngoGetDocumentBDO.put("locale", "en_US");
            ngoGetDocumentBDO.put("oAuth", "N");
            ngoGetDocumentBDO.put("sessionValid", "");

            ObjectNode payload = jsonMapper.createObjectNode();
            payload.set("NGOGetDocumentBDO", ngoGetDocumentBDO);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

            log.info("Calling OmniDocs getDocumentStreamJSON: docIndex={}, url={}", documentIndex, getDocumentUrl);
            log.debug("Payload: {}", payload.toString());

            ResponseEntity<byte[]> response = restTemplate.exchange(getDocumentUrl, HttpMethod.POST, request,
                    byte[].class);

            byte[] content = response.getBody();
            if (content != null && content.length > 0) {
                log.info("Downloaded document: {} bytes", content.length);
                return content;
            }

            log.warn("No document content in response");
            return null;

        } catch (Exception e) {
            log.error("Error downloading document: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Saves document content to a temp file with unique name.
     */
    private String saveToTempFile(byte[] content, String originalFileName) throws IOException {
        // Ensure temp directory exists
        Path tempDir = Paths.get(tempDirectory);
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        // Generate unique filename
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
        }
        String uniqueFileName = UUID.randomUUID().toString() + extension;

        Path filePath = tempDir.resolve(uniqueFileName);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(content);
        }

        return filePath.toAbsolutePath().toString();
    }

    /**
     * Downloads a document with annotations burned into the PDF.
     * Uses PDFBox to render annotations onto the document.
     *
     * @param documentIndex Document Index
     * @param sessionId     Session ID for authentication
     * @return byte array of the PDF with annotations, or null on error
     */
    public byte[] downloadDocumentWithAnnotations(String documentIndex, long sessionId) {
        log.info("Downloading document with annotations: documentIndex={}", documentIndex);

        try {
            // Step 1: Download the original document
            byte[] documentContent = downloadDocument(documentIndex, sessionId);
            if (documentContent == null || documentContent.length == 0) {
                log.error("Failed to download document content");
                return null;
            }
            log.info("Downloaded document: {} bytes", documentContent.length);

            // Step 2: Get annotations
            JsonNode annotationsResult = getAnnotations(documentIndex, sessionId);
            if (!annotationsResult.path("success").asBoolean(false)) {
                log.warn("Failed to get annotations, returning original document");
                return documentContent;
            }

            JsonNode annotations = annotationsResult.path("annotations");
            if (annotations.isMissingNode() || annotations.isEmpty()) {
                log.info("No annotations found, returning original document");
                return documentContent;
            }

            // Step 3: Load PDF with PDFBox
            try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc =
                    org.apache.pdfbox.pdmodel.PDDocument.load(documentContent)) {

                // Step 4: Process each annotation group
                JsonNode groupsNode = annotations.path("AnnotationGroup");
                if (groupsNode.isMissingNode()) {
                    groupsNode = annotations;
                }

                java.util.List<JsonNode> groupList = new java.util.ArrayList<>();
                if (groupsNode.isArray()) {
                    for (JsonNode g : groupsNode) {
                        groupList.add(g);
                    }
                } else if (!groupsNode.isEmpty()) {
                    groupList.add(groupsNode);
                }

                log.info("Processing {} annotation groups", groupList.size());

                for (JsonNode group : groupList) {
                    int pageNo = group.path("PageNo").asInt(1) - 1; // Convert to 0-based
                    String buffer = group.path("AnnotationBuffer").asText("");

                    if (buffer.isEmpty() || pageNo < 0 || pageNo >= pdfDoc.getNumberOfPages()) {
                        continue;
                    }

                    org.apache.pdfbox.pdmodel.PDPage page = pdfDoc.getPage(pageNo);
                    float pageHeight = page.getMediaBox().getHeight();

                    // Parse and render annotations from buffer
                    renderAnnotationsFromBuffer(pdfDoc, page, buffer, pageHeight);
                }

                // Step 5: Write to byte array
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                pdfDoc.save(baos);
                log.info("Generated PDF with annotations: {} bytes", baos.size());
                return baos.toByteArray();
            }

        } catch (Exception e) {
            log.error("Error downloading document with annotations: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parses annotation buffer and renders annotations onto a PDF page.
     * Annotation buffer format is INI-style with sections like [GroupNameAnnotation1]
     */
    private void renderAnnotationsFromBuffer(org.apache.pdfbox.pdmodel.PDDocument doc,
            org.apache.pdfbox.pdmodel.PDPage page, String buffer, float pageHeight) {

        try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page,
                    org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true)) {

            // Parse the buffer into sections
            String[] lines = buffer.split("\n");
            java.util.Map<String, String> currentSection = null;
            String currentSectionName = "";

            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("[") && line.endsWith("]")) {
                    // Process previous section if exists
                    if (currentSection != null && !currentSection.isEmpty()) {
                        renderAnnotation(cs, currentSectionName, currentSection, pageHeight);
                    }
                    // Start new section
                    currentSectionName = line.substring(1, line.length() - 1);
                    currentSection = new java.util.HashMap<>();
                } else if (line.contains("=") && currentSection != null) {
                    int idx = line.indexOf("=");
                    String key = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();
                    currentSection.put(key, value);
                }
            }

            // Process last section
            if (currentSection != null && !currentSection.isEmpty()) {
                renderAnnotation(cs, currentSectionName, currentSection, pageHeight);
            }

        } catch (Exception e) {
            log.error("Error rendering annotations: {}", e.getMessage(), e);
        }
    }

    /**
     * Renders a single annotation based on its type.
     */
    private void renderAnnotation(org.apache.pdfbox.pdmodel.PDPageContentStream cs,
            String sectionName, java.util.Map<String, String> props, float pageHeight) {

        try {
            // Determine annotation type from section name
            String type = "";
            if (sectionName.contains("Line") || sectionName.contains("LNE")) {
                type = "LINE";
            } else if (sectionName.contains("Box") || sectionName.contains("BOX")) {
                type = "BOX";
            } else if (sectionName.contains("Hyperlink")) {
                type = "HYPERLINK";
            } else if (sectionName.contains("TextStamp") || sectionName.contains("TXTSTAMP")) {
                type = "TEXTSTAMP";
            } else if (sectionName.contains("Highlight") || sectionName.contains("HLT")) {
                type = "HIGHLIGHT";
            } else if (sectionName.contains("FreeHand") || sectionName.contains("FRH")) {
                type = "FREEHAND";
            }

            // Get coordinates (OmniDocs: Y from top, PDFBox: Y from bottom)
            float x1 = parseFloat(props.get("X1"), 0);
            float y1 = parseFloat(props.get("Y1"), 0);
            float x2 = parseFloat(props.get("X2"), 0);
            float y2 = parseFloat(props.get("Y2"), 0);

            // Convert OmniDocs coordinates to PDFBox coordinates
            // OmniDocs uses a scale factor relative to PDF coordinates
            float scale = pageHeight / 1040f; // Approximate OmniDocs page height
            float pdfX1 = x1 * scale;
            float pdfY1 = pageHeight - (y1 * scale);
            float pdfX2 = x2 * scale;
            float pdfY2 = pageHeight - (y2 * scale);

            // Parse color (OmniDocs stores as integer)
            int colorInt = parseInt(props.get("Color"), 0);
            float r = ((colorInt >> 16) & 0xFF) / 255f;
            float g = ((colorInt >> 8) & 0xFF) / 255f;
            float b = (colorInt & 0xFF) / 255f;

            switch (type) {
                case "LINE":
                    cs.setStrokingColor(r, g, b);
                    cs.setLineWidth(1f);
                    cs.moveTo(pdfX1, pdfY1);
                    cs.lineTo(pdfX2, pdfY2);
                    cs.stroke();
                    break;

                case "BOX":
                    cs.setStrokingColor(r, g, b);
                    cs.setLineWidth(1f);
                    float width = Math.abs(pdfX2 - pdfX1);
                    float height = Math.abs(pdfY2 - pdfY1);
                    cs.addRect(Math.min(pdfX1, pdfX2), Math.min(pdfY1, pdfY2), width, height);
                    cs.stroke();
                    break;

                case "HIGHLIGHT":
                    cs.setNonStrokingColor(r, g, b);
                    cs.addRect(Math.min(pdfX1, pdfX2), Math.min(pdfY1, pdfY2),
                            Math.abs(pdfX2 - pdfX1), Math.abs(pdfY2 - pdfY1));
                    // Use transparency effect by drawing a semi-transparent rectangle
                    // Note: PDFBox basic content stream doesn't support transparency directly
                    // For now, just fill with color
                    cs.fill();
                    break;

                case "HYPERLINK":
                    // Draw hyperlink text
                    String linkName = props.getOrDefault("HyperlinkName", "View");
                    cs.beginText();
                    cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 10);
                    cs.setNonStrokingColor(0, 0, 1); // Blue for hyperlinks
                    cs.newLineAtOffset(pdfX1, pdfY1);
                    cs.showText(linkName);
                    cs.endText();
                    break;

                case "TEXTSTAMP":
                    String text = props.getOrDefault("StampText", "");
                    if (!text.isEmpty()) {
                        cs.beginText();
                        cs.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 10);
                        cs.setNonStrokingColor(r, g, b);
                        cs.newLineAtOffset(pdfX1, pdfY1);
                        cs.showText(text);
                        cs.endText();
                    }
                    break;

                default:
                    // Unknown type, skip
                    break;
            }
        } catch (Exception e) {
            log.warn("Error rendering annotation {}: {}", sectionName, e.getMessage());
        }
    }

    private float parseFloat(String s, float defaultValue) {
        if (s == null || s.isEmpty()) return defaultValue;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int parseInt(String s, int defaultValue) {
        if (s == null || s.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Creates a "not found" response (success but document not found).
     */
    private JsonNode createNotFoundResponse(String message) {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("success", true);
        response.put("found", false);
        response.put("message", message);
        return response;
    }

    // ========== CREATE PDF NOTE IMPLEMENTATION ==========

    @org.springframework.beans.factory.annotation.Autowired
    private DocumentOpsService documentOpsService;

    @org.springframework.beans.factory.annotation.Autowired
    private SupportingDocsService supportingDocsService;

    /**
     * Creates a PDF note from the original notesheet with appended comments and supporting documents.
     * Flow:
     * 1. Call getOriginalNotesheet - get originalnotesheetdocindex
     * 2. Call getComments - save to temp file
     * 3. Call getNotesheet - get notedocumentIndex
     * 4. Call getSupportingDocuments - get list of attached documents
     * 5. Generate PDF from HTML + documents + comments
     * 6. Call checkoutCheckinWithAnnotations to update notesheet
     */
    public JsonNode createPdfNote(String processInstanceId, String workitemId, long sessionId) {
        log.info("Creating PDF note for processInstanceId: {}, workitemId: {}", processInstanceId, workitemId);
        String uniqueId = UUID.randomUUID().toString();

        try {
            // Step 1: Call getOriginalNotesheet
            log.info("Step 1: Getting original notesheet...");
            JsonNode originalResult = getOriginalNotesheet(processInstanceId, workitemId, sessionId);
            if (!originalResult.path("success").asBoolean(false) || !originalResult.path("found").asBoolean(false)) {
                return createErrorResponse("Failed to get original notesheet",
                        originalResult.path("message").asText("Not found"));
            }
            String originalDocIndex = originalResult.path("documentIndex").asText();
            String htmlFilePath = originalResult.path("filePath").asText();
            log.info("Original notesheet docIndex: {}, filePath: {}", originalDocIndex, htmlFilePath);

            // Step 2: Call getComments
            log.info("Step 2: Getting comments...");
            JsonNode commentsResult = getComments(processInstanceId, workitemId, sessionId);
            String commentsPath = saveCommentsToFile(commentsResult, uniqueId);
            log.info("Comments saved to: {}", commentsPath);

            // Step 3: Call getNotesheet
            log.info("Step 3: Getting notesheet document index...");
            JsonNode notesheetResult = getNotesheet(processInstanceId, workitemId, sessionId);
            if (!notesheetResult.path("success").asBoolean(false) || !notesheetResult.path("found").asBoolean(false)) {
                return createErrorResponse("Failed to get notesheet",
                        notesheetResult.path("message").asText("Not found"));
            }
            String notedocumentIndex = notesheetResult.path("documentIndex").asText();
            log.info("Notesheet docIndex: {}", notedocumentIndex);

            // Step 4: Call getSupportingDocuments
            log.info("Step 4: Getting supporting documents...");
            JsonNode supportingDocsResult = supportingDocsService.getSupportingDocuments(processInstanceId, workitemId, sessionId);
            JsonNode documentsArray = supportingDocsResult.path("documents");
            int docCount = supportingDocsResult.path("count").asInt(0);
            log.info("Found {} supporting documents", docCount);

            // Step 5: Generate PDF with documents, comments, and track View positions
            log.info("Step 5: Generating PDF with documents, comments, and position tracking...");
            JsonNode commentsArray = commentsResult.path("comments");
            PdfGenerationResult pdfResult = generatePdfWithPositions(htmlFilePath, documentsArray, commentsArray, uniqueId);
            String pdfPath = pdfResult.pdfPath;
            List<ViewLinkPosition> viewPositions = pdfResult.viewPositions;
            log.info("PDF generated at: {} with {} view positions", pdfPath, viewPositions.size());

            // Step 6: Call checkoutCheckinWithAnnotations (with filtering of existing View hyperlinks)
            log.info("Step 6: Updating notesheet with new PDF...");
            JsonNode updateResult = documentOpsService.checkoutCheckinWithAnnotations(
                    notedocumentIndex, pdfPath, sessionId, true);

            if (!updateResult.path("success").asBoolean(false)) {
                return createErrorResponse("Failed to update notesheet",
                        updateResult.path("error").asText("Update failed"));
            }

            // Step 7: Add View hyperlink annotations
            log.info("Step 7: Adding View hyperlink annotations...");
            int annotationsAdded = 0;
            if (!viewPositions.isEmpty()) {
                String annotBuffer = buildHyperlinkAnnotationBuffer(viewPositions, "ViewLinks", "system");
                ObjectNode annotGroup = jsonMapper.createObjectNode();
                annotGroup.put("AnnotationType", "A");  // "A" = Annotation type used by working hyperlinks
                annotGroup.put("PageNo", "1");  // Will be handled per-page in buffer
                annotGroup.put("AnnotGroupName", "ViewLinks");
                annotGroup.put("AccessType", "S");  // Shared
                annotGroup.put("AnnotationBuffer", annotBuffer);

                JsonNode annotResult = setAnnotations(notedocumentIndex, annotGroup, sessionId);
                if (annotResult.path("success").asBoolean(false)) {
                    annotationsAdded = viewPositions.size();
                    log.info("Successfully added {} View hyperlink annotations", annotationsAdded);
                } else {
                    log.warn("Failed to add View hyperlink annotations: {}", annotResult.path("error").asText());
                }
            }

            // Build success response
            ObjectNode result = jsonMapper.createObjectNode();
            result.put("success", true);
            result.put("originalDocIndex", originalDocIndex);
            result.put("notedocumentIndex", notedocumentIndex);
            result.put("newVersion", updateResult.path("newVersion").asText());
            result.put("pdfPath", pdfPath);
            result.put("commentsPath", commentsPath);
            result.put("annotationsPreserved", updateResult.path("annotationsRestored").asBoolean(false));
            result.put("viewHyperlinksAdded", annotationsAdded);

            log.info("PDF note created successfully. New version: {}", updateResult.path("newVersion").asText());
            return result;

        } catch (Exception e) {
            log.error("Error creating PDF note: {}", e.getMessage(), e);
            return createErrorResponse("Error creating PDF note", e.getMessage());
        }
    }

    /**
     * Saves comments JSON to a temp file.
     */
    private String saveCommentsToFile(JsonNode commentsResult, String uniqueId) throws IOException {
        Path tempDir = Paths.get(tempDirectory.replace("notesheets", "comments"));
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        String fileName = "comments-" + uniqueId + ".json";
        Path filePath = tempDir.resolve(fileName);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(commentsResult.toPrettyString().getBytes());
        }

        return filePath.toAbsolutePath().toString();
    }

    /**
     * Generates PDF from original HTML content with supporting documents and appended comments.
     * Also tracks the positions of View elements for hyperlink annotation creation.
     */
    private PdfGenerationResult generatePdfWithPositions(String htmlFilePath, JsonNode documents, JsonNode comments, String uniqueId) throws Exception {
        // Read original HTML content
        String htmlContent = new String(Files.readAllBytes(Paths.get(htmlFilePath)));

        // Load document templates
        String documentListTemplate = loadTemplate("templates/document_list_template.html");
        String documentRowTemplate = loadTemplate("templates/document_row_template.html");

        // Render document rows (above comments)
        RenderDocumentResult docResult = renderDocumentRows(documents, documentRowTemplate, docsViewerBaseUrl);
        String documentsSection = documentListTemplate.replace("{{DOCUMENT_ROWS}}", docResult.html);

        // Load comment templates
        String commentTemplate = loadTemplate("templates/comment_template.html");
        String rowTemplate = loadTemplate("templates/comment_row_template.html");

        // Render comment rows
        String commentRows = renderCommentRows(comments, rowTemplate);
        String commentsSection = commentTemplate.replace("{{COMMENTS_ROWS}}", commentRows);

        // Clean up HTML entities and wrap in XHTML document structure
        // The original content is HTML fragments, we need to wrap in proper XHTML
        // Documents section comes ABOVE comments section per user requirement
        String bodyContent = htmlContent + documentsSection + commentsSection;

        // Replace HTML entities that are not valid in XHTML
        bodyContent = bodyContent.replace("&nbsp;", "&#160;");
        bodyContent = bodyContent.replace("&amp;", "&#38;");
        // Fix self-closing tags for XHTML compliance
        bodyContent = bodyContent.replaceAll("<br>", "<br/>");
        bodyContent = bodyContent.replaceAll("<hr>", "<hr/>");
        bodyContent = bodyContent.replaceAll("<img([^>]*)>", "<img$1/>");
        // Remove HTML comments that contain -- which is invalid in XML
        bodyContent = bodyContent.replaceAll("<!--.*?-->", "");
        // Remove any other comment-like patterns that might be problematic
        bodyContent = bodyContent.replaceAll("<!\\[CDATA\\[.*?\\]\\]>", "");

        // Wrap in proper XHTML document
        String fullHtml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" " +
                "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n" +
                "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                "<head>\n" +
                "  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n" +
                "  <style type=\"text/css\">\n" +
                "    body { font-family: Arial, sans-serif; font-size: 12px; margin: 20px; }\n" +
                "    table { border-collapse: collapse; width: 100%; }\n" +
                "    td, th { border: 1px solid #333; padding: 8px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                bodyContent + "\n" +
                "</body>\n" +
                "</html>";

        // Convert HTML to PDF using Flying Saucer
        Path tempDir = Paths.get(tempDirectory);
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        String pdfFileName = "newNoteContent-" + uniqueId + ".pdf";
        Path pdfPath = tempDir.resolve(pdfFileName);

        List<ViewLinkPosition> viewPositions = new ArrayList<>();

        // Create the PDF first
        try (java.io.OutputStream os = new FileOutputStream(pdfPath.toFile())) {
            org.xhtmlrenderer.pdf.ITextRenderer renderer = new org.xhtmlrenderer.pdf.ITextRenderer();
            renderer.setDocumentFromString(fullHtml);
            renderer.layout();

            // Log renderer info for debugging
            float dotsPerPoint = renderer.getDotsPerPoint();
            log.info("Flying Saucer dotsPerPoint: {}", dotsPerPoint);

            renderer.createPDF(os);
        }

        // Now extract View positions using PDFBox from the actual rendered PDF
        // This gives us accurate coordinates that match the actual PDF content
        viewPositions = extractViewPositionsFromPdf(pdfPath.toAbsolutePath().toString(), docResult.docIndices);

        log.info("Extracted {} view link positions from PDF for annotation creation", viewPositions.size());
        return new PdfGenerationResult(pdfPath.toAbsolutePath().toString(), viewPositions);
    }

    /**
     * Extracts View element positions from Flying Saucer renderer using element IDs.
     * Uses the layout tree to find elements with id="view-0", "view-1", etc.
     *
     * Coordinate system:
     * - Flying Saucer: origin at top-left, Y increases downward, units in "dots"
     * - OmniDocs: origin at top-left, Y increases downward, units in points
     * - Conversion: points = dots / dotsPerPoint
     */
    private List<ViewLinkPosition> extractViewPositionsFromRenderer(org.xhtmlrenderer.pdf.ITextRenderer renderer, List<String> docIndices) {
        List<ViewLinkPosition> positions = new ArrayList<>();

        try {
            org.xhtmlrenderer.render.BlockBox rootBox = renderer.getRootBox();
            float dotsPerPoint = renderer.getDotsPerPoint();

            // Get page height for page number calculation
            // A4 page is 842 points, but let's get it from the renderer if possible
            int pageHeightPts = 842;

            log.info("=== Flying Saucer Coordinate Extraction ===");
            log.info("dotsPerPoint: {}", dotsPerPoint);
            log.info("Document has {} view elements to find", docIndices.size());

            for (int i = 0; i < docIndices.size(); i++) {
                String elementId = "view-" + i;
                org.xhtmlrenderer.render.Box box = findBoxById(rootBox, elementId);

                if (box != null) {
                    // Get raw coordinates from Flying Saucer (in dots)
                    int rawAbsX = box.getAbsX();
                    int rawAbsY = box.getAbsY();
                    int rawWidth = box.getWidth();
                    int rawHeight = box.getHeight();

                    log.info("Element '{}' RAW coords: absX={}, absY={}, width={}, height={}",
                            elementId, rawAbsX, rawAbsY, rawWidth, rawHeight);

                    // If width is 0 (inline span), try parent (TD cell)
                    if (rawWidth <= 0) {
                        org.xhtmlrenderer.render.Box parent = box.getParent();
                        if (parent != null) {
                            log.info("  Parent box: absX={}, absY={}, width={}, height={}",
                                    parent.getAbsX(), parent.getAbsY(), parent.getWidth(), parent.getHeight());
                            if (parent.getWidth() > 0) {
                                rawWidth = parent.getWidth();
                                rawAbsX = parent.getAbsX();
                            }
                        }
                    }

                    // Ensure minimum dimensions
                    if (rawWidth <= 0) rawWidth = (int) (40 * dotsPerPoint); // ~40pt for "View" text
                    if (rawHeight <= 0) rawHeight = (int) (20 * dotsPerPoint); // ~20pt height

                    // Convert from dots to points
                    int x1 = (int) (rawAbsX / dotsPerPoint);
                    int y1 = (int) (rawAbsY / dotsPerPoint);
                    int x2 = (int) ((rawAbsX + rawWidth) / dotsPerPoint);
                    int y2 = (int) ((rawAbsY + rawHeight) / dotsPerPoint);

                    log.info("  Converted to POINTS: x1={}, y1={}, x2={}, y2={}", x1, y1, x2, y2);

                    // Calculate page number (Y in points / page height)
                    int pageNo = (y1 / pageHeightPts) + 1;

                    // Adjust Y to be relative to current page
                    int pageOffsetY = (pageNo - 1) * pageHeightPts;
                    int adjY1 = y1 - pageOffsetY;
                    int adjY2 = y2 - pageOffsetY;

                    log.info("  Page {}: adjusted Y1={}, Y2={}", pageNo, adjY1, adjY2);

                    positions.add(new ViewLinkPosition(i, docIndices.get(i), pageNo, x1, adjY1, x2, adjY2));
                } else {
                    log.warn("Could not find element with id: {}", elementId);
                }
            }

            log.info("=== End Coordinate Extraction ({} positions found) ===", positions.size());

        } catch (Exception e) {
            log.error("Error extracting view positions: {}", e.getMessage(), e);
        }

        return positions;
    }

    /**
     * Extracts the positions of view-N span elements from the Flying Saucer layout tree.
     * These positions are used to create hyperlink annotations in OmniDocs.
     *
     * @param renderer The ITextRenderer after layout() has been called
     * @param docIndices List of document indices corresponding to each row
     * @return List of ViewLinkPosition objects with coordinates for each view element
     */
    private List<ViewLinkPosition> extractViewPositions(org.xhtmlrenderer.pdf.ITextRenderer renderer, List<String> docIndices) {
        List<ViewLinkPosition> positions = new ArrayList<>();

        try {
            org.xhtmlrenderer.render.BlockBox rootBox = renderer.getRootBox();

            // Get page dimensions - A4 is 595 x 842 points
            float dotsPerPoint = renderer.getDotsPerPoint();

            // Find all elements with id starting with "view-"
            for (int i = 0; i < docIndices.size(); i++) {
                String elementId = "view-" + i;
                org.xhtmlrenderer.render.Box box = findBoxById(rootBox, elementId);

                if (box != null) {
                    // Get absolute position in document coordinates
                    int absX = box.getAbsX();
                    int absY = box.getAbsY();
                    int width = box.getWidth();
                    int height = box.getHeight();

                    // If width is 0 (inline element), try to get parent's width or use minimum
                    // "View" text is approximately 30 points wide, 15 points tall
                    if (width <= 0) {
                        // Try to get parent box (the TD cell)
                        org.xhtmlrenderer.render.Box parent = box.getParent();
                        if (parent != null && parent.getWidth() > 0) {
                            width = parent.getWidth();
                            absX = parent.getAbsX();
                        } else {
                            // Fallback: use minimum width for "View" text (30 points * dotsPerPoint)
                            width = (int) (30 * dotsPerPoint);
                        }
                    }
                    if (height <= 0) {
                        // Minimum height for text (15 points * dotsPerPoint)
                        height = (int) (15 * dotsPerPoint);
                    }

                    // Convert to PDF points (divide by dotsPerPoint)
                    int x1 = (int) (absX / dotsPerPoint);
                    int y1 = (int) (absY / dotsPerPoint);
                    int x2 = (int) ((absX + width) / dotsPerPoint);
                    int y2 = (int) ((absY + height) / dotsPerPoint);

                    // Determine which page this element is on
                    // Each page is typically 842 points (A4 height)
                    int pageHeight = 842; // A4 page height in points
                    int pageNo = (y1 / pageHeight) + 1;

                    // Adjust Y coordinates to be relative to the current page
                    int pageOffsetY = (pageNo - 1) * pageHeight;
                    y1 = y1 - pageOffsetY;
                    y2 = y2 - pageOffsetY;

                    positions.add(new ViewLinkPosition(i, docIndices.get(i), pageNo, x1, y1, x2, y2));
                    log.info("Found view element {} at page {}: ({}, {}) to ({}, {}) width={} height={}",
                            elementId, pageNo, x1, y1, x2, y2, x2-x1, y2-y1);
                } else {
                    log.warn("Could not find element with id: {}", elementId);
                }
            }
        } catch (Exception e) {
            log.error("Error extracting view positions from layout tree: {}", e.getMessage(), e);
        }

        return positions;
    }

    /**
     * Calculates View link positions using fixed layout coordinates.
     * Since we removed "View" text from HTML, we calculate positions based on:
     * - Fixed X position for the View column (from calibration)
     * - Y position calculated from row index and fixed row height
     *
     * Calibration data (from manual TT annotation on first View cell):
     * - OmniDocs X1 = 675 (center of View column)
     * - OmniDocs Y1 = 336 (first data row)
     *
     * @param pdfPath Path to the PDF file (used for page count)
     * @param docIndices List of document indices for each View link
     * @return List of ViewLinkPosition objects with calculated coordinates
     */
    private List<ViewLinkPosition> extractViewPositionsFromPdf(String pdfPath, List<String> docIndices) {
        List<ViewLinkPosition> positions = new ArrayList<>();

        // Fixed layout coordinates (calibrated from manual annotation testing)
        // These values are in OmniDocs coordinate system (origin top-left, Y increases downward)
        final int VIEW_X1 = 675;           // Left edge of "View" text position
        final int VIEW_WIDTH = 40;         // Width of hyperlink area
        final int VIEW_HEIGHT = 15;        // Height of hyperlink area
        final int FIRST_ROW_Y = 336;       // Y position of first data row
        final int ROW_HEIGHT = 30;         // Height between rows (calibrated)

        log.info("=== Fixed Position Calculation for View Links ===");
        log.info("Using calibrated coordinates: X1={}, FIRST_ROW_Y={}, ROW_HEIGHT={}",
                VIEW_X1, FIRST_ROW_Y, ROW_HEIGHT);
        log.info("Creating {} View link positions for documents", docIndices.size());

        for (int rowIndex = 0; rowIndex < docIndices.size(); rowIndex++) {
            int x1 = VIEW_X1;
            int y1 = FIRST_ROW_Y + (rowIndex * ROW_HEIGHT);
            int x2 = x1 + VIEW_WIDTH;
            int y2 = y1 + VIEW_HEIGHT;
            int pageNo = 1;  // Assuming all on page 1 for now

            log.info("Row {}: docIndex={}, coords: x1={}, y1={}, x2={}, y2={}",
                    rowIndex, docIndices.get(rowIndex), x1, y1, x2, y2);

            positions.add(new ViewLinkPosition(rowIndex, docIndices.get(rowIndex), pageNo, x1, y1, x2, y2));
        }

        log.info("=== End Fixed Position Calculation ({} positions) ===", positions.size());

        return positions;
    }

    /**
     * Recursively searches for a box with the given element ID in the layout tree.
     */
    private org.xhtmlrenderer.render.Box findBoxById(org.xhtmlrenderer.render.Box box, String id) {
        if (box == null) {
            return null;
        }

        // Check if this box has the target ID
        org.w3c.dom.Element element = box.getElement();
        if (element != null && id.equals(element.getAttribute("id"))) {
            return box;
        }

        // Recursively search children
        for (int i = 0; i < box.getChildCount(); i++) {
            org.xhtmlrenderer.render.Box child = box.getChild(i);
            org.xhtmlrenderer.render.Box found = findBoxById(child, id);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * Builds the OmniDocs annotation buffer for hyperlink annotations.
     * Format matches the NewgenOne viewer hyperlink annotation structure.
     *
     * @param positions List of ViewLinkPosition objects with coordinates
     * @param groupName The annotation group name (e.g., "ViewLinks")
     * @param userId The user ID for the annotations
     * @return The annotation buffer string in OmniDocs format
     */
    private String buildHyperlinkAnnotationBuffer(List<ViewLinkPosition> positions, String groupName, String userId) {
        StringBuilder buffer = new StringBuilder();

        // AnnotationBuffer format for OmniDocs - matches viewimageanno servlet format
        // Include [GroupNameAnnotationHeader] section followed by annotation counts and hyperlink sections
        buffer.append("[").append(groupName).append("AnnotationHeader]\n");
        buffer.append("TotalAnnotations=").append(positions.size()).append("\n");
        buffer.append("NoOfHyperlinks=").append(positions.size()).append("\n");

        // Each hyperlink annotation
        int index = 1;
        for (ViewLinkPosition pos : positions) {
            buffer.append("[").append(groupName).append("Hyperlink").append(index++).append("]\n");
            buffer.append("X1=").append(pos.x1).append("\n");
            buffer.append("Y1=").append(pos.y1).append("\n");
            buffer.append("X2=").append(pos.x2).append("\n");
            buffer.append("Y2=").append(pos.y2).append("\n");
            buffer.append("Color=11141120\n");
            buffer.append("TimeOrder=").append(getCurrentTimeOrder()).append("\n");
            buffer.append("MouseSensitivity=1\n");
            buffer.append("AnnotationGroupID=").append(groupName).append("\n");
            buffer.append("UserID=").append(userId).append("\n");
            buffer.append("Rights=VM\n");
            buffer.append("HyperlinkName=View\n");
            buffer.append("HyperlinkURL=http://google.com\n");
            buffer.append("Height=-15\n");
            buffer.append("Width=0\n");
            buffer.append("Escapement=0\n");
            buffer.append("Orientation=0\n");
            buffer.append("Weight=400\n");
            buffer.append("Italic=0\n");
            buffer.append("Underlined=0\n");
            buffer.append("StrikeOut=0\n");
            buffer.append("CharSet=0\n");
            buffer.append("OutPrecision=0\n");
            buffer.append("ClipPrecision=0\n");
            buffer.append("Quality=1\n");
            buffer.append("PitchAndFamily=49\n");
            buffer.append("FontName=Arial\n");
            buffer.append("FontColor=11141120\n");
        }

        return buffer.toString();
    }

    /**
     * Returns the current time in OmniDocs TimeOrder format: YYYY,MM,DD,HH,MM,SS
     */
    private String getCurrentTimeOrder() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        return String.format("%d,%02d,%02d,%02d,%02d,%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond());
    }

    /**
     * Loads HTML template from classpath resources.
     */
    private String loadTemplate(String templatePath) throws IOException {
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(templatePath)) {
            if (is == null) {
                log.warn("Template not found: {}, using default", templatePath);
                if (templatePath.contains("document_list_template")) {
                    return "<div style=\"margin-top:20px;\"><table border=\"1\" style=\"width:100%;\"><tr><td colspan=\"3\" style=\"background:#e0e0e0;\"><b>Supporting Documents</b></td></tr><tr style=\"background:#f0f0f0;font-weight:bold;\"><td style=\"width:5%;text-align:center;\">S.No</td><td style=\"width:70%;\">Document Name</td><td style=\"width:25%;\">Document Index</td></tr>{{DOCUMENT_ROWS}}</table></div>";
                } else if (templatePath.contains("document_row_template")) {
                    return "<tr><td style=\"text-align:center;\">{{SNO}}</td><td>{{DOCUMENT_NAME}}</td><td>{{DOCUMENT_INDEX}}</td></tr>";
                } else if (templatePath.contains("comment_template")) {
                    return "<div style=\"margin-top:20px;\"><table border=\"1\" style=\"width:100%;\"><tr><td colspan=\"3\" style=\"background:#e0e0e0;\"><b>Notesheet Comments</b></td></tr>{{COMMENTS_ROWS}}</table></div>";
                } else {
                    return "<tr><td><b>SNo:</b> {{SNO}}</td><td><b>User:</b> {{USER}}</td><td><b>Date:</b> {{DATE}}</td></tr><tr><td colspan=\"3\">{{COMMENT_TEXT}}</td></tr><tr><td><b>Stage:</b> {{STAGE}}</td><td><b>Status:</b> {{STATUS}}</td><td></td></tr>";
                }
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Renders comment rows using the row template.
     */
    private String renderCommentRows(JsonNode comments, String rowTemplate) {
        StringBuilder rows = new StringBuilder();
        int sno = 1;

        if (comments != null && comments.isArray()) {
            for (JsonNode comment : comments) {
                String row = rowTemplate
                        .replace("{{SNO}}", String.valueOf(sno++))
                        .replace("{{USER}}", comment.path("userName").asText(""))
                        .replace("{{DATE}}", comment.path("dateTime").asText(""))
                        .replace("{{COMMENT_TEXT}}", comment.path("comments").asText(""))
                        .replace("{{STAGE}}", comment.path("stage").asText(""))
                        .replace("{{STATUS}}", comment.path("status").asText(""));
                rows.append(row);
            }
        }

        return rows.toString();
    }

    /**
     * Renders document rows using the row template.
     * Format: S.NO. | Document Name (hyperlinked) | View
     * Skips documents whose name starts with "notesheet" (case-insensitive).
     *
     * @param documents JSON array of documents
     * @param rowTemplate HTML template for each row
     * @param viewerBaseUrl Base URL for document viewer (e.g., http://host:port/app/docs/viewer)
     * @return RenderDocumentResult containing rendered HTML and document indices for position tracking
     */
    private RenderDocumentResult renderDocumentRows(JsonNode documents, String rowTemplate, String viewerBaseUrl) {
        StringBuilder rows = new StringBuilder();
        List<String> docIndices = new ArrayList<>();
        int sno = 1;
        int rowIndex = 0;

        if (documents != null && documents.isArray()) {
            for (JsonNode doc : documents) {
                String docName = doc.path("documentName").asText("");
                // Skip documents starting with "notesheet"
                if (docName.toLowerCase().startsWith("notesheet")) {
                    continue;
                }
                String docIndex = doc.path("documentIndex").asText("");

                // Document name is plain text - hyperlinks are added via View column annotations
                String docNameHtml = escapeHtml(docName);

                String row = rowTemplate
                        .replace("{{SNO}}", String.valueOf(sno++))
                        .replace("{{DOCUMENT_NAME}}", docNameHtml)
                        .replace("{{DOCUMENT_INDEX}}", docIndex)
                        .replace("{{ROW_INDEX}}", String.valueOf(rowIndex));
                rows.append(row);
                docIndices.add(docIndex);
                rowIndex++;
            }
        }

        return new RenderDocumentResult(rows.toString(), docIndices);
    }

    /**
     * Result class for renderDocumentRows containing HTML and document indices.
     */
    private static class RenderDocumentResult {
        final String html;
        final List<String> docIndices;

        RenderDocumentResult(String html, List<String> docIndices) {
            this.html = html;
            this.docIndices = docIndices;
        }
    }

    /**
     * Holds position information for a View hyperlink annotation.
     */
    private static class ViewLinkPosition {
        final int rowIndex;
        final String docIndex;
        final int pageNo;
        final int x1, y1, x2, y2;  // PDF coordinates (origin at top-left for OmniDocs)

        ViewLinkPosition(int rowIndex, String docIndex, int pageNo, int x1, int y1, int x2, int y2) {
            this.rowIndex = rowIndex;
            this.docIndex = docIndex;
            this.pageNo = pageNo;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    /**
     * Result class for PDF generation with position tracking.
     */
    private static class PdfGenerationResult {
        final String pdfPath;
        final List<ViewLinkPosition> viewPositions;

        PdfGenerationResult(String pdfPath, List<ViewLinkPosition> viewPositions) {
            this.pdfPath = pdfPath;
            this.viewPositions = viewPositions;
        }
    }

    /**
     * Escapes HTML special characters to prevent XSS and ensure valid XHTML.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&#38;")
                .replace("<", "&#60;")
                .replace(">", "&#62;")
                .replace("\"", "&#34;")
                .replace("'", "&#39;");
    }
}
