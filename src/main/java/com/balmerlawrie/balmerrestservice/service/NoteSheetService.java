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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @Value("${ibps.server.host}")
    private String ibpsServerHost;

    @Value("${ibps.server.port}")
    private String ibpsServerPort;

    @Value("${ibps.cabinet.name}")
    private String ibpsCabinetName;

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

    @Value("${notesheet.font.directories:}")
    private String noteSheetFontDirectories;

    private static final Object PDF_FONT_CONFIG_LOCK = new Object();
    private static volatile boolean pdfFontsConfigured = false;

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
                return createNotFoundResponse(buildFetchAttributesFailureMessage(attributes));
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
     * Retrieves the original notesheet document using a caller-specified attribute name
     * instead of the default 'notesheet_original'. Used by TCR (attribute 'tcr_original').
     * Does not alter behavior of {@link #getOriginalNotesheet(String, String, long)}.
     */
    public JsonNode getOriginalNotesheet(String processInstanceId, String workitemId, long sessionId,
            String originalAttributeName) {
        log.info("Getting original notesheet (attribute='{}') for processInstanceId: {}, workitemId: {}",
                originalAttributeName, processInstanceId, workitemId);

        try {
            JsonNode attributes = fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
            if (attributes == null || attributes.has("error")) {
                return createNotFoundResponse(buildFetchAttributesFailureMessage(attributes));
            }

            String notesheetValue = extractAttributeValue(attributes, originalAttributeName);
            if (notesheetValue == null || notesheetValue.isEmpty()) {
                return createNotFoundResponse("No '" + originalAttributeName + "' attribute found in work item");
            }

            log.info("Found {} attribute: {}", originalAttributeName, notesheetValue);

            String[] parts = notesheetValue.split("#");
            if (parts.length < 3) {
                return createNotFoundResponse("Invalid " + originalAttributeName + " format: " + notesheetValue);
            }

            String folderIndex = parts[0];
            String versionNo = parts[1];
            String documentIndex = parts[2];

            byte[] documentContent = downloadDocument(documentIndex, sessionId);
            if (documentContent == null || documentContent.length == 0) {
                return createNotFoundResponse("Failed to download document content");
            }

            String documentName = originalAttributeName + "_" + processInstanceId;
            String filePath = saveToTempFile(documentContent, documentName);

            ObjectNode response = jsonMapper.createObjectNode();
            response.put("success", true);
            response.put("found", true);
            response.put("filePath", filePath);
            response.put("documentName", documentName);
            response.put("documentIndex", documentIndex);
            response.put("folderIndex", folderIndex);
            response.put("versionNo", versionNo);
            response.put("fileSize", documentContent.length);

            log.info("Successfully retrieved {} to: {}", originalAttributeName, filePath);
            return response;

        } catch (Exception e) {
            log.error("Error retrieving original notesheet (attribute='{}'): {}", originalAttributeName, e.getMessage(), e);
            return createErrorResponse("Error retrieving notesheet", e.getMessage());
        }
    }

    /**
     * Case-insensitive lookup of a work item attribute's value, handling the
     * common iBPS JSON shapes (direct text, empty-key "", or "content" key).
     */
    private String extractAttributeValue(JsonNode attributes, String attributeName) {
        JsonNode output = attributes.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) {
            output = attributes;
        }

        JsonNode attrs = output.path("Attributes");
        if (attrs.isMissingNode()) {
            attrs = output;
        }

        JsonNode node = findFieldIgnoreCase(attrs, attributeName);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String value = node.asText();
        if (value != null && !value.isEmpty() && !value.equals("null")) {
            return value;
        }
        value = node.path("").asText();
        if (value != null && !value.isEmpty()) {
            return value;
        }
        value = node.path("content").asText();
        if (value != null && !value.isEmpty()) {
            return value;
        }
        return null;
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
                return createNotFoundResponse(buildFetchAttributesFailureMessage(attributes));
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
            // Step 1: Fetch annotation groups across pages and pagination.
            final int maxPagesToScan = 500;
            final int emptyPagesToStop = 3;
            final int recordsPerFetch = 100;

            ArrayNode allGroups = jsonMapper.createArrayNode();
            Set<String> seenGroupKeys = new HashSet<>();
            int pageNo = 1;
            int consecutiveEmptyPages = 0;

            while (pageNo <= maxPagesToScan && consecutiveEmptyPages < emptyPagesToStop) {
                String previousAnnotationIndex = "0";
                int groupsFoundOnPage = 0;
                int fetchesForPage = 0;

                while (true) {
                    // Build NGOGetAnnotationGroupList input for current page + pagination index.
                    ObjectNode input = jsonMapper.createObjectNode();
                    input.put("Option", "NGOGetAnnotationGroupList");
                    input.put("CabinetName", cabinetName);
                    input.put("UserDBId", String.valueOf(sessionId));
                    input.put("DocumentIndex", documentIndex);
                    input.put("PageNo", String.valueOf(pageNo));
                    input.put("PreviousAnnotationIndex", previousAnnotationIndex);
                    // Omit VersionNo to get annotations for current/latest version.
                    input.put("SortOrder", "A");
                    input.put("NoOfRecordsToFetch", String.valueOf(recordsPerFetch));

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

                    log.info("Calling NGOGetAnnotationGroupList: docIndex={}, pageNo={}, previousAnnotationIndex={}",
                            documentIndex, pageNo, previousAnnotationIndex);
                    ResponseEntity<String> response = restTemplate.exchange(
                            omniDocsApiUrl, HttpMethod.POST, request, String.class);

                    JsonNode responseJson = parseXmlToJson(response.getBody());
                    JsonNode output = responseJson.path("NGOExecuteAPIResponseBDO").path("outputData")
                            .path("NGOGetAnnotationGroupList_Output");

                    if (output.isMissingNode() || output.path("Status").asInt() != 0) {
                        String error = output.path("Error").asText(output.path("Status").asText("Unknown Error"));
                        if (pageNo == 1 && allGroups.isEmpty()) {
                            log.error("OmniDocs Error on first page while getting annotations: {}", error);
                            return createErrorResponse("Failed to get annotations", error);
                        }
                        log.info("Stopping annotation scan at page {} due to status/error: {}", pageNo, error);
                        break;
                    }

                    int addedThisCall = appendAnnotationGroups(output.path("AnnotationGroups"), allGroups, seenGroupKeys);
                    groupsFoundOnPage += addedThisCall;
                    fetchesForPage++;

                    String nextAnnotationIndex = extractNextAnnotationIndex(output);
                    boolean hasMore = nextAnnotationIndex != null
                            && !nextAnnotationIndex.isBlank()
                            && !nextAnnotationIndex.equals(previousAnnotationIndex)
                            && !"0".equals(nextAnnotationIndex);

                    if (addedThisCall == 0 || !hasMore || fetchesForPage >= 50) {
                        break;
                    }
                    previousAnnotationIndex = nextAnnotationIndex;
                }

                if (groupsFoundOnPage == 0) {
                    consecutiveEmptyPages++;
                } else {
                    consecutiveEmptyPages = 0;
                }
                pageNo++;
            }

            // Keep response shape compatible: annotations -> AnnotationGroup (object or array).
            ObjectNode annotations = jsonMapper.createObjectNode();
            if (allGroups.size() == 1) {
                annotations.set("AnnotationGroup", allGroups.get(0));
            } else if (allGroups.size() > 1) {
                annotations.set("AnnotationGroup", allGroups);
            }
            log.info("Retrieved {} unique annotation groups for document {}", allGroups.size(), documentIndex);

            // Step 2: Save to temp file
            String fileName = "annotations_" + documentIndex + ".json";
            Path tempDir = Paths.get(tempDirectory.replace("notesheets", "annotations")); // Use distinct folder
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            String uniqueFileName = UUID.randomUUID().toString() + "_" + fileName;
            Path filePath = tempDir.resolve(uniqueFileName);

            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(annotations.toPrettyString().getBytes(StandardCharsets.UTF_8));
            }

            // Step 3: Build success response
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

    private int appendAnnotationGroups(JsonNode annotationGroupsNode, ArrayNode allGroups, Set<String> seenGroupKeys) {
        if (annotationGroupsNode == null || annotationGroupsNode.isMissingNode()
                || annotationGroupsNode.isNull() || annotationGroupsNode.isEmpty()) {
            return 0;
        }

        JsonNode groupsNode = annotationGroupsNode.path("AnnotationGroup");
        if (groupsNode.isMissingNode() || groupsNode.isNull()) {
            if (annotationGroupsNode.isArray()) {
                groupsNode = annotationGroupsNode;
            } else if (annotationGroupsNode.has("AnnotGroupName")) {
                groupsNode = annotationGroupsNode;
            } else {
                return 0;
            }
        }

        int added = 0;
        if (groupsNode.isArray()) {
            for (JsonNode group : groupsNode) {
                if (tryAddGroup(group, allGroups, seenGroupKeys)) {
                    added++;
                }
            }
        } else {
            if (tryAddGroup(groupsNode, allGroups, seenGroupKeys)) {
                added++;
            }
        }
        return added;
    }

    private boolean tryAddGroup(JsonNode group, ArrayNode allGroups, Set<String> seenGroupKeys) {
        if (group == null || !group.isObject()) {
            return false;
        }

        String groupName = group.path("AnnotGroupName").asText("");
        String pageNo = group.path("PageNo").asText("");
        String buffer = group.path("AnnotationBuffer").asText("");
        if (groupName.isBlank() && buffer.isBlank()) {
            return false;
        }

        String key = groupName + "|" + pageNo + "|" + Integer.toHexString(buffer.hashCode());
        if (!seenGroupKeys.add(key)) {
            return false;
        }

        allGroups.add(group);
        return true;
    }

    private String extractNextAnnotationIndex(JsonNode output) {
        String[] candidates = new String[] {
                output.path("NextAnnotationIndex").asText(""),
                output.path("PreviousAnnotationIndex").asText(""),
                output.path("LastAnnotationIndex").asText(""),
                output.path("AnnotationGroups").path("NextAnnotationIndex").asText(""),
                output.path("AnnotationGroups").path("PreviousAnnotationIndex").asText(""),
                output.path("AnnotationGroups").path("LastAnnotationIndex").asText("")
        };

        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "";
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
        java.util.Map<String, JsonNode> historyAttrs = new java.util.LinkedHashMap<>();
        java.util.Iterator<String> names = attributes.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name != null && name.toLowerCase().endsWith("commentshistory")) {
                JsonNode node = attributes.path(name);
                if (!node.isMissingNode()) {
                    historyAttrs.put(name.toLowerCase(), node);
                }
            }
        }

        for (JsonNode historyAttr : historyAttrs.values()) {
            appendCommentsFromNode(historyAttr, commentsList, mapper);
        }

        result.put("success", true);
        result.put("count", commentsList.size());

        return result;
    }

    private JsonNode extractCommentsFromAttributes(JsonNode attributesResponse) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode commentsList = result.putArray("comments");

        JsonNode output = attributesResponse.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) output = attributesResponse;

        JsonNode attributes = output.path("Attributes");
        if (!attributes.isMissingNode()) {
            java.util.Map<String, JsonNode> historyAttrs = new java.util.LinkedHashMap<>();
            java.util.Iterator<String> names = attributes.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (name != null && name.toLowerCase().endsWith("commentshistory")) {
                    JsonNode node = attributes.path(name);
                    if (!node.isMissingNode()) historyAttrs.put(name.toLowerCase(), node);
                }
            }
            for (JsonNode historyAttr : historyAttrs.values()) {
                appendCommentsFromNode(historyAttr, commentsList, mapper);
            }
        }

        result.put("success", true);
        result.put("count", commentsList.size());
        return result;
    }

    /**
     * TCR variant of {@link #extractCommentsFromAttributes}: reads only the
     * 'Q_tcr_comments' attribute instead of scanning for any 'commentshistory'-suffixed attribute.
     */
    private JsonNode extractTcrCommentsFromAttributes(JsonNode attributesResponse) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode commentsList = result.putArray("comments");

        JsonNode output = attributesResponse.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) output = attributesResponse;

        JsonNode attributes = output.path("Attributes");
        if (!attributes.isMissingNode()) {
            JsonNode tcrCommentsNode = findFieldIgnoreCase(attributes, "Q_tcr_comments");
            if (tcrCommentsNode != null) {
                appendCommentsFromNode(tcrCommentsNode, commentsList, mapper);
            }
        }

        result.put("success", true);
        result.put("count", commentsList.size());
        return result;
    }

    /**
     * Corrigendum variant of {@link #extractCommentsFromAttributes}: reads only the
     * 'Q_duedatecomments' attribute instead of scanning for any 'commentshistory'-suffixed attribute.
     */
    private JsonNode extractCorrigendumCommentsFromAttributes(JsonNode attributesResponse) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode commentsList = result.putArray("comments");

        JsonNode output = attributesResponse.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) output = attributesResponse;

        JsonNode attributes = output.path("Attributes");
        if (!attributes.isMissingNode()) {
            JsonNode corrigendumCommentsNode = findFieldIgnoreCase(attributes, "Q_duedatecomments");
            if (corrigendumCommentsNode != null) {
                appendCommentsFromNode(corrigendumCommentsNode, commentsList, mapper);
            }
        }

        result.put("success", true);
        result.put("count", commentsList.size());
        return result;
    }

    /**
     * Pricebidopening variant of {@link #extractCommentsFromAttributes}: reads only the
     * 'Q_pricebid_comments' attribute instead of scanning for any 'commentshistory'-suffixed attribute.
     */
    private JsonNode extractPricebidopeningCommentsFromAttributes(JsonNode attributesResponse) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode result = mapper.createObjectNode();
        ArrayNode commentsList = result.putArray("comments");

        JsonNode output = attributesResponse.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) output = attributesResponse;

        JsonNode attributes = output.path("Attributes");
        if (!attributes.isMissingNode()) {
            JsonNode pricebidCommentsNode = findFieldIgnoreCase(attributes, "Q_pricebid_comments");
            if (pricebidCommentsNode != null) {
                appendCommentsFromNode(pricebidCommentsNode, commentsList, mapper);
            }
        }

        result.put("success", true);
        result.put("count", commentsList.size());
        return result;
    }

    private String getValue(JsonNode parent, String key) {
        if (parent == null || !parent.isObject()) {
            return "";
        }

        JsonNode node = findFieldIgnoreCase(parent, key);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }

        // iBPS attributes often have text in "" or content/#text keys
        JsonNode emptyKeyValue = node.path("");
        if (!emptyKeyValue.isMissingNode() && !emptyKeyValue.isNull()) {
            return emptyKeyValue.asText("");
        }

        JsonNode contentValue = node.path("content");
        if (!contentValue.isMissingNode() && !contentValue.isNull()) {
            return contentValue.asText("");
        }

        JsonNode textValue = node.path("#text");
        if (!textValue.isMissingNode() && !textValue.isNull()) {
            return textValue.asText("");
        }

        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText("");
        }

        return "";
    }

    private void appendCommentsFromNode(JsonNode node, ArrayNode commentsList, ObjectMapper mapper) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                appendCommentsFromNode(item, commentsList, mapper);
            }
            return;
        }

        if (node.isTextual()) {
            String text = node.asText("").trim();
            if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
                try {
                    JsonNode parsed = mapper.readTree(text);
                    appendCommentsFromNode(parsed, commentsList, mapper);
                } catch (Exception ignored) {
                    // Non-JSON text; ignore
                }
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        String userName = getValue(node, "username");
        String userId = getValue(node, "userid");
        String dateTime = getValue(node, "datetime");
        String comments = getValue(node, "comments");
        String stage = getValue(node, "stagename");
        String status = getValue(node, "email");
        String actionTaken = getValue(node, "actiontaken");
        if (actionTaken == null || actionTaken.isEmpty()) {
            actionTaken = status;
        }

        boolean looksLikeComment = !(isBlank(userName) && isBlank(dateTime) && isBlank(comments) && isBlank(stage)
                && isBlank(actionTaken));
        if (looksLikeComment) {
            ObjectNode comment = mapper.createObjectNode();
            comment.put("userName", userName);
            comment.put("userId", userId);
            comment.put("dateTime", dateTime);
            comment.put("comments", comments);
            comment.put("stage", stage);
            comment.put("status", status);
            comment.put("actionTaken", actionTaken);
            commentsList.add(comment);
        }

        java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            java.util.Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode value = field.getValue();
            if (name == null || value == null || !value.isContainerNode()) {
                continue;
            }
            appendCommentsFromNode(value, commentsList, mapper);
        }
    }

    private JsonNode findFieldIgnoreCase(JsonNode node, String targetKey) {
        if (node == null || !node.isObject() || targetKey == null) {
            return null;
        }
        java.util.Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (targetKey.equalsIgnoreCase(name)) {
                return node.path(name);
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
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

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("iBPS returned HTTP {}, parsing error body: {}",
                    e.getStatusCode(), responseBody.substring(0, Math.min(300, responseBody.length())));
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
     * Builds an actionable failure message from fetchWorkItemAttributes response.
     */
    private String buildFetchAttributesFailureMessage(JsonNode attributes) {
        String base = "Failed to fetch work item attributes";
        if (attributes == null) {
            return base;
        }

        String error = attributes.path("error").asText("");
        String details = attributes.path("details").asText("");
        StringBuilder sb = new StringBuilder(base);

        if (!error.isEmpty()) {
            sb.append(": ").append(error);
        }
        if (!details.isEmpty()) {
            if (!error.isEmpty()) {
                sb.append(" - ");
            } else {
                sb.append(": ");
            }
            sb.append(details);
        }

        return sb.toString();
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
     * Uses Aspose.PDF to render annotations onto the document.
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

            // Step 3: Load PDF with Aspose
            com.aspose.pdf.Document pdfDoc = new com.aspose.pdf.Document(
                    new java.io.ByteArrayInputStream(documentContent));
            try {
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
                    int pageNo = group.path("PageNo").asInt(1); // 1-based for Aspose
                    String buffer = group.path("AnnotationBuffer").asText("");

                    if (buffer.isEmpty() || pageNo < 1 || pageNo > pdfDoc.getPages().size()) {
                        continue;
                    }

                    com.aspose.pdf.Page page = pdfDoc.getPages().get_Item(pageNo);
                    float pageHeight = (float) page.getRect().getHeight();

                    // Parse and render annotations from buffer
                    renderAnnotationsFromBuffer(pdfDoc, page, buffer, pageHeight);
                }

                // Step 5: Write to byte array
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                pdfDoc.save(baos);
                log.info("Generated PDF with annotations: {} bytes", baos.size());
                return baos.toByteArray();
            } finally {
                pdfDoc.close();
            }

        } catch (Exception e) {
            log.error("Error downloading document with annotations: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Parses annotation buffer and renders annotations onto a PDF page using Aspose.
     * Annotation buffer format is INI-style with sections like [GroupNameAnnotation1]
     */
    private void renderAnnotationsFromBuffer(com.aspose.pdf.Document doc,
            com.aspose.pdf.Page page, String buffer, float pageHeight) {

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
                    renderAnnotation(doc, page, currentSectionName, currentSection, pageHeight);
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
            renderAnnotation(doc, page, currentSectionName, currentSection, pageHeight);
        }
    }

    /**
     * Renders a single annotation based on its type using Aspose.PDF annotations.
     */
    private void renderAnnotation(com.aspose.pdf.Document doc, com.aspose.pdf.Page page,
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
            }

            // Get coordinates (OmniDocs: Y from top; Aspose: Y from bottom)
            float x1 = parseFloat(props.get("X1"), 0);
            float y1 = parseFloat(props.get("Y1"), 0);
            float x2 = parseFloat(props.get("X2"), 0);
            float y2 = parseFloat(props.get("Y2"), 0);

            // Convert OmniDocs coordinates to Aspose coordinates
            float scale = pageHeight / 1040f;
            float pdfX1 = x1 * scale;
            float pdfY1 = pageHeight - (y1 * scale);
            float pdfX2 = x2 * scale;
            float pdfY2 = pageHeight - (y2 * scale);

            // Parse color (OmniDocs stores as integer)
            int colorInt = parseInt(props.get("Color"), 0);
            int r = (colorInt >> 16) & 0xFF;
            int g = (colorInt >> 8) & 0xFF;
            int b = colorInt & 0xFF;
            com.aspose.pdf.Color color = com.aspose.pdf.Color.fromRgb(
                    new java.awt.Color(r, g, b));

            com.aspose.pdf.Rectangle rect = new com.aspose.pdf.Rectangle(
                    Math.min(pdfX1, pdfX2), Math.min(pdfY1, pdfY2),
                    Math.max(pdfX1, pdfX2), Math.max(pdfY1, pdfY2));

            switch (type) {
                case "LINE":
                    com.aspose.pdf.LineAnnotation lineAnnot = new com.aspose.pdf.LineAnnotation(
                            page, rect,
                            new com.aspose.pdf.Point(pdfX1, pdfY1),
                            new com.aspose.pdf.Point(pdfX2, pdfY2));
                    lineAnnot.setColor(color);
                    page.getAnnotations().add(lineAnnot);
                    break;

                case "BOX":
                    com.aspose.pdf.SquareAnnotation boxAnnot = new com.aspose.pdf.SquareAnnotation(page, rect);
                    boxAnnot.setColor(color);
                    page.getAnnotations().add(boxAnnot);
                    break;

                case "HIGHLIGHT":
                    com.aspose.pdf.HighlightAnnotation hlAnnot = new com.aspose.pdf.HighlightAnnotation(page, rect);
                    hlAnnot.setColor(color);
                    page.getAnnotations().add(hlAnnot);
                    break;

                case "HYPERLINK":
                    String linkName = props.getOrDefault("HyperlinkName", "View");
                    String linkUrl = props.getOrDefault("HyperlinkURL", "");
                    // Add as a link annotation with text
                    com.aspose.pdf.LinkAnnotation linkAnnot = new com.aspose.pdf.LinkAnnotation(page, rect);
                    if (!linkUrl.isEmpty()) {
                        linkAnnot.setAction(new com.aspose.pdf.GoToURIAction(linkUrl));
                    }
                    linkAnnot.setColor(com.aspose.pdf.Color.getBlue());
                    page.getAnnotations().add(linkAnnot);
                    // Add visible text stamp for the hyperlink label
                    com.aspose.pdf.TextStamp textStamp = new com.aspose.pdf.TextStamp(linkName);
                    textStamp.setXIndent(pdfX1);
                    textStamp.setYIndent(Math.min(pdfY1, pdfY2));
                    textStamp.getTextState().setFontSize(10);
                    textStamp.getTextState().setForegroundColor(com.aspose.pdf.Color.getBlue());
                    page.addStamp(textStamp);
                    break;

                case "TEXTSTAMP":
                    String text = props.getOrDefault("StampText", "");
                    if (!text.isEmpty()) {
                        com.aspose.pdf.TextStamp stamp = new com.aspose.pdf.TextStamp(text);
                        stamp.setXIndent(pdfX1);
                        stamp.setYIndent(Math.min(pdfY1, pdfY2));
                        stamp.getTextState().setFontSize(10);
                        stamp.getTextState().setForegroundColor(color);
                        page.addStamp(stamp);
                    }
                    break;

                default:
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
        return createPdfNoteInternal(processInstanceId, workitemId, sessionId, "");
    }

    /**
     * Creates a PDF note with an extra HTML section inserted before Supporting Documents.
     * Used by legal print flow to include case details in the PDF.
     */
    public JsonNode createPdfNoteWithExtraSection(String processInstanceId, String workitemId, long sessionId,
            String extraSectionHtml) {
        return createPdfNoteInternal(processInstanceId, workitemId, sessionId, extraSectionHtml);
    }

    /**
     * TCR variant of {@link #createPdfNote(String, String, long)}: reads the original document
     * from the 'tcr_original' attribute (instead of 'notesheet_original') and checks the generated
     * PDF in against the document referenced by the 'tcr' attribute (instead of 'notesheet').
     * Does not modify or share mutable state with {@link #createPdfNoteInternal}.
     */
    public JsonNode createTcrPdfNote(String processInstanceId, String workitemId, long sessionId) {
        log.info("Creating TCR PDF note for processInstanceId: {}, workitemId: {}", processInstanceId, workitemId);
        final long totalStartNanos = System.nanoTime();
        String uniqueId = UUID.randomUUID().toString();
        List<Path> tempFilesToCleanup = new ArrayList<>();

        try {
            long stepStartNanos = System.nanoTime();
            log.info("Step 1: Fetching work item attributes...");
            JsonNode attributes = fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
            if (attributes == null || attributes.has("error")) {
                return createErrorResponse("Failed to get work item attributes",
                        buildFetchAttributesFailureMessage(attributes));
            }
            log.info("Step 1 completed in {} ms", elapsedMs(stepStartNanos));

            // Extract tcr_original attribute (FolderIndex#VersionNo#DocumentIndex)
            String tcrOriginalValue = extractAttributeValue(attributes, "tcr_original");
            if (tcrOriginalValue == null || tcrOriginalValue.isEmpty()) {
                return createErrorResponse("Failed to get original TCR notesheet", "tcr_original attribute not found");
            }
            String[] origParts = tcrOriginalValue.split("#");
            if (origParts.length < 3) {
                return createErrorResponse("Failed to get original TCR notesheet",
                        "Invalid tcr_original format: " + tcrOriginalValue);
            }
            String originalDocIndex = origParts[2];
            log.info("originalDocIndex: {}", originalDocIndex);

            // Extract tcr attribute (FolderIndex#VersionNo#DocumentIndex) - check-in target
            String tcrValue = extractAttributeValue(attributes, "tcr");
            if (tcrValue == null || tcrValue.isEmpty()) {
                return createErrorResponse("Failed to get TCR notesheet", "tcr attribute not found");
            }
            String[] notesParts = tcrValue.split("#");
            if (notesParts.length < 3) {
                return createErrorResponse("Failed to get TCR notesheet",
                        "Invalid tcr format: " + tcrValue);
            }
            String notedocumentIndex = notesParts[2];
            log.info("notedocumentIndex: {}", notedocumentIndex);

            // Extract comments from the 'Q_tcr_comments' attribute (no extra iBPS call)
            JsonNode commentsResult = extractTcrCommentsFromAttributes(attributes);
            String commentsPath = saveCommentsToFile(commentsResult, uniqueId);
            log.info("Comments extracted and saved to: {}", commentsPath);

            // Step 2 (parallel): Download original TCR doc + get supporting documents concurrently
            stepStartNanos = System.nanoTime();
            log.info("Step 2: Downloading original document and fetching supporting docs in parallel...");
            final long finalSessionId = sessionId;
            final String finalOriginalDocIndex = originalDocIndex;
            java.util.concurrent.CompletableFuture<byte[]> docFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            downloadDocument(finalOriginalDocIndex, finalSessionId));
            java.util.concurrent.CompletableFuture<JsonNode> supportingDocsFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            supportingDocsService.getSupportingDocuments(processInstanceId, workitemId, finalSessionId));

            byte[] documentContent = docFuture.get();
            if (documentContent == null || documentContent.length == 0) {
                return createErrorResponse("Failed to get original TCR notesheet", "Document download returned empty content");
            }
            String htmlFilePath = saveToTempFile(documentContent, "tcr_original_" + processInstanceId);
            tempFilesToCleanup.add(Paths.get(htmlFilePath));

            JsonNode supportingDocsResult = supportingDocsFuture.get();
            JsonNode documentsArray = supportingDocsResult.path("documents");
            int docCount = supportingDocsResult.path("count").asInt(0);
            log.info("Step 2 completed in {} ms — original doc: {} bytes, supporting docs: {}",
                    elapsedMs(stepStartNanos), documentContent.length, docCount);

            // Step 3: Generate PDF with documents, comments, and track View positions
            stepStartNanos = System.nanoTime();
            log.info("Step 3: Generating PDF with documents, comments, and position tracking...");
            JsonNode commentsArray = commentsResult.path("comments");
            PdfGenerationResult pdfResult = generatePdfWithPositions(
                    htmlFilePath, documentsArray, commentsArray, uniqueId, "");
            String pdfPath = pdfResult.pdfPath;
            log.info("PDF generated at: {}", pdfPath);
            log.info("Step 3 completed in {} ms", elapsedMs(stepStartNanos));

            tempFilesToCleanup.add(Paths.get(pdfPath));
            tempFilesToCleanup.add(Paths.get(commentsPath));
            Path debugHtml = Paths.get(tempDirectory).resolve("debug-" + uniqueId + ".html");
            if (Files.exists(debugHtml)) {
                tempFilesToCleanup.add(debugHtml);
            }

            // Step 4: Check the generated PDF in against the 'tcr' document
            stepStartNanos = System.nanoTime();
            log.info("Step 4: Checking in PDF to TCR document...");
            JsonNode updateResult = documentOpsService.checkoutCheckinWithAnnotations(
                    notedocumentIndex, pdfPath, sessionId, true, originalDocIndex);

            if (!updateResult.path("success").asBoolean(false)) {
                return createErrorResponse("Failed to update TCR notesheet",
                        updateResult.path("error").asText("Update failed"));
            }
            log.info("Step 4 completed in {} ms", elapsedMs(stepStartNanos));

            ObjectNode result = jsonMapper.createObjectNode();
            result.put("success", true);
            result.put("originalDocIndex", originalDocIndex);
            result.put("notedocumentIndex", notedocumentIndex);
            result.put("newVersion", updateResult.path("newVersion").asText());
            result.put("pdfPath", pdfPath);
            result.put("commentsPath", commentsPath);
            result.put("annotationsPreserved", updateResult.path("annotationsRestored").asBoolean(false));
            result.put("durationMs", elapsedMs(totalStartNanos));

            log.info("TCR PDF note created successfully. New version: {}", updateResult.path("newVersion").asText());
            log.info("createTcrPdfNote total duration: {} ms", elapsedMs(totalStartNanos));
            return result;

        } catch (Exception e) {
            log.error("Error creating TCR PDF note: {}", e.getMessage(), e);
            return createErrorResponse("Error creating TCR PDF note", e.getMessage());
        } finally {
            log.info("createTcrPdfNote finished (including failure/cleanup) in {} ms", elapsedMs(totalStartNanos));
            cleanupTempFiles(tempFilesToCleanup);
        }
    }

    /**
     * Corrigendum variant of {@link #createPdfNote(String, String, long)}: reads the original document
     * from the 'corrigendum_original' attribute (instead of 'notesheet_original') and checks the generated
     * PDF in against the document referenced by the 'corrigendum' attribute (instead of 'notesheet').
     * Comments are embedded from the 'Q_duedatecomments' attribute.
     * Does not modify or share mutable state with {@link #createPdfNoteInternal}.
     */
    public JsonNode createCorrigendumPdfNote(String processInstanceId, String workitemId, long sessionId) {
        log.info("Creating Corrigendum PDF note for processInstanceId: {}, workitemId: {}", processInstanceId, workitemId);
        final long totalStartNanos = System.nanoTime();
        String uniqueId = UUID.randomUUID().toString();
        List<Path> tempFilesToCleanup = new ArrayList<>();

        try {
            long stepStartNanos = System.nanoTime();
            log.info("Step 1: Fetching work item attributes...");
            JsonNode attributes = fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
            if (attributes == null || attributes.has("error")) {
                return createErrorResponse("Failed to get work item attributes",
                        buildFetchAttributesFailureMessage(attributes));
            }
            log.info("Step 1 completed in {} ms", elapsedMs(stepStartNanos));

            // Extract corrigendum_original attribute (FolderIndex#VersionNo#DocumentIndex)
            String corrigendumOriginalValue = extractAttributeValue(attributes, "corrigendum_original");
            if (corrigendumOriginalValue == null || corrigendumOriginalValue.isEmpty()) {
                return createErrorResponse("Failed to get original Corrigendum notesheet", "corrigendum_original attribute not found");
            }
            String[] origParts = corrigendumOriginalValue.split("#");
            if (origParts.length < 3) {
                return createErrorResponse("Failed to get original Corrigendum notesheet",
                        "Invalid corrigendum_original format: " + corrigendumOriginalValue);
            }
            String originalDocIndex = origParts[2];
            log.info("originalDocIndex: {}", originalDocIndex);

            // Extract corrigendum attribute (FolderIndex#VersionNo#DocumentIndex) - check-in target
            String corrigendumValue = extractAttributeValue(attributes, "corrigendum");
            if (corrigendumValue == null || corrigendumValue.isEmpty()) {
                return createErrorResponse("Failed to get Corrigendum notesheet", "corrigendum attribute not found");
            }
            String[] notesParts = corrigendumValue.split("#");
            if (notesParts.length < 3) {
                return createErrorResponse("Failed to get Corrigendum notesheet",
                        "Invalid corrigendum format: " + corrigendumValue);
            }
            String notedocumentIndex = notesParts[2];
            log.info("notedocumentIndex: {}", notedocumentIndex);

            // Extract comments from the 'Q_duedatecomments' attribute (no extra iBPS call)
            JsonNode commentsResult = extractCorrigendumCommentsFromAttributes(attributes);
            String commentsPath = saveCommentsToFile(commentsResult, uniqueId);
            log.info("Comments extracted and saved to: {}", commentsPath);

            // Step 2 (parallel): Download original Corrigendum doc + get supporting documents concurrently
            stepStartNanos = System.nanoTime();
            log.info("Step 2: Downloading original document and fetching supporting docs in parallel...");
            final long finalSessionId = sessionId;
            final String finalOriginalDocIndex = originalDocIndex;
            java.util.concurrent.CompletableFuture<byte[]> docFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            downloadDocument(finalOriginalDocIndex, finalSessionId));
            java.util.concurrent.CompletableFuture<JsonNode> supportingDocsFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            supportingDocsService.getSupportingDocuments(processInstanceId, workitemId, finalSessionId));

            byte[] documentContent = docFuture.get();
            if (documentContent == null || documentContent.length == 0) {
                return createErrorResponse("Failed to get original Corrigendum notesheet", "Document download returned empty content");
            }
            String htmlFilePath = saveToTempFile(documentContent, "corrigendum_original_" + processInstanceId);
            tempFilesToCleanup.add(Paths.get(htmlFilePath));

            JsonNode supportingDocsResult = supportingDocsFuture.get();
            JsonNode documentsArray = supportingDocsResult.path("documents");
            int docCount = supportingDocsResult.path("count").asInt(0);
            log.info("Step 2 completed in {} ms — original doc: {} bytes, supporting docs: {}",
                    elapsedMs(stepStartNanos), documentContent.length, docCount);

            // Step 3: Generate PDF with documents, comments, and track View positions
            stepStartNanos = System.nanoTime();
            log.info("Step 3: Generating PDF with documents, comments, and position tracking...");
            JsonNode commentsArray = commentsResult.path("comments");
            PdfGenerationResult pdfResult = generatePdfWithPositions(
                    htmlFilePath, documentsArray, commentsArray, uniqueId, "");
            String pdfPath = pdfResult.pdfPath;
            log.info("PDF generated at: {}", pdfPath);
            log.info("Step 3 completed in {} ms", elapsedMs(stepStartNanos));

            tempFilesToCleanup.add(Paths.get(pdfPath));
            tempFilesToCleanup.add(Paths.get(commentsPath));
            Path debugHtml = Paths.get(tempDirectory).resolve("debug-" + uniqueId + ".html");
            if (Files.exists(debugHtml)) {
                tempFilesToCleanup.add(debugHtml);
            }

            // Step 4: Check the generated PDF in against the 'corrigendum' document
            stepStartNanos = System.nanoTime();
            log.info("Step 4: Checking in PDF to Corrigendum document...");
            JsonNode updateResult = documentOpsService.checkoutCheckinWithAnnotations(
                    notedocumentIndex, pdfPath, sessionId, true, originalDocIndex);

            if (!updateResult.path("success").asBoolean(false)) {
                return createErrorResponse("Failed to update Corrigendum notesheet",
                        updateResult.path("error").asText("Update failed"));
            }
            log.info("Step 4 completed in {} ms", elapsedMs(stepStartNanos));

            ObjectNode result = jsonMapper.createObjectNode();
            result.put("success", true);
            result.put("originalDocIndex", originalDocIndex);
            result.put("notedocumentIndex", notedocumentIndex);
            result.put("newVersion", updateResult.path("newVersion").asText());
            result.put("pdfPath", pdfPath);
            result.put("commentsPath", commentsPath);
            result.put("annotationsPreserved", updateResult.path("annotationsRestored").asBoolean(false));
            result.put("durationMs", elapsedMs(totalStartNanos));

            log.info("Corrigendum PDF note created successfully. New version: {}", updateResult.path("newVersion").asText());
            log.info("createCorrigendumPdfNote total duration: {} ms", elapsedMs(totalStartNanos));
            return result;

        } catch (Exception e) {
            log.error("Error creating Corrigendum PDF note: {}", e.getMessage(), e);
            return createErrorResponse("Error creating Corrigendum PDF note", e.getMessage());
        } finally {
            log.info("createCorrigendumPdfNote finished (including failure/cleanup) in {} ms", elapsedMs(totalStartNanos));
            cleanupTempFiles(tempFilesToCleanup);
        }
    }

    /**
     * Pricebidopening variant of {@link #createPdfNote(String, String, long)}: reads the original document
     * from the 'pricebidopening_original' attribute (instead of 'notesheet_original') and checks the generated
     * PDF in against the document referenced by the 'pricebidopening' attribute (instead of 'notesheet').
     * Comments are embedded from the 'Q_pricebid_comments' attribute.
     * Does not modify or share mutable state with {@link #createPdfNoteInternal}.
     */
    public JsonNode createPricebidopeningPdfNote(String processInstanceId, String workitemId, long sessionId) {
        log.info("Creating Pricebidopening PDF note for processInstanceId: {}, workitemId: {}", processInstanceId, workitemId);
        final long totalStartNanos = System.nanoTime();
        String uniqueId = UUID.randomUUID().toString();
        List<Path> tempFilesToCleanup = new ArrayList<>();

        try {
            long stepStartNanos = System.nanoTime();
            log.info("Step 1: Fetching work item attributes...");
            JsonNode attributes = fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
            if (attributes == null || attributes.has("error")) {
                return createErrorResponse("Failed to get work item attributes",
                        buildFetchAttributesFailureMessage(attributes));
            }
            log.info("Step 1 completed in {} ms", elapsedMs(stepStartNanos));

            // Extract pricebidopening_original attribute (FolderIndex#VersionNo#DocumentIndex)
            String pricebidOriginalValue = extractAttributeValue(attributes, "pricebidopening_original");
            if (pricebidOriginalValue == null || pricebidOriginalValue.isEmpty()) {
                return createErrorResponse("Failed to get original Pricebidopening notesheet", "pricebidopening_original attribute not found");
            }
            String[] origParts = pricebidOriginalValue.split("#");
            if (origParts.length < 3) {
                return createErrorResponse("Failed to get original Pricebidopening notesheet",
                        "Invalid pricebidopening_original format: " + pricebidOriginalValue);
            }
            String originalDocIndex = origParts[2];
            log.info("originalDocIndex: {}", originalDocIndex);

            // Extract pricebidopening attribute (FolderIndex#VersionNo#DocumentIndex) - check-in target
            String pricebidValue = extractAttributeValue(attributes, "pricebidopening");
            if (pricebidValue == null || pricebidValue.isEmpty()) {
                return createErrorResponse("Failed to get Pricebidopening notesheet", "pricebidopening attribute not found");
            }
            String[] notesParts = pricebidValue.split("#");
            if (notesParts.length < 3) {
                return createErrorResponse("Failed to get Pricebidopening notesheet",
                        "Invalid pricebidopening format: " + pricebidValue);
            }
            String notedocumentIndex = notesParts[2];
            log.info("notedocumentIndex: {}", notedocumentIndex);

            // Extract comments from the 'Q_pricebid_comments' attribute (no extra iBPS call)
            JsonNode commentsResult = extractPricebidopeningCommentsFromAttributes(attributes);
            String commentsPath = saveCommentsToFile(commentsResult, uniqueId);
            log.info("Comments extracted and saved to: {}", commentsPath);

            // Step 2 (parallel): Download original Pricebidopening doc + get supporting documents concurrently
            stepStartNanos = System.nanoTime();
            log.info("Step 2: Downloading original document and fetching supporting docs in parallel...");
            final long finalSessionId = sessionId;
            final String finalOriginalDocIndex = originalDocIndex;
            java.util.concurrent.CompletableFuture<byte[]> docFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            downloadDocument(finalOriginalDocIndex, finalSessionId));
            java.util.concurrent.CompletableFuture<JsonNode> supportingDocsFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            supportingDocsService.getSupportingDocuments(processInstanceId, workitemId, finalSessionId));

            byte[] documentContent = docFuture.get();
            if (documentContent == null || documentContent.length == 0) {
                return createErrorResponse("Failed to get original Pricebidopening notesheet", "Document download returned empty content");
            }
            String htmlFilePath = saveToTempFile(documentContent, "pricebidopening_original_" + processInstanceId);
            tempFilesToCleanup.add(Paths.get(htmlFilePath));

            JsonNode supportingDocsResult = supportingDocsFuture.get();
            JsonNode documentsArray = supportingDocsResult.path("documents");
            int docCount = supportingDocsResult.path("count").asInt(0);
            log.info("Step 2 completed in {} ms — original doc: {} bytes, supporting docs: {}",
                    elapsedMs(stepStartNanos), documentContent.length, docCount);

            // Step 3: Generate PDF with documents, comments, and track View positions
            stepStartNanos = System.nanoTime();
            log.info("Step 3: Generating PDF with documents, comments, and position tracking...");
            JsonNode commentsArray = commentsResult.path("comments");
            PdfGenerationResult pdfResult = generatePdfWithPositions(
                    htmlFilePath, documentsArray, commentsArray, uniqueId, "");
            String pdfPath = pdfResult.pdfPath;
            log.info("PDF generated at: {}", pdfPath);
            log.info("Step 3 completed in {} ms", elapsedMs(stepStartNanos));

            tempFilesToCleanup.add(Paths.get(pdfPath));
            tempFilesToCleanup.add(Paths.get(commentsPath));
            Path debugHtml = Paths.get(tempDirectory).resolve("debug-" + uniqueId + ".html");
            if (Files.exists(debugHtml)) {
                tempFilesToCleanup.add(debugHtml);
            }

            // Step 4: Check the generated PDF in against the 'pricebidopening' document
            stepStartNanos = System.nanoTime();
            log.info("Step 4: Checking in PDF to Pricebidopening document...");
            JsonNode updateResult = documentOpsService.checkoutCheckinWithAnnotations(
                    notedocumentIndex, pdfPath, sessionId, true, originalDocIndex);

            if (!updateResult.path("success").asBoolean(false)) {
                return createErrorResponse("Failed to update Pricebidopening notesheet",
                        updateResult.path("error").asText("Update failed"));
            }
            log.info("Step 4 completed in {} ms", elapsedMs(stepStartNanos));

            ObjectNode result = jsonMapper.createObjectNode();
            result.put("success", true);
            result.put("originalDocIndex", originalDocIndex);
            result.put("notedocumentIndex", notedocumentIndex);
            result.put("newVersion", updateResult.path("newVersion").asText());
            result.put("pdfPath", pdfPath);
            result.put("commentsPath", commentsPath);
            result.put("annotationsPreserved", updateResult.path("annotationsRestored").asBoolean(false));
            result.put("durationMs", elapsedMs(totalStartNanos));

            log.info("Pricebidopening PDF note created successfully. New version: {}", updateResult.path("newVersion").asText());
            log.info("createPricebidopeningPdfNote total duration: {} ms", elapsedMs(totalStartNanos));
            return result;

        } catch (Exception e) {
            log.error("Error creating Pricebidopening PDF note: {}", e.getMessage(), e);
            return createErrorResponse("Error creating Pricebidopening PDF note", e.getMessage());
        } finally {
            log.info("createPricebidopeningPdfNote finished (including failure/cleanup) in {} ms", elapsedMs(totalStartNanos));
            cleanupTempFiles(tempFilesToCleanup);
        }
    }

    private JsonNode createPdfNoteInternal(String processInstanceId, String workitemId, long sessionId,
            String extraSectionHtml) {
        log.info("Creating PDF note for processInstanceId: {}, workitemId: {}", processInstanceId, workitemId);
        final long totalStartNanos = System.nanoTime();
        String uniqueId = UUID.randomUUID().toString();
        // Track temp files for cleanup
        List<Path> tempFilesToCleanup = new ArrayList<>();
        String sanitizedExtraSectionHtml = "";
        if (extraSectionHtml != null && !extraSectionHtml.trim().isEmpty()) {
            if (processInstanceId != null && processInstanceId.toLowerCase().contains("legal")) {
                sanitizedExtraSectionHtml = extraSectionHtml;
            } else {
                log.info("Ignoring extra section HTML for non-legal processInstanceId: {}", processInstanceId);
            }
        }

        try {
            // Step 1: Fetch work item attributes ONCE (replaces 3 separate sequential iBPS calls)
            long stepStartNanos = System.nanoTime();
            log.info("Step 1: Fetching work item attributes...");
            JsonNode attributes = fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
            if (attributes == null || attributes.has("error")) {
                return createErrorResponse("Failed to get work item attributes",
                        buildFetchAttributesFailureMessage(attributes));
            }
            log.info("Step 1 completed in {} ms", elapsedMs(stepStartNanos));

            // Extract notesheet_original attribute (FolderIndex#VersionNo#DocumentIndex)
            String notesheetOriginalValue = extractNotesheetOriginal(attributes);
            if (notesheetOriginalValue == null || notesheetOriginalValue.isEmpty()) {
                return createErrorResponse("Failed to get original notesheet", "notesheet_original attribute not found");
            }
            String[] origParts = notesheetOriginalValue.split("#");
            if (origParts.length < 3) {
                return createErrorResponse("Failed to get original notesheet",
                        "Invalid notesheet_original format: " + notesheetOriginalValue);
            }
            String originalDocIndex = origParts[2];
            log.info("originalDocIndex: {}", originalDocIndex);

            // Extract notesheet attribute (FolderIndex#VersionNo#DocumentIndex)
            String notesheetValue = extractNotesheetAttribute(attributes);
            if (notesheetValue == null || notesheetValue.isEmpty()) {
                return createErrorResponse("Failed to get notesheet", "notesheet attribute not found");
            }
            String[] notesParts = notesheetValue.split("#");
            if (notesParts.length < 3) {
                return createErrorResponse("Failed to get notesheet",
                        "Invalid notesheet format: " + notesheetValue);
            }
            String notedocumentIndex = notesParts[2];
            log.info("notedocumentIndex: {}", notedocumentIndex);

            // Extract comments from the same attribute response (no extra iBPS call)
            JsonNode commentsResult = extractCommentsFromAttributes(attributes);
            String commentsPath = saveCommentsToFile(commentsResult, uniqueId);
            log.info("Comments extracted and saved to: {}", commentsPath);

            // Step 2 (parallel): Download original notesheet doc + get supporting documents concurrently
            stepStartNanos = System.nanoTime();
            log.info("Step 2: Downloading original document and fetching supporting docs in parallel...");
            final long finalSessionId = sessionId;
            final String finalOriginalDocIndex = originalDocIndex;
            java.util.concurrent.CompletableFuture<byte[]> docFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            downloadDocument(finalOriginalDocIndex, finalSessionId));
            java.util.concurrent.CompletableFuture<JsonNode> supportingDocsFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            supportingDocsService.getSupportingDocuments(processInstanceId, workitemId, finalSessionId));

            byte[] documentContent = docFuture.get();
            if (documentContent == null || documentContent.length == 0) {
                return createErrorResponse("Failed to get original notesheet", "Document download returned empty content");
            }
            String htmlFilePath = saveToTempFile(documentContent, "notesheet_original_" + processInstanceId);
            tempFilesToCleanup.add(Paths.get(htmlFilePath));

            JsonNode supportingDocsResult = supportingDocsFuture.get();
            JsonNode documentsArray = supportingDocsResult.path("documents");
            int docCount = supportingDocsResult.path("count").asInt(0);
            log.info("Step 2 completed in {} ms — original doc: {} bytes, supporting docs: {}",
                    elapsedMs(stepStartNanos), documentContent.length, docCount);

            // Step 5: Generate PDF with documents, comments, and track View positions
            stepStartNanos = System.nanoTime();
            log.info("Step 5: Generating PDF with documents, comments, and position tracking...");
            JsonNode commentsArray = commentsResult.path("comments");
            PdfGenerationResult pdfResult = generatePdfWithPositions(
                    htmlFilePath, documentsArray, commentsArray, uniqueId, sanitizedExtraSectionHtml);
            String pdfPath = pdfResult.pdfPath;
            List<ViewLinkPosition> viewPositions = pdfResult.viewPositions;
            log.info("PDF generated at: {} with {} view positions", pdfPath, viewPositions.size());
            log.info("Step 5 completed in {} ms", elapsedMs(stepStartNanos));

            // Track temp files for cleanup
            tempFilesToCleanup.add(Paths.get(pdfPath));
            tempFilesToCleanup.add(Paths.get(commentsPath));
            // Debug HTML file
            Path debugHtml = Paths.get(tempDirectory).resolve("debug-" + uniqueId + ".html");
            if (Files.exists(debugHtml)) {
                tempFilesToCleanup.add(debugHtml);
            }
            // Original notesheet temp file already added to cleanup right after saveToTempFile

            // Step 6: Call checkoutCheckinWithAnnotations (with filtering of existing View hyperlinks)
            stepStartNanos = System.nanoTime();
            log.info("Step 6: Updating notesheet with new PDF...");
            JsonNode updateResult = documentOpsService.checkoutCheckinWithAnnotations(
                    notedocumentIndex, pdfPath, sessionId, true, originalDocIndex);

            if (!updateResult.path("success").asBoolean(false)) {
                return createErrorResponse("Failed to update notesheet",
                        updateResult.path("error").asText("Update failed"));
            }
            log.info("Step 6 completed in {} ms", elapsedMs(stepStartNanos));

            // Step 7: Add View hyperlink annotations (grouped by page)
            // TEMPORARILY DISABLED per request
            int annotationsAdded = 0;
            // log.info("Step 7: Adding View hyperlink annotations...");
            // if (!viewPositions.isEmpty()) {
            //     // Group positions by page number
            //     java.util.Map<Integer, List<ViewLinkPosition>> positionsByPage = new java.util.LinkedHashMap<>();
            //     for (ViewLinkPosition pos : viewPositions) {
            //         positionsByPage.computeIfAbsent(pos.pageNo, k -> new ArrayList<>()).add(pos);
            //     }
            //
            //     log.info("View links distributed across {} page(s)", positionsByPage.size());
            //
            //     // Create separate annotation groups for each page
            //     for (java.util.Map.Entry<Integer, List<ViewLinkPosition>> entry : positionsByPage.entrySet()) {
            //         int pageNo = entry.getKey();
            //         List<ViewLinkPosition> pagePositions = entry.getValue();
            //         String groupName = "ViewLinks_Page" + pageNo;
            //
            //         log.info("Creating annotation group '{}' with {} hyperlinks for page {}",
            //                 groupName, pagePositions.size(), pageNo);
            //
            //         String annotBuffer = buildHyperlinkAnnotationBuffer(pagePositions, groupName, "system");
            //         ObjectNode annotGroup = jsonMapper.createObjectNode();
            //         annotGroup.put("AnnotationType", "A");  // "A" = Annotation type used by working hyperlinks
            //         annotGroup.put("PageNo", String.valueOf(pageNo));  // Set correct page number
            //         annotGroup.put("AnnotGroupName", groupName);
            //         annotGroup.put("AccessType", "S");  // Shared
            //         annotGroup.put("AnnotationBuffer", annotBuffer);
            //
            //         JsonNode annotResult = setAnnotations(notedocumentIndex, annotGroup, sessionId);
            //         if (annotResult.path("success").asBoolean(false)) {
            //             annotationsAdded += pagePositions.size();
            //             log.info("Successfully added {} View hyperlink annotations for page {}", pagePositions.size(), pageNo);
            //         } else {
            //             log.warn("Failed to add View hyperlink annotations for page {}: {}", pageNo, annotResult.path("error").asText());
            //         }
            //     }
            // }

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
            result.put("durationMs", elapsedMs(totalStartNanos));

            log.info("PDF note created successfully. New version: {}", updateResult.path("newVersion").asText());
            log.info("createPdfNote total duration: {} ms", elapsedMs(totalStartNanos));
            return result;

        } catch (Exception e) {
            log.error("Error creating PDF note: {}", e.getMessage(), e);
            return createErrorResponse("Error creating PDF note", e.getMessage());
        } finally {
            log.info("createPdfNote finished (including failure/cleanup) in {} ms", elapsedMs(totalStartNanos));
            // Cleanup temp files (PDF, comments, debug HTML, original notesheet download)
            cleanupTempFiles(tempFilesToCleanup);
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * Saves comments JSON to a temp file.
     */
    private String saveCommentsToFile(JsonNode commentsResult, String uniqueId) throws IOException {
        Path tempDir = Paths.get(tempDirectory).getParent().resolve("comments");
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
     * Deletes temp files, logging warnings for any that fail.
     */
    private void cleanupTempFiles(List<Path> files) {
        for (Path file : files) {
            try {
                if (file != null && Files.exists(file)) {
                    Files.delete(file);
                    log.debug("Cleaned up temp file: {}", file);
                }
            } catch (IOException e) {
                log.warn("Failed to delete temp file {}: {}", file, e.getMessage());
            }
        }
    }

    /**
     * Generates PDF from original HTML content with supporting documents and appended comments.
     * Also tracks the positions of View elements for hyperlink annotation creation.
     */
    private PdfGenerationResult generatePdfWithPositions(String htmlFilePath, JsonNode documents, JsonNode comments,
            String uniqueId) throws Exception {
        return generatePdfWithPositions(htmlFilePath, documents, comments, uniqueId, "");
    }

    private PdfGenerationResult generatePdfWithPositions(String htmlFilePath, JsonNode documents, JsonNode comments,
            String uniqueId, String extraSectionHtml) throws Exception {
        // Read original content (from Froala editor — may contain Word-pasted tables)
        byte[] rawBytes = Files.readAllBytes(Paths.get(htmlFilePath));

        // Detect binary/docx files: DOCX files start with PK (ZIP magic bytes 0x504B)
        if (rawBytes.length >= 4 && rawBytes[0] == 0x50 && rawBytes[1] == 0x4B) {
            throw new IllegalArgumentException(
                    "Original notesheet appears to be a DOCX/ZIP file, not HTML. " +
                    "The Froala editor should save content as HTML. File: " + htmlFilePath);
        }

        String htmlContent = new String(rawBytes, StandardCharsets.UTF_8);

        // Sanitize Word/Froala HTML to prevent table truncation in PDF:
        // 1. Convert absolute pixel widths on tables to 100% so they fit within the page
        // 2. Convert absolute pixel widths on table cells to percentages or remove them
        // 3. Remove Word-specific mso-* CSS properties that can interfere with rendering
        htmlContent = sanitizeHtmlForPdf(htmlContent);

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

        // Documents section comes ABOVE comments section per user requirement
        // Sanitize extra section HTML too (e.g., legal print form may have Word-pasted content)
        String extraSection = (extraSectionHtml == null || extraSectionHtml.isEmpty())
                ? "" : sanitizeHtmlForPdf(extraSectionHtml);
        String bodyContent = htmlContent + extraSection + documentsSection + commentsSection;

        // Wrap in HTML document — Aspose handles regular HTML directly, no XHTML conversion needed
        // CSS body margin is 0 — page margins are controlled by Aspose PageInfo to avoid double margins.
        // table-layout is set to 'auto' so Aspose can calculate column widths based on content,
        // preventing truncation of long document names or comment text.
        // overflow-wrap ensures long unbreakable strings wrap instead of overflowing cells.
        String fullHtml = "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <style>\n" +
                "    body { font-family: 'Nirmala UI', Mangal, 'Noto Sans Devanagari', Arial, sans-serif !important; font-size: 12px; margin: 0; padding: 0; }\n" +
                "    * { font-family: 'Nirmala UI', Mangal, 'Noto Sans Devanagari', Arial, sans-serif !important; }\n" +
                "    table { border-collapse: collapse; width: 100% !important; max-width: 100% !important; table-layout: fixed !important; }\n" +
                "    td, th { border: 1px solid #333; padding: 4px; width: auto !important; white-space: normal !important; overflow-wrap: anywhere !important; word-break: break-word !important; }\n" +
                "    p, li, span, strong { overflow-wrap: anywhere; word-break: break-word; }\n" +
                "    img { max-width: 100% !important; height: auto !important; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                bodyContent + "\n" +
                "</body>\n" +
                "</html>";

        // Convert HTML to PDF using Aspose.PDF
        Path tempDir = Paths.get(tempDirectory);
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        String pdfFileName = "newNoteContent-" + uniqueId + ".pdf";
        Path pdfPath = tempDir.resolve(pdfFileName);

        configurePdfFonts();

        // Configure Aspose page layout
        // - A4 landscape-ready page size (default A4 portrait: 595 x 842 points)
        // - 20pt margins on all sides → usable width = 555pt
        // - IsRenderToSinglePage=false ensures multi-page support
        com.aspose.pdf.HtmlLoadOptions htmlOptions = new com.aspose.pdf.HtmlLoadOptions();
        htmlOptions.setInputEncoding(StandardCharsets.UTF_8.name());
        htmlOptions.setEmbedFonts(true);
        htmlOptions.setPageInfo(new com.aspose.pdf.PageInfo());
        htmlOptions.getPageInfo().setWidth(595);
        htmlOptions.getPageInfo().setHeight(842);
        htmlOptions.getPageInfo().setMargin(new com.aspose.pdf.MarginInfo(20, 20, 20, 20));
        // Scale content to fit within the page width so nothing gets cut off
        htmlOptions.setPageLayoutOption(com.aspose.pdf.HtmlPageLayoutOption.ScaleToPageWidth);

        // Save debug HTML only when DEBUG logging is enabled (avoid disk bloat in production)
        if (log.isDebugEnabled()) {
            Path htmlDebugPath = tempDir.resolve("debug-" + uniqueId + ".html");
            Files.writeString(htmlDebugPath, fullHtml, StandardCharsets.UTF_8);
            log.debug("Debug HTML saved to: {}", htmlDebugPath.toAbsolutePath());
        }

        try (java.io.ByteArrayInputStream htmlStream =
                new java.io.ByteArrayInputStream(fullHtml.getBytes(StandardCharsets.UTF_8))) {
            com.aspose.pdf.Document pdfDoc = new com.aspose.pdf.Document(htmlStream, htmlOptions);
            try {
                pdfDoc.save(pdfPath.toAbsolutePath().toString());
            } finally {
                pdfDoc.close();
            }
        }

        // extractViewPositionsFromPdf disabled — Step 7 (hyperlink annotations) is currently off
        // List<ViewLinkPosition> viewPositions = extractViewPositionsFromPdf(
        //         pdfPath.toAbsolutePath().toString(), docResult.docIndices, docResult.docNames);
        // log.info("Extracted {} view link positions from PDF for annotation creation", viewPositions.size());
        return new PdfGenerationResult(pdfPath.toAbsolutePath().toString(), new ArrayList<>());
    }

    private void configurePdfFonts() {
        if (pdfFontsConfigured) {
            return;
        }

        synchronized (PDF_FONT_CONFIG_LOCK) {
            if (pdfFontsConfigured) {
                return;
            }

            java.util.LinkedHashSet<String> fontDirs = new java.util.LinkedHashSet<>();
            if (noteSheetFontDirectories != null && !noteSheetFontDirectories.isBlank()) {
                for (String configuredPath : noteSheetFontDirectories.split("[,;]")) {
                    if (!configuredPath.isBlank()) {
                        fontDirs.add(configuredPath.trim());
                    }
                }
            }

            fontDirs.add("C:\\Windows\\Fonts");
            fontDirs.add("/usr/share/fonts");
            fontDirs.add("/usr/local/share/fonts");

            for (String fontDir : fontDirs) {
                try {
                    if (Files.isDirectory(Paths.get(fontDir))) {
                        com.aspose.pdf.FontRepository.addLocalFontPath(fontDir);
                        log.info("Registered PDF font directory: {}", fontDir);
                    }
                } catch (Exception e) {
                    log.warn("Failed to register PDF font directory {}: {}", fontDir, e.getMessage());
                }
            }

            try {
                com.aspose.pdf.FontRepository.loadFonts();
            } catch (Exception e) {
                log.warn("Failed to preload PDF fonts: {}", e.getMessage());
            }

            pdfFontsConfigured = true;
        }
    }

    /**
     * Extracts View link positions from the generated PDF using Aspose.PDF text search.
     * Searches for document names in the Supporting Documents table, then calculates
     * the corresponding View column coordinates for OmniDocs annotation placement.
     *
     * @param pdfPath Path to the PDF file
     * @param docIndices List of document indices for each View link
     * @param docNames List of document names to search for in the PDF
     * @return List of ViewLinkPosition objects with calculated coordinates
     */
    private List<ViewLinkPosition> extractViewPositionsFromPdf(
            String pdfPath,
            List<String> docIndices,
            List<String> docNames) {
        List<ViewLinkPosition> positions = new ArrayList<>();

        // Fixed layout coordinates for OmniDocs annotation system
        final int VIEW_X1 = 675;           // X position of View column (calibrated)
        final int VIEW_WIDTH = 40;         // Width of hyperlink area
        final int VIEW_HEIGHT = 15;        // Height of hyperlink area
        final int Y_ADJUST_OMNI = 24;      // Move overlay down into data row (header + padding)

        log.info("=== Extracting View Positions using Aspose.PDF ===");
        log.info("PDF path: {}", pdfPath);
        log.info("Number of documents: {}", docNames.size());

        com.aspose.pdf.Document pdfDoc = null;
        try {
            pdfDoc = new com.aspose.pdf.Document(pdfPath);

            // Find "Supporting Documents" header to establish minimum search boundary
            int minPage = 1;
            float minY = 0;
            com.aspose.pdf.TextFragmentAbsorber headerAbsorber =
                    new com.aspose.pdf.TextFragmentAbsorber("Supporting Documents");
            pdfDoc.getPages().accept(headerAbsorber);
            com.aspose.pdf.TextFragmentCollection headerFragments = headerAbsorber.getTextFragments();
            if (headerFragments.size() > 0) {
                // Aspose TextFragmentCollection is 1-based
                com.aspose.pdf.TextFragment headerFragment = headerFragments.get_Item(1);
                if (headerFragment == null || headerFragment.getPage() == null) {
                    log.warn("Supporting Documents header fragment has no page info, skipping boundary check");
                } else {
                    minPage = headerFragment.getPage().getNumber();
                    // Aspose uses bottom-left origin; convert to top-down for comparison
                    float pageHeight = (float) headerFragment.getPage().getRect().getHeight();
                    minY = pageHeight - (float) headerFragment.getRectangle().getURY();
                    log.info("Found 'Supporting Documents' on page {} at Y={} (from top)", minPage, minY);
                }
            }

            // Search for each document name in the PDF
            for (int i = 0; i < docNames.size(); i++) {
                String docName = docNames.get(i);
                String docIndex = docIndices.get(i);

                // Use TextSearchOptions to ensure literal text matching (not regex),
                // so document names with special chars like "Report (Q1).pdf" match correctly
                com.aspose.pdf.TextFragmentAbsorber absorber =
                        new com.aspose.pdf.TextFragmentAbsorber(docName);
                com.aspose.pdf.TextSearchOptions searchOpts = new com.aspose.pdf.TextSearchOptions(false);
                absorber.setTextSearchOptions(searchOpts);
                pdfDoc.getPages().accept(absorber);
                com.aspose.pdf.TextFragmentCollection fragments = absorber.getTextFragments();

                // Find the first match that's below the Supporting Documents header
                com.aspose.pdf.TextFragment matchedFragment = null;
                for (int f = 1; f <= fragments.size(); f++) {
                    com.aspose.pdf.TextFragment frag = fragments.get_Item(f);
                    int fragPage = frag.getPage().getNumber();
                    float fragPageHeight = (float) frag.getPage().getRect().getHeight();
                    float fragTopDownY = fragPageHeight - (float) frag.getRectangle().getURY();

                    boolean isAfterHeader = fragPage > minPage ||
                            (fragPage == minPage && fragTopDownY > minY);
                    if (isAfterHeader) {
                        matchedFragment = frag;
                        break;
                    }
                }

                if (matchedFragment != null) {
                    int pageNo = matchedFragment.getPage().getNumber();
                    float pageHeight = (float) matchedFragment.getPage().getRect().getHeight();
                    float topDownY = pageHeight - (float) matchedFragment.getRectangle().getURY();
                    float omniScale = 1040f / pageHeight;
                    int y1 = Math.round(topDownY * omniScale) + Y_ADJUST_OMNI;
                    int x1 = VIEW_X1;
                    int x2 = x1 + VIEW_WIDTH;
                    int y2 = y1 + VIEW_HEIGHT;

                    log.info("Found '{}' on page {} at Y={}. View coords: ({},{}) to ({},{})",
                            docName, pageNo, y1, x1, y1, x2, y2);

                    positions.add(new ViewLinkPosition(i, docIndex, pageNo, x1, y1, x2, y2));
                } else {
                    log.warn("Could not find document name '{}' in PDF", docName);
                }
            }

        } catch (Exception e) {
            log.error("Error extracting View positions from PDF: {}", e.getMessage(), e);
        } finally {
            if (pdfDoc != null) {
                pdfDoc.close();
            }
        }

        log.info("=== End Position Extraction ({} positions found) ===", positions.size());
        return positions;
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
            buffer.append("HyperlinkURL=").append(buildOmniDocsRedirectUrl(pos.docIndex)).append("\n");
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

    private String buildOmniDocsRedirectUrl(String documentIndex) {
        String baseUrl = "http://" + ibpsServerHost + ":" + ibpsServerPort
                + "/omnidocs/WebApiRequestRedirection";
        return baseUrl
                + "?Application=SupportDocsView"
                + "&cabinetName=" + urlEncode(ibpsCabinetName)
                + "&sessionIndexSet=false"
                + "&DocumentId=" + urlEncode(documentIndex)
                + "&enableDCInfo=true"
                + "&S=S";
    }

    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Loads HTML template from classpath resources.
     */
    private String loadTemplate(String templatePath) throws IOException {
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(templatePath)) {
            if (is == null) {
                log.warn("Template not found: {}, using default", templatePath);
                if (templatePath.contains("document_list_template")) {
                    return "<div style=\"margin-top:20px;\"><table border=\"1\" style=\"width:100%;\"><tr><td colspan=\"2\" style=\"background:#e0e0e0;\"><b>Supporting Documents</b></td></tr><tr style=\"background:#f0f0f0;font-weight:bold;\"><td style=\"width:8%;text-align:center;\">S.No</td><td style=\"width:92%;\">Document Name</td></tr>{{DOCUMENT_ROWS}}</table></div>";
                } else if (templatePath.contains("document_row_template")) {
                    return "<tr><td style=\"text-align:center;\">{{SNO}}</td><td>{{DOCUMENT_NAME}}</td></tr>";
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
                        .replace("{{USER}}", escapeHtml(comment.path("userName").asText("")))
                        .replace("{{DATE}}", escapeHtml(comment.path("dateTime").asText("")))
                        .replace("{{COMMENT_TEXT}}", escapeHtml(comment.path("comments").asText("")))
                        .replace("{{STAGE}}", escapeHtml(comment.path("stage").asText("")))
                        .replace("{{STATUS}}", escapeHtml(comment.path("actionTaken").asText(comment.path("status").asText(""))))
                        .replace("{{Actiontaken}}", escapeHtml(comment.path("actionTaken").asText("")));
                rows.append(row);
            }
        }

        return rows.toString();
    }

    /**
     * Renders document rows using the row template.
     * Format: S.NO. | Document Name (hyperlinked)
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
        List<String> docNames = new ArrayList<>();
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

                String docUrl = buildOmniDocsRedirectUrl(docIndex);
                String docNameHtml = "<a href=\"" + escapeHtml(docUrl)
                        + "\" style=\"color:#000;text-decoration:none;\">"
                        + escapeHtml(docName) + "</a>";

                String row = rowTemplate
                        .replace("{{SNO}}", String.valueOf(sno++))
                        .replace("{{DOCUMENT_NAME}}", docNameHtml)
                        .replace("{{DOCUMENT_INDEX}}", docIndex)
                        .replace("{{ROW_INDEX}}", String.valueOf(rowIndex));
                rows.append(row);
                docIndices.add(docIndex);
                docNames.add(docName);
                rowIndex++;
            }
        }

        return new RenderDocumentResult(rows.toString(), docIndices, docNames);
    }

    /**
     * Result class for renderDocumentRows containing HTML, document indices, and document names.
     */
    private static class RenderDocumentResult {
        final String html;
        final List<String> docIndices;
        final List<String> docNames;

        RenderDocumentResult(String html, List<String> docIndices, List<String> docNames) {
            this.html = html;
            this.docIndices = docIndices;
            this.docNames = docNames;
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
     * Sanitizes HTML content from Froala editor (which may contain Word-pasted tables)
     * to ensure tables fit within the PDF page width without horizontal truncation.
     *
     * Word/Froala generates HTML with absolute pixel widths on tables and cells
     * (e.g., style="width: 950px"). These exceed the PDF page width (~555pt usable)
     * and cause horizontal clipping. This method:
     *
     * 1. Replaces absolute pixel widths on <table> tags with width:100%
     * 2. Removes absolute pixel widths on <td>/<th>/<col> tags (lets auto-layout size them)
     * 3. Strips Word-specific mso-* CSS properties
     * 4. Removes explicit width/height on images that exceed page width
     */
    private String sanitizeHtmlForPdf(String html) {
        if (html == null || html.isEmpty()) return html;

        html = forcePdfFontFamily(html);

        // ===== TABLE WIDTH FIXES =====

        // 1a. Replace absolute pixel/pt/cm/in widths on <table> style with 100%
        //     Covers: width:950px, width: 950.5pt, width:25cm, width:8in
        //     Case-insensitive for tags since Froala/Word may output <TABLE>
        html = html.replaceAll(
                "(?i)(<table[^>]*style\\s*=\\s*[\"'][^\"']*)width\\s*:\\s*[0-9]+\\.?[0-9]*(px|pt|cm|in|mm)",
                "$1width: 100%");

        // 1b. Handle width as an HTML attribute: <table width="950"> or <table width="950px">
        html = html.replaceAll(
                "(?i)(<table[^>]*)\\s+width\\s*=\\s*[\"'][0-9]+(px)?[\"']",
                "$1 width=\"100%\"");

        // 1c. Handle single-quoted style attributes: <table style='width: 950px'>
        //     (Froala sometimes outputs single quotes)
        html = html.replaceAll(
                "(?i)(<table[^>]*style\\s*=\\s*'[^']*)width\\s*:\\s*[0-9]+\\.?[0-9]*(px|pt|cm|in|mm)",
                "$1width: 100%");

        // ===== CELL WIDTH FIXES =====

        // 2a. Remove absolute widths on <td>, <th> style (px, pt, cm, in, mm)
        //     This lets table-layout:auto distribute columns based on content
        html = html.replaceAll(
                "(?i)(<t[dh][^>]*style\\s*=\\s*[\"'][^\"']*)width\\s*:\\s*[0-9]+\\.?[0-9]*(px|pt|cm|in|mm)\\s*;?",
                "$1");

        // 2b. Remove absolute widths on <col>/<colgroup> style
        html = html.replaceAll(
                "(?i)(<col[^>]*style\\s*=\\s*[\"'][^\"']*)width\\s*:\\s*[0-9]+\\.?[0-9]*(px|pt|cm|in|mm)\\s*;?",
                "$1");

        // 2c. Remove width HTML attribute on <td>/<th>/<col>: <td width="250">
        html = html.replaceAll(
                "(?i)(<t[dh][^>]*)\\s+width\\s*=\\s*[\"'][0-9]+(px)?[\"']",
                "$1");
        html = html.replaceAll(
                "(?i)(<col[^>]*)\\s+width\\s*=\\s*[\"'][0-9]+(px)?[\"']",
                "$1");

        // 2d. Dynamic Word/Froala content can contain percentage or non-numeric widths
        //     (width:52%, width:auto, <table width="100%">, etc.). Strip those too so
        //     the PDF wrapper CSS owns table sizing. The negative lookbehind prevents
        //     accidentally matching border-width.
        html = html.replaceAll("(?i)(?<!-)\\bwidth\\s*:\\s*[^;\"']+;?", "");
        html = html.replaceAll("(?i)\\bmin-width\\s*:\\s*[^;\"']+;?", "");
        html = html.replaceAll("(?i)\\bmax-width\\s*:\\s*[^;\"']+;?", "");
        html = html.replaceAll(
                "(?i)(<table[^>]*)\\s+width\\s*=\\s*[\"'][^\"']*[\"']",
                "$1");
        html = html.replaceAll(
                "(?i)(<t[dh][^>]*)\\s+width\\s*=\\s*[\"'][^\"']*[\"']",
                "$1");
        html = html.replaceAll(
                "(?i)(<col[^>]*)\\s+width\\s*=\\s*[\"'][^\"']*[\"']",
                "$1");

        // ===== WORD-SPECIFIC CLEANUP =====

        // 3a. Remove Word mso-* CSS properties (mso-table-lspace, mso-border-alt, etc.)
        //     These are not valid CSS and can confuse PDF renderers
        html = html.replaceAll("mso-[a-zA-Z\\-]+\\s*:[^;\"']+;?", "");

        // 3b. Remove Word conditional comments: <!--[if gte mso 9]>...<![endif]-->
        html = html.replaceAll("(?s)<!--\\[if[^]]*\\]>.*?<!\\[endif\\]-->", "");

        // 3c. Remove Word XML namespace tags: <o:p>, </o:p>, <w:sdt>, <v:shape>, <m:oMath>, etc.
        //     Only targets known Word namespace prefixes to avoid stripping valid SVG/XLink tags.
        html = html.replaceAll("(?i)</?(?:o|w|v|m|st\\d*):[a-z]+[^>]*>", "");

        // 3d. Remove class="Mso*" Word style classes (MsoNormal, MsoTableGrid, etc.)
        //     These reference undefined CSS classes that can cause layout issues
        html = html.replaceAll("(?i)\\s*class\\s*=\\s*[\"']Mso[^\"']*[\"']", "");

        // ===== IMAGE OVERFLOW FIXES =====

        // 4a. Cap image style widths to max-width:100% (px, pt, cm, in)
        html = html.replaceAll(
                "(?i)(<img[^>]*style\\s*=\\s*[\"'][^\"']*)width\\s*:\\s*[0-9]+\\.?[0-9]*(px|pt|cm|in|mm)",
                "$1max-width: 100%; width: auto");

        // 4b. Remove width/height HTML attributes on <img> (Word sets these in pixels)
        //     Keep aspect ratio by removing both; CSS max-width:100% handles sizing
        html = html.replaceAll(
                "(?i)(<img[^>]*)\\s+width\\s*=\\s*[\"'][0-9]+[\"']",
                "$1");
        html = html.replaceAll(
                "(?i)(<img[^>]*)\\s+height\\s*=\\s*[\"'][0-9]+[\"']",
                "$1");

        // ===== ABSOLUTE POSITION / MIN-WIDTH FIXES =====

        // 5a. Remove min-width with absolute values on any element (can force overflow)
        html = html.replaceAll(
                "(?i)min-width\\s*:\\s*[0-9]+\\.?[0-9]*(px|pt|cm|in|mm)\\s*;?", "");

        // 5b. Remove position:absolute from table elements (Word sometimes adds this)
        html = html.replaceAll(
                "(?i)(<t(?:able|[dhr]|head|body|foot)[^>]*style\\s*=\\s*[\"'][^\"']*)position\\s*:\\s*absolute\\s*;?",
                "$1");

        // ===== TEXT-INDENT & NEGATIVE MARGIN FIXES =====

        // 5c. Remove text-indent values > 40px (Word uses these for alignment, e.g. text-indent:768px)
        //     These push content beyond the page width causing left-side truncation.
        //     Preserves small indents (0-40px) that may be intentional paragraph indentation.
        {
            java.util.regex.Matcher tiMatcher = java.util.regex.Pattern.compile(
                    "(?i)text-indent\\s*:\\s*([0-9]+)\\.?[0-9]*(px|pt|cm|in|mm)\\s*;?").matcher(html);
            StringBuilder sb = new StringBuilder();
            while (tiMatcher.find()) {
                int val = Integer.parseInt(tiMatcher.group(1));
                tiMatcher.appendReplacement(sb, val > 40 ? "" : java.util.regex.Matcher.quoteReplacement(tiMatcher.group(0)));
            }
            tiMatcher.appendTail(sb);
            html = sb.toString();
        }

        // 5d. Remove individual negative margin-left/margin-right that push content off-page
        html = html.replaceAll("(?i)margin-left\\s*:\\s*-[0-9]+\\.?[0-9]*(px|pt|cm|in|mm)\\s*;?", "");
        html = html.replaceAll("(?i)margin-right\\s*:\\s*-[0-9]+\\.?[0-9]*(px|pt|cm|in|mm)\\s*;?", "");
        html = html.replaceAll("(?i)margin-left\\s*:\\s*calc\\([^;\"']+\\)\\s*;?", "");
        html = html.replaceAll("(?i)margin-right\\s*:\\s*calc\\([^;\"']+\\)\\s*;?", "");

        // 5e. For shorthand margin with negative values, zero out the negative sides
        //     e.g. "margin: 0px -51px 10.6667px -48px" → "margin: 0px 0px 10.6667px 0px"
        {
            java.util.regex.Matcher mMatcher = java.util.regex.Pattern.compile(
                    "(?i)(margin\\s*:\\s*)([^;\"']+)(;?)").matcher(html);
            StringBuilder sb = new StringBuilder();
            while (mMatcher.find()) {
                String value = mMatcher.group(2);
                if (value.contains("-")) {
                    // Replace negative values with 0px
                    value = value.replaceAll("-[0-9]+\\.?[0-9]*(px|pt|cm|in|mm)", "0px");
                    mMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                            mMatcher.group(1) + value + mMatcher.group(3)));
                } else {
                    mMatcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(mMatcher.group(0)));
                }
            }
            mMatcher.appendTail(sb);
            html = sb.toString();
        }

        // ===== NON-BREAKING SPACE OVERFLOW FIXES =====

        // 6a. Collapse runs of 3+ &nbsp; (possibly separated by regular spaces) into a single space.
        //     Word-pasted content uses &nbsp; chains for alignment (e.g., 72 consecutive &nbsp;
        //     before "Criteria notified"). These create unbreakable lines wider than the page.
        html = html.replaceAll("(?:&nbsp;\\s*){3,}", " ");

        // 6b. Replace remaining pairs of &nbsp; with a regular space + &nbsp;
        //     This preserves minimal spacing but allows line breaks between them.
        html = html.replaceAll("&nbsp;\\s*&nbsp;", " &nbsp;");

        // ===== CLEANUP =====

        // 7. Remove empty style attributes left after cleanup: style="" or style=''
        html = html.replaceAll("(?i)style\\s*=\\s*[\"']\\s*[\"']", "");

        return html;
    }

    private String forcePdfFontFamily(String html) {
        return html.replaceAll(
                "(?i)font-family\\s*:\\s*[^;\"]+;?",
                "font-family: 'Nirmala UI', Mangal, 'Noto Sans Devanagari', Arial, sans-serif;");
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
