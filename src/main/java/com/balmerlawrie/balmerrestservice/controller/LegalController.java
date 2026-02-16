package com.balmerlawrie.balmerrestservice.controller;

import com.balmerlawrie.balmerrestservice.service.NoteSheetService;
import com.balmerlawrie.balmerrestservice.service.SessionManager;
import com.balmerlawrie.balmerrestservice.service.WorkItemAttributesService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/legal")
@Tag(name = "Legal", description = "Legal module endpoints")
public class LegalController {

    @Autowired
    private NoteSheetService noteSheetService;

    @Autowired
    private WorkItemAttributesService workItemAttributesService;

    @Autowired
    private SessionManager sessionManager;

    private final ObjectMapper mapper = new ObjectMapper();

    @Operation(summary = "Legal Print Form", description = "POST wrapper that chain-calls /notesheet/createpdfnote and enriches response.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PDF note created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @ApiResponse(responseCode = "401", description = "Authentication failed")
    })
    @PostMapping(value = "/printform", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> printForm(
            @Parameter(description = "Work Item ID", required = true, example = "1")
            @RequestParam String workitemId,
            @Parameter(description = "Process Instance ID", required = true, example = "e-Notes-000000000008-process")
            @RequestParam String processInstanceId,
            @Parameter(description = "Optional Session ID from login. If not provided, uses service account.")
            @RequestHeader(value = "sessionId", required = false) Long providedSessionId) {

        if (workitemId == null || workitemId.trim().isEmpty() ||
                processInstanceId == null || processInstanceId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(error("Missing required parameters: workitemId, processInstanceId"));
        }

        if (!processInstanceId.toLowerCase().contains("legal")) {
            return ResponseEntity.badRequest().body(
                    error("Invalid processInstanceId for legal print form. Expected value containing 'legal'."));
        }

        Long effectiveSessionId = providedSessionId;
        if (effectiveSessionId == null || effectiveSessionId == 0) {
            effectiveSessionId = sessionManager.getServiceSession();
            if (effectiveSessionId == null) {
                return ResponseEntity.status(401).body(error("Failed to establish service session"));
            }
        }

        JsonNode attrResponse = workItemAttributesService.fetchWorkItemAttributes(processInstanceId, workitemId, effectiveSessionId);
        if (attrResponse == null || attrResponse.path("error").asText("").length() > 0) {
            return ResponseEntity.status(500).body(error("Failed to fetch legal attributes from work item"));
        }

        ObjectNode legalDetails = mapper.createObjectNode();
        legalDetails.put("caseid", readAttribute(attrResponse, "caseid"));
        legalDetails.put("casetitle", readAttribute(attrResponse, "casetitle"));
        legalDetails.put("casetype", readAttribute(attrResponse, "casetype"));
        legalDetails.put("jurisdiction", readAttribute(attrResponse, "jurisdiction"));
        legalDetails.put("region", readAttribute(attrResponse, "region"));
        legalDetails.put("filingdate", readAttribute(attrResponse, "filingdate"));
        legalDetails.put("casestatus", readAttribute(attrResponse, "casestatus"));
        legalDetails.put("courtname", readAttribute(attrResponse, "courtname"));
        legalDetails.put("location", readAttribute(attrResponse, "location"));
        legalDetails.put("judgename", readAttribute(attrResponse, "judgename"));
        legalDetails.put("partyname", readAttribute(attrResponse, "partyname"));
        legalDetails.put("partyaddress", readAttribute(attrResponse, "partyaddress"));
        legalDetails.put("partycontact", readAttribute(attrResponse, "partycontact"));

        String legalPrintFormHtml = renderLegalPrintForm(legalDetails);

        JsonNode pdfResult = noteSheetService.createPdfNoteWithExtraSection(
                processInstanceId, workitemId, effectiveSessionId, legalPrintFormHtml);

        if (pdfResult == null || !pdfResult.isObject()) {
            return ResponseEntity.status(500).body(error("Failed to generate legal PDF"));
        }

        ObjectNode enriched = ((ObjectNode) pdfResult).deepCopy();
        enriched.put("module", "legal");
        enriched.put("endpoint", "/legal/printform");
        enriched.put("chainedFrom", "/notesheet/createpdfnote");
        enriched.set("legalDetails", legalDetails);
        enriched.put("legalPrintFormHtml", legalPrintFormHtml);
        return ResponseEntity.ok(enriched);
    }

    private JsonNode error(String message) {
        ObjectNode error = mapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }

    private String renderLegalPrintForm(ObjectNode legalDetails) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/legal_printform.html");
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return template
                    .replace("{{CASEID}}", escapeHtml(legalDetails.path("caseid").asText("")))
                    .replace("{{CASETITLE}}", escapeHtml(legalDetails.path("casetitle").asText("")))
                    .replace("{{CASETYPE}}", escapeHtml(legalDetails.path("casetype").asText("")))
                    .replace("{{JURISDICTION}}", escapeHtml(legalDetails.path("jurisdiction").asText("")))
                    .replace("{{REGION}}", escapeHtml(legalDetails.path("region").asText("")))
                    .replace("{{FILINGDATE}}", escapeHtml(legalDetails.path("filingdate").asText("")))
                    .replace("{{CASESTATUS}}", escapeHtml(legalDetails.path("casestatus").asText("")))
                    .replace("{{COURTNAME}}", escapeHtml(legalDetails.path("courtname").asText("")))
                    .replace("{{LOCATION}}", escapeHtml(legalDetails.path("location").asText("")))
                    .replace("{{JUDGENAME}}", escapeHtml(legalDetails.path("judgename").asText("")))
                    .replace("{{PARTYNAME}}", escapeHtml(legalDetails.path("partyname").asText("")))
                    .replace("{{PARTYADDRESS}}", escapeHtml(legalDetails.path("partyaddress").asText("")))
                    .replace("{{PARTYCONTACT}}", escapeHtml(legalDetails.path("partycontact").asText("")));
        } catch (IOException e) {
            return "<div><b>Legal Print Form:</b> template load failed</div>";
        }
    }

    private String readAttribute(JsonNode attrResponse, String key) {
        JsonNode attrs = attrResponse.path("WMFetchWorkItemAttributes_Output").path("Attributes");
        if (attrs.isMissingNode()) {
            attrs = attrResponse.path("Attributes");
        }
        if (attrs.isMissingNode()) {
            attrs = attrResponse;
        }

        JsonNode valueNode = findFieldIgnoreCase(attrs, key);
        if (valueNode == null || valueNode.isMissingNode() || valueNode instanceof NullNode) {
            return "";
        }

        String direct = valueNode.asText("");
        if (direct != null && !direct.isEmpty() && !"null".equalsIgnoreCase(direct)) {
            return direct;
        }

        String fromEmptyKey = valueNode.path("").asText("");
        if (!fromEmptyKey.isEmpty()) {
            return fromEmptyKey;
        }

        String fromContent = valueNode.path("content").asText("");
        if (!fromContent.isEmpty()) {
            return fromContent;
        }

        return "";
    }

    private JsonNode findFieldIgnoreCase(JsonNode node, String targetKey) {
        if (node == null || !node.isObject()) {
            return null;
        }
        java.util.Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.equalsIgnoreCase(targetKey)) {
                return node.path(name);
            }
        }
        return null;
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
