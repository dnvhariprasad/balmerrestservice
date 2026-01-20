package com.balmerlawrie.balmerrestservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

/**
 * Service for retrieving supporting documents attached to work items.
 * Uses OmniDocs NGOGetFolderContents API to list documents in the attachment folder.
 */
@Service
public class SupportingDocsService extends BaseIbpsService {

    @Value("${ibps.fetchAttributes.url}")
    private String fetchAttributesUrlTemplate;

    @Value("${omnidocs.api.url}")
    private String omniDocsApiUrl;

    /**
     * Retrieves the list of supporting documents for a work item.
     *
     * Flow:
     * 1. Call WMFetchWorkItemAttributes to get the AttachmentFolderId
     * 2. Call NGOGetFolderContents to list all documents in the folder
     *
     * @param processInstanceId Process instance ID
     * @param workitemId        Work item ID
     * @param sessionId         Session ID for authentication
     * @return JSON response with list of supporting documents
     */
    public JsonNode getSupportingDocuments(String processInstanceId, String workitemId, long sessionId) {
        log.info("Getting supporting documents for processInstanceId: {}, workitemId: {}",
                processInstanceId, workitemId);

        try {
            // Step 1: Get work item attributes to find the attachment folder
            JsonNode attributes = fetchWorkItemAttributes(processInstanceId, workitemId, sessionId);
            if (attributes == null || attributes.has("error")) {
                return createErrorResponse("Failed to fetch work item attributes",
                        attributes != null ? attributes.path("error").asText() : "Unknown error");
            }

            // Step 2: Extract AttachmentFolderId from attributes
            String attachmentFolderId = extractAttachmentFolderId(attributes);
            if (attachmentFolderId == null || attachmentFolderId.isEmpty()) {
                return createNotFoundResponse("No AttachmentFolderId found in work item attributes");
            }

            log.info("Found AttachmentFolderId: {}", attachmentFolderId);

            // Step 3: Get folder contents from OmniDocs
            JsonNode folderContents = getFolderContents(attachmentFolderId, sessionId);
            if (folderContents == null || folderContents.has("error")) {
                return createErrorResponse("Failed to get folder contents",
                        folderContents != null ? folderContents.path("error").asText() : "Unknown error");
            }

            // Step 4: Parse and return the document list
            return buildDocumentListResponse(folderContents, attachmentFolderId, processInstanceId, workitemId);

        } catch (Exception e) {
            log.error("Error retrieving supporting documents: {}", e.getMessage(), e);
            return createErrorResponse("Error retrieving supporting documents", e.getMessage());
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
            headers.set("sessionId", String.valueOf(sessionId));

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

            return result;

        } catch (org.springframework.web.client.HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("iBPS returned HTTP 500: {}", responseBody.substring(0, Math.min(300, responseBody.length())));
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

                String errorDesc = output.path("Error").path("Exception").path("Description")
                        .asText(output.path("Error").path("Exception").path("Subject").asText("Unknown iBPS error"));
                return createErrorResponse("iBPS error: " + errorDesc);
            } catch (Exception parseEx) {
                log.error("Failed to parse iBPS error response: {}", parseEx.getMessage());
                return createErrorResponse("Error fetching work item: " + e.getStatusCode());
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
     * Extracts AttachmentFolderId from work item attributes.
     */
    private String extractAttachmentFolderId(JsonNode attributes) {
        JsonNode output = attributes.path("WMFetchWorkItemAttributes_Output");
        if (output.isMissingNode()) {
            output = attributes;
        }

        JsonNode attrs = output.path("Attributes");
        if (attrs.isMissingNode()) {
            attrs = output;
        }

        // Try different attribute names for the attachment folder
        String[] folderAttributeNames = {
            "AttachmentFolderId", "attachmentfolderid", "ATTACHMENTFOLDERID",
            "itemindex", "ItemIndex", "ITEMINDEX",
            "FolderIndex", "folderindex", "FOLDERINDEX"
        };

        for (String attrName : folderAttributeNames) {
            JsonNode attrNode = attrs.path(attrName);
            if (!attrNode.isMissingNode()) {
                // Value can be directly or in empty key "" (iBPS format)
                String value = attrNode.asText();
                if (value != null && !value.isEmpty() && !value.equals("null")) {
                    return value;
                }
                value = attrNode.path("").asText();
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        log.warn("AttachmentFolderId not found in attributes");
        return null;
    }

    /**
     * Gets folder contents from OmniDocs using NGOGetDocumentListExt API.
     * Uses the exact structure from iBPS JavaScript implementation.
     */
    private JsonNode getFolderContents(String folderIndex, long sessionId) {
        try {
            // Build NGOGetDocumentListExt Input (exact structure from iBPS JS)
            ObjectNode input = jsonMapper.createObjectNode();
            input.put("Option", "NGOGetDocumentListExt");
            input.put("CabinetName", cabinetName);
            input.put("UserDBId", String.valueOf(sessionId));
            input.put("ZipBuffer", "N");
            input.put("FolderIndex", folderIndex);
            input.put("DocumentIndex", "0");
            input.put("StartPos", "0");
            input.put("NoOfRecordsToFetch", "1000");
            input.put("OrderBy", "5"); // 5 = CreatedDateTime
            input.put("SortOrder", "A"); // Ascending
            input.put("DataAlsoFlag", "N");
            input.put("PreviousRefIndex", "0");
            input.put("RefOrderBy", "2");
            input.put("RefSortOrder", "A");
            input.put("RecursiveFlag", "N");
            input.put("ThumbnailAlsoFlag", "N");

            // Wrap in NGOExecuteAPIBDO structure
            ObjectNode inputData = jsonMapper.createObjectNode();
            inputData.set("NGOGetDocumentListExt_Input", input);

            ObjectNode ngoExecuteBDO = jsonMapper.createObjectNode();
            ngoExecuteBDO.set("inputData", inputData);
            ngoExecuteBDO.put("base64Encoded", "N");
            ngoExecuteBDO.put("locale", "en_US");

            ObjectNode payload = jsonMapper.createObjectNode();
            payload.set("NGOExecuteAPIBDO", ngoExecuteBDO);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

            log.info("Calling OmniDocs NGOGetDocumentListExt: folderIndex={}, url={}", folderIndex, omniDocsApiUrl);
            log.debug("Payload: {}", payload.toString());

            ResponseEntity<String> response = restTemplate.exchange(omniDocsApiUrl, HttpMethod.POST, request,
                    String.class);

            log.info("OmniDocs response: {}", response.getBody());

            // Parse response
            JsonNode responseJson = parseXmlToJson(response.getBody());
            JsonNode output = responseJson.path("NGOExecuteAPIResponseBDO").path("outputData")
                    .path("NGOGetDocumentListExt_Output");

            if (output.isMissingNode()) {
                // Try alternative response paths
                output = responseJson.path("NGOExecuteAPIResponseBDO").path("outputData");
                log.warn("NGOGetDocumentListExt_Output missing, using fallback. Response: {}",
                        responseJson.toString().substring(0, Math.min(500, responseJson.toString().length())));
            }

            // Check for error status
            int status = output.path("Status").asInt(0);
            if (status != 0) {
                String error = output.path("Error").asText("Unknown error (Status: " + status + ")");
                log.error("OmniDocs NGOGetDocumentListExt error: Status={}, Error={}", status, error);

                // Return the full response for debugging
                ObjectNode errorResponse = jsonMapper.createObjectNode();
                errorResponse.put("success", false);
                errorResponse.put("error", error);
                errorResponse.put("status", status);
                errorResponse.put("rawResponse", responseJson.toString());
                return errorResponse;
            }

            return output;

        } catch (Exception e) {
            log.error("Error getting folder contents: {}", e.getMessage(), e);
            return createErrorResponse("Error getting folder contents", e.getMessage());
        }
    }

    /**
     * Builds the response with parsed document list.
     */
    private JsonNode buildDocumentListResponse(JsonNode folderContents, String folderId,
            String processInstanceId, String workitemId) {

        ObjectNode result = jsonMapper.createObjectNode();
        result.put("success", true);
        result.put("folderId", folderId);
        result.put("processInstanceId", processInstanceId);
        result.put("workitemId", workitemId);

        ArrayNode documents = result.putArray("documents");

        // Log full response for debugging
        log.info("Full folder contents response: {}", folderContents.toString());

        // Parse documents from folder contents - try multiple possible paths
        // NGOGetDocumentListExt typically returns Documents > Document array
        JsonNode documentList = folderContents.path("Documents").path("Document");
        if (documentList.isMissingNode()) {
            documentList = folderContents.path("Documents");
        }
        if (documentList.isMissingNode()) {
            documentList = folderContents.path("Document");
        }
        if (documentList.isMissingNode()) {
            documentList = folderContents.path("DocumentList").path("Document");
        }
        if (documentList.isMissingNode()) {
            documentList = folderContents.path("DocumentList");
        }

        log.info("Document list node - Type: {}, isArray: {}, isMissing: {}, size: {}",
                documentList.getNodeType(), documentList.isArray(), documentList.isMissingNode(),
                documentList.isArray() ? documentList.size() : (documentList.isMissingNode() ? 0 : 1));

        if (documentList.isArray()) {
            for (JsonNode doc : documentList) {
                log.debug("Parsing document node: {}", doc.toString());
                documents.add(parseDocumentNode(doc));
            }
        } else if (!documentList.isMissingNode() && !documentList.isNull()) {
            // Single document case
            log.debug("Parsing single document node: {}", documentList.toString());
            documents.add(parseDocumentNode(documentList));
        }

        result.put("count", documents.size());

        // Always add raw response for debugging during development
        result.put("rawResponse", folderContents.toString());

        log.info("Found {} supporting documents for workitem {}", documents.size(), workitemId);
        return result;
    }

    /**
     * Parses a single document node into a standardized format.
     */
    private ObjectNode parseDocumentNode(JsonNode doc) {
        ObjectNode docInfo = jsonMapper.createObjectNode();

        // Log all available fields in this document node for debugging
        StringBuilder fields = new StringBuilder();
        doc.fieldNames().forEachRemaining(f -> fields.append(f).append(", "));
        log.info("Document node fields: {}", fields.toString());

        // Try multiple field name variants for each property
        // NGOGetDocumentListExt commonly uses: DocIndex, DocName, DocType, etc.
        docInfo.put("documentIndex", getFirstNonEmpty(doc,
                "DocIndex", "DocumentIndex", "documentIndex", "docIndex", "Index", "DocId"));
        docInfo.put("documentName", getFirstNonEmpty(doc,
                "DocName", "DocumentName", "Name", "documentName", "name", "FileName", "DocFileName"));
        docInfo.put("documentType", getFirstNonEmpty(doc,
                "DocType", "DocumentType", "Type", "documentType", "type", "FileType", "Extension", "CreatedByAppName"));
        docInfo.put("documentSize", getFirstNonEmpty(doc,
                "DocSize", "DocumentSize", "Size", "documentSize", "size", "FileSize"));
        docInfo.put("createdDateTime", getFirstNonEmpty(doc,
                "CreatedDatetime", "CreatedDateTime", "CreationDateTime", "createdDateTime", "CreateDate", "CreatedDate", "CreationDate"));
        docInfo.put("modifiedDateTime", getFirstNonEmpty(doc,
                "RevisedDatetime", "ModifiedDateTime", "RevisedDateTime", "modifiedDateTime", "ModifyDate", "LastModified", "AccessedDatetime"));
        docInfo.put("versionNo", getFirstNonEmpty(doc,
                "VersionNo", "Version", "versionNo", "version", "DocumentVersion", "LatestVersionNo"));
        docInfo.put("owner", getFirstNonEmpty(doc,
                "Owner", "CreatedBy", "owner", "createdBy", "CreatedByName", "OwnerName", "CreatedByUserName"));
        docInfo.put("comment", getFirstNonEmpty(doc,
                "Comment", "Comments", "comment", "comments", "Description", "DocComment"));

        return docInfo;
    }

    /**
     * Returns the first non-empty value from multiple possible field names.
     */
    private String getFirstNonEmpty(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode fieldNode = node.path(fieldName);
            if (!fieldNode.isMissingNode() && !fieldNode.isNull()) {
                String value = fieldNode.asText();
                if (value != null && !value.isEmpty() && !value.equals("null")) {
                    return value;
                }
            }
        }
        return "";
    }

    /**
     * Creates a "not found" response.
     */
    private JsonNode createNotFoundResponse(String message) {
        ObjectNode response = jsonMapper.createObjectNode();
        response.put("success", true);
        response.put("found", false);
        response.put("message", message);
        response.putArray("documents");
        response.put("count", 0);
        return response;
    }
}
