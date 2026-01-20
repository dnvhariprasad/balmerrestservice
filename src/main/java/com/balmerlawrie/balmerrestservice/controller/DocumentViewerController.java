package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.service.SessionManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Controller for document viewing from PDF hyperlinks.
 * Provides login page and document download for users clicking links in downloaded PDFs.
 *
 * Scenario 2: User downloads PDF, clicks hyperlink, gets login page, authenticates, downloads document.
 */
@RestController
@RequestMapping("/docs")
@Tag(name = "Document Viewer", description = "Document viewing for PDF hyperlinks (Scenario 2)")
public class DocumentViewerController {

    private static final Logger log = LoggerFactory.getLogger(DocumentViewerController.class);

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${omnidocs.base.url}")
    private String omniDocsBaseUrl;

    @Value("${ibps.cabinet.name}")
    private String cabinetName;

    private final ObjectMapper mapper = new ObjectMapper();

    @Operation(summary = "Document Viewer Login Page",
               description = "Serves the login page for document viewing. Users are redirected here when clicking hyperlinks in downloaded PDFs.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login page HTML returned"),
            @ApiResponse(responseCode = "400", description = "Missing docIndex parameter")
    })
    @GetMapping(value = "/viewer", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewerPage(
            @Parameter(description = "Document Index to view after authentication", required = true)
            @RequestParam String docIndex) {

        if (docIndex == null || docIndex.isEmpty()) {
            return ResponseEntity.badRequest().body("<html><body><h1>Error: Missing docIndex parameter</h1></body></html>");
        }

        try {
            // Load the login page template
            ClassPathResource resource = new ClassPathResource("templates/document_viewer_login.html");
            String htmlContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            // Replace the docIndex placeholder
            htmlContent = htmlContent.replace("{{DOC_INDEX}}", docIndex);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(htmlContent);

        } catch (IOException e) {
            log.error("Failed to load login page template", e);
            return ResponseEntity.status(500).body("<html><body><h1>Error loading login page</h1></body></html>");
        }
    }

    @Operation(summary = "Authenticate for Document Download",
               description = "Authenticates user credentials and returns session info for document download.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication result returned"),
            @ApiResponse(responseCode = "401", description = "Authentication failed")
    })
    @PostMapping(value = "/viewer/authenticate",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> authenticate(@RequestBody JsonNode credentials) {

        String userName = credentials.path("userName").asText("");
        String password = credentials.path("password").asText("");
        String docIndex = credentials.path("docIndex").asText("");

        if (userName.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing userName or password"));
        }

        if (docIndex.isEmpty()) {
            return ResponseEntity.badRequest().body(createError("Missing docIndex"));
        }

        log.info("Authentication request for document {} by user {}", docIndex, userName);

        // Authenticate using session manager
        Long sessionId = sessionManager.getSession(userName, password);

        if (sessionId == null) {
            log.warn("Authentication failed for user {}", userName);
            return ResponseEntity.status(401).body(createError("Authentication failed. Please check your credentials."));
        }

        log.info("Authentication successful for user {}, sessionId: {}", userName, sessionId);

        ObjectNode response = mapper.createObjectNode();
        response.put("success", true);
        response.put("sessionId", sessionId);
        response.put("downloadUrl", "/docs/download/" + docIndex);
        response.put("userName", userName);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Download Document",
               description = "Downloads the document content as a file attachment. Requires valid sessionId header.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document content returned"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing session"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping(value = "/download/{docIndex}")
    public ResponseEntity<byte[]> downloadDocument(
            @Parameter(description = "Document Index", required = true)
            @PathVariable String docIndex,

            @Parameter(description = "Session ID from authentication", required = true)
            @RequestHeader("sessionId") long sessionId) {

        log.info("Download request for document {} with sessionId {}", docIndex, sessionId);

        try {
            // Build OmniDocs request to download document
            ObjectNode ngoGetDocumentBDO = mapper.createObjectNode();
            ngoGetDocumentBDO.put("cabinetName", cabinetName);
            ngoGetDocumentBDO.put("docIndex", docIndex);
            ngoGetDocumentBDO.put("versionNo", ""); // Latest version
            ngoGetDocumentBDO.put("userDBId", String.valueOf(sessionId));
            ngoGetDocumentBDO.put("userName", "");
            ngoGetDocumentBDO.put("userPassword", "");
            ngoGetDocumentBDO.put("authToken", "");
            ngoGetDocumentBDO.put("authTokenType", "");
            ngoGetDocumentBDO.put("locale", "en_US");
            ngoGetDocumentBDO.put("oAuth", "N");
            ngoGetDocumentBDO.put("sessionValid", "");

            ObjectNode payload = mapper.createObjectNode();
            payload.set("NGOGetDocumentBDO", ngoGetDocumentBDO);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload.toString(), headers);

            String getDocumentUrl = omniDocsBaseUrl + "/getDocumentStreamJSON";
            log.debug("Calling OmniDocs: {}", getDocumentUrl);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    getDocumentUrl, HttpMethod.POST, request, byte[].class);

            byte[] content = response.getBody();
            if (content == null || content.length == 0) {
                log.warn("No content returned for document {}", docIndex);
                return ResponseEntity.notFound().build();
            }

            log.info("Downloaded document {}: {} bytes", docIndex, content.length);

            // Determine content type and filename
            String contentType = "application/octet-stream";
            String filename = "document_" + docIndex;

            // Try to detect file type from content
            if (content.length > 4) {
                if (content[0] == 0x25 && content[1] == 0x50 && content[2] == 0x44 && content[3] == 0x46) {
                    contentType = "application/pdf";
                    filename = "document_" + docIndex + ".pdf";
                } else if (content[0] == (byte) 0xD0 && content[1] == (byte) 0xCF) {
                    contentType = "application/msword";
                    filename = "document_" + docIndex + ".doc";
                } else if (content[0] == 0x50 && content[1] == 0x4B) {
                    // Could be docx, xlsx, zip
                    contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    filename = "document_" + docIndex + ".docx";
                }
            }

            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.setContentType(MediaType.parseMediaType(contentType));
            responseHeaders.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
            responseHeaders.setContentLength(content.length);

            return new ResponseEntity<>(content, responseHeaders, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error downloading document {}: {}", docIndex, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    private JsonNode createError(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
}
