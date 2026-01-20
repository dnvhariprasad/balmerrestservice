package com.balmerlawrie.balmerrestservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Service for document operations: checkout, checkin, and annotation
 * preservation.
 */
@Service
public class DocumentOpsService {

    private static final Logger log = LoggerFactory.getLogger(DocumentOpsService.class);

    @Value("${omnidocs.api.url}")
    private String omniDocsApiUrl;

    @Value("${omnidocs.checkin.url:${omnidocs.api.url}/../checkInDocumentJSON}")
    private String checkinUrl;

    @Value("${omnidocs.cabinet.name:fosasoft}")
    private String cabinetName;

    @Value("${omnidocs.default.volumeId:1}")
    private String defaultVolumeId;

    @Value("${omnidocs.default.siteId:1}")
    private String defaultSiteId;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private NoteSheetService noteSheetService;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Performs checkout, checkin with new content, and restores annotations.
     * 
     * @param documentIndex Document to update
     * @param contentPath   Path to the new content file
     * @param sessionId     Session ID for authentication
     * @return JSON result with status
     */
    public JsonNode checkoutCheckinWithAnnotations(String documentIndex, String contentPath, long sessionId) {
        log.info("Starting checkoutCheckinWithAnnotations for documentIndex: {}", documentIndex);

        try {
            // Step 1: Get annotations and save temporarily
            log.info("Step 1: Backing up annotations...");
            JsonNode annotationsResult = noteSheetService.getAnnotations(documentIndex, sessionId);
            JsonNode annotations = annotationsResult.path("annotations");

            String annotationBackupPath = null;
            if (!annotations.isMissingNode() && annotations.size() > 0) {
                annotationBackupPath = saveAnnotationsToTemp(documentIndex, annotations);
                log.info("Annotations backed up to: {}", annotationBackupPath);
            } else {
                log.info("No annotations found to backup.");
            }

            // Step 2: Force undo checkout (in case already checked out)
            log.info("Step 2: Ensuring document is not checked out...");
            undoCheckout(documentIndex, sessionId);

            // Step 3: Checkout the document
            log.info("Step 3: Checking out document...");
            JsonNode checkoutResult = checkoutDocument(documentIndex, sessionId);
            if (!checkoutResult.path("success").asBoolean(false)) {
                return checkoutResult;
            }

            String volumeId = checkoutResult.path("volumeId").asText(defaultVolumeId);
            String siteId = checkoutResult.path("siteId").asText(defaultSiteId);
            if (volumeId.isEmpty())
                volumeId = defaultVolumeId;
            if (siteId.isEmpty())
                siteId = defaultSiteId;

            // Step 4: Checkin with new content
            log.info("Step 4: Checking in with new content from: {}", contentPath);
            JsonNode checkinResult = checkinDocument(documentIndex, contentPath, volumeId, siteId, sessionId);
            if (!checkinResult.path("success").asBoolean(false)) {
                return checkinResult;
            }

            String newVersion = checkinResult.path("newVersion").asText();

            // Step 5: Restore annotations
            log.info("Step 5: Restoring annotations...");
            JsonNode restoreResult = null;
            if (annotationBackupPath != null) {
                restoreResult = noteSheetService.setAnnotations(documentIndex, annotations, sessionId);
                log.info("Annotations restored.");
            }

            // Build success response
            ObjectNode result = jsonMapper.createObjectNode();
            result.put("success", true);
            result.put("documentIndex", documentIndex);
            result.put("newVersion", newVersion);
            result.put("annotationsBackedUp", annotationBackupPath != null);
            result.put("annotationsRestored", restoreResult != null);
            if (annotationBackupPath != null) {
                result.put("annotationBackupPath", annotationBackupPath);
            }

            return result;

        } catch (Exception e) {
            log.error("Error in checkoutCheckinWithAnnotations: {}", e.getMessage(), e);
            ObjectNode errorResult = jsonMapper.createObjectNode();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * Saves annotations to a temporary file.
     */
    private String saveAnnotationsToTemp(String documentIndex, JsonNode annotations) throws Exception {
        Path tempDir = Paths.get("./tmp/annotations");
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
        }

        String fileName = UUID.randomUUID().toString() + "_annotations_" + documentIndex + ".json";
        Path filePath = tempDir.resolve(fileName);

        try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
            fos.write(annotations.toPrettyString().getBytes());
        }

        return filePath.toAbsolutePath().toString();
    }

    /**
     * Forces undo checkout of a document.
     */
    private void undoCheckout(String documentIndex, long sessionId) {
        try {
            ObjectNode docNode = jsonMapper.createObjectNode();
            docNode.put("DocumentIndex", documentIndex);

            ObjectNode documentsNode = jsonMapper.createObjectNode();
            documentsNode.set("Document", docNode);

            ObjectNode input = jsonMapper.createObjectNode();
            input.put("Option", "NGOCheckinCheckoutExt");
            input.put("CabinetName", cabinetName);
            input.put("UserDBId", String.valueOf(sessionId));
            input.put("CheckInOutFlag", "U"); // Undo
            input.set("Documents", documentsNode);

            ObjectNode inputData = jsonMapper.createObjectNode();
            inputData.set("NGOCheckinCheckoutExt_Input", input);

            ObjectNode ngoExecuteBDO = jsonMapper.createObjectNode();
            ngoExecuteBDO.set("inputData", inputData);
            ngoExecuteBDO.put("base64Encoded", "N");
            ngoExecuteBDO.put("locale", "en_US");

            ObjectNode payload = jsonMapper.createObjectNode();
            payload.set("NGOExecuteAPIBDO", ngoExecuteBDO);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

            log.info("Calling NGOCheckinCheckoutExt (Undo) for documentIndex: {}", documentIndex);
            restTemplate.exchange(omniDocsApiUrl, HttpMethod.POST, request, String.class);
            // Ignore errors (e.g., if not checked out)

        } catch (Exception e) {
            log.warn("Undo checkout warning (may be expected): {}", e.getMessage());
        }
    }

    /**
     * Maps OmniDocs error codes to human-readable messages.
     */
    private String getOmniDocsErrorMessage(String errorCode) {
        switch (errorCode) {
            case "-1011":
                return "Insufficient rights/Access denied - User does not have permission to checkout this document";
            case "-1001":
                return "Invalid session - Session may have expired or is invalid";
            case "-1002":
                return "Invalid cabinet name";
            case "-1003":
                return "Document not found";
            case "-1004":
                return "Document is locked by another user";
            case "-1005":
                return "Invalid document index";
            case "-50146":
                return "Document already checked out";
            case "50011":
                return "Document already checked out by current user";
            case "-1010":
                return "User not found in cabinet";
            case "-1012":
                return "Document is read-only";
            case "-1013":
                return "Document version mismatch";
            default:
                return "Unknown error code: " + errorCode;
        }
    }

    /**
     * Checks out a document.
     */
    private JsonNode checkoutDocument(String documentIndex, long sessionId) {
        try {
            ObjectNode docNode = jsonMapper.createObjectNode();
            docNode.put("DocumentIndex", documentIndex);

            ObjectNode documentsNode = jsonMapper.createObjectNode();
            documentsNode.set("Document", docNode);

            ObjectNode input = jsonMapper.createObjectNode();
            input.put("Option", "NGOCheckinCheckoutExt");
            input.put("CabinetName", cabinetName);
            input.put("UserDBId", String.valueOf(sessionId));
            input.put("CheckInOutFlag", "Y"); // Checkout
            input.put("SupAnnotVersion", "N"); // Preserve annotations
            input.set("Documents", documentsNode);

            ObjectNode inputData = jsonMapper.createObjectNode();
            inputData.set("NGOCheckinCheckoutExt_Input", input);

            ObjectNode ngoExecuteBDO = jsonMapper.createObjectNode();
            ngoExecuteBDO.set("inputData", inputData);
            ngoExecuteBDO.put("base64Encoded", "N");
            ngoExecuteBDO.put("locale", "en_US");

            ObjectNode payload = jsonMapper.createObjectNode();
            payload.set("NGOExecuteAPIBDO", ngoExecuteBDO);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

            log.info("=== CHECKOUT REQUEST DEBUG ===");
            log.info("API URL: {}", omniDocsApiUrl);
            log.info("Document Index: {}", documentIndex);
            log.info("Session ID (UserDBId): {}", sessionId);
            log.info("Cabinet Name: {}", cabinetName);
            log.info("Request Payload: {}", payload.toString());

            ResponseEntity<String> response = restTemplate.exchange(omniDocsApiUrl, HttpMethod.POST, request,
                    String.class);

            log.info("=== CHECKOUT RESPONSE DEBUG ===");
            log.info("HTTP Status: {}", response.getStatusCode());
            log.info("Response Body: {}", response.getBody());

            JsonNode responseJson = jsonMapper.readTree(response.getBody());
            JsonNode output = responseJson.path("NGOExecuteAPIResponseBDO").path("outputData")
                    .path("NGOCheckinCheckoutExt_Output");

            String status = output.path("Status").asText();
            String errorDescription = output.path("ErrorDescription").asText("");

            log.info("OmniDocs Status Code: {}", status);
            if (!errorDescription.isEmpty()) {
                log.info("OmniDocs Error Description: {}", errorDescription);
            }

            if ("0".equals(status)) {
                JsonNode doc = output.path("Documents").path("Document");
                ObjectNode result = jsonMapper.createObjectNode();
                result.put("success", true);
                result.put("volumeId", doc.path("VolumeId").asText(""));
                result.put("siteId", doc.path("SiteId").asText(""));
                result.put("version", doc.path("DocumentVersionNo").asText(""));
                log.info("Checkout successful - Version: {}", doc.path("DocumentVersionNo").asText(""));
                return result;
            } else if ("-50146".equals(status) || "50011".equals(status)) {
                // Already checked out - retry after undo
                log.info("Document already checked out, retrying after undo...");
                undoCheckout(documentIndex, sessionId);
                return checkoutDocument(documentIndex, sessionId);
            } else {
                String errorMsg = getOmniDocsErrorMessage(status);
                log.error("=== CHECKOUT FAILED ===");
                log.error("Error Code: {}", status);
                log.error("Error Message: {}", errorMsg);
                log.error("OmniDocs Error Description: {}", errorDescription);
                log.error("Document Index: {}", documentIndex);
                log.error("Session ID: {}", sessionId);
                log.error("Cabinet: {}", cabinetName);
                log.error("Full Response: {}", response.getBody());

                ObjectNode result = jsonMapper.createObjectNode();
                result.put("success", false);
                result.put("error", "Checkout failed with status: " + status);
                result.put("errorCode", status);
                result.put("errorMessage", errorMsg);
                result.put("omniDocsErrorDescription", errorDescription);
                return result;
            }

        } catch (Exception e) {
            log.error("=== CHECKOUT EXCEPTION ===");
            log.error("Document Index: {}", documentIndex);
            log.error("Session ID: {}", sessionId);
            log.error("Exception: {}", e.getMessage(), e);
            ObjectNode result = jsonMapper.createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Checks in a document with new content using multipart form.
     */
    private JsonNode checkinDocument(String documentIndex, String contentPath, String volumeId, String siteId,
            long sessionId) {
        try {
            File file = new File(contentPath);
            if (!file.exists()) {
                ObjectNode result = jsonMapper.createObjectNode();
                result.put("success", false);
                result.put("error", "Content file not found: " + contentPath);
                return result;
            }

            // Build NGOCheckInDocumentBDO
            ObjectNode checkInBDO = jsonMapper.createObjectNode();
            checkInBDO.put("cabinetName", cabinetName);
            checkInBDO.put("userDBId", String.valueOf(sessionId));
            checkInBDO.put("documentIndex", documentIndex);
            checkInBDO.put("checkInOutFlag", "N");
            checkInBDO.put("majorVersion", "N");
            checkInBDO.put("volumeId", volumeId);
            checkInBDO.put("siteId", siteId);
            checkInBDO.put("supAnnotVersion", "N");
            checkInBDO.put("createdByAppName", "pdf");

            // Build multipart request
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("NGOCheckInDocumentBDO", checkInBDO.toString());
            body.add("file", new FileSystemResource(file));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            // Construct checkin URL
            String url = omniDocsApiUrl.replace("/executeAPIJSON", "/checkInDocumentJSON");
            log.info("Calling checkInDocumentJSON at: {}", url);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            JsonNode responseJson = jsonMapper.readTree(response.getBody());
            String status = responseJson.path("NGOCheckInDocumentResponseBDO").path("status").asText();

            if ("0".equals(status)) {
                String newVersion = responseJson.path("NGOCheckInDocumentResponseBDO").path("documentVersionNo")
                        .asText();
                ObjectNode result = jsonMapper.createObjectNode();
                result.put("success", true);
                result.put("newVersion", newVersion);
                return result;
            } else {
                ObjectNode result = jsonMapper.createObjectNode();
                result.put("success", false);
                result.put("error", "Checkin failed with status: " + status);
                result.set("response", responseJson);
                return result;
            }

        } catch (Exception e) {
            log.error("Checkin error: {}", e.getMessage(), e);
            ObjectNode result = jsonMapper.createObjectNode();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
