package com.balmerlawrie.balmerrestservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for notesheet operations.
 */
@Schema(description = "Notesheet request parameters")
public class NoteSheetRequest {

    @Schema(description = "Work item ID", example = "e-Notes-000000000005-process")
    private String workitemId;

    @Schema(description = "Process Instance ID", example = "e-Notes-000000000005-process")
    private String processInstanceId;

    public NoteSheetRequest() {
    }

    public NoteSheetRequest(String workitemId, String processInstanceId) {
        this.workitemId = workitemId;
        this.processInstanceId = processInstanceId;
    }

    public String getWorkitemId() {
        return workitemId;
    }

    public void setWorkitemId(String workitemId) {
        this.workitemId = workitemId;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }
}
