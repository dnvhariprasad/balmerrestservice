package com.balmerlawrie.balmerrestservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for fetching My Queue and Common Queue work items.
 * 
 * My Queue = Work items assigned directly to the user
 * Common Queue = Work items in shared/group queues the user has access to
 */
@Service
public class MyQueueService extends BaseIbpsService {

    private static final Logger log = LoggerFactory.getLogger(MyQueueService.class);

    @Value("${ibps.fetchMyQueueWorkItems.url}")
    private String fetchMyQueueUrl;

    @Value("${ibps.getQueueList.url}")
    private String getQueueListUrl;

    @Value("${ibps.fetchWorkList.url}")
    private String fetchWorkListUrl;

    /**
     * Fetches all work items in the user's My Queue.
     * Uses WMFetchWorkList with MyQueueFlag=Y.
     *
     * @param sessionId The user's session ID
     * @return JSON with work items list
     */
    public JsonNode getMyQueueItems(long sessionId) {
        log.info("Fetching My Queue items for session: {}", sessionId);

        String engineName = getEngineName();
        if (engineName == null) {
            return createErrorResponse("Failed to retrieve engine name");
        }

        String url = String.format(fetchMyQueueUrl, engineName);
        String xmlPayload = buildMyQueuePayload(engineName);

        try {
            JsonNode response = callIbpsApi(url, xmlPayload, sessionId);
            return parseWorkListResponse(response, "myQueue");
        } catch (Exception e) {
            log.error("Failed to fetch My Queue items: {}", e.getMessage());
            return createErrorResponse("Failed to fetch My Queue items: " + e.getMessage());
        }
    }

    /**
     * Fetches all accessible queues for the user.
     * Uses WMGetQueueList with QueueAssociation=2.
     *
     * @param sessionId The user's session ID
     * @return JSON with queue list
     */
    public JsonNode getUserQueues(long sessionId) {
        log.info("Fetching user queues for session: {}", sessionId);

        String engineName = getEngineName();
        if (engineName == null) {
            return createErrorResponse("Failed to retrieve engine name");
        }

        String url = String.format(getQueueListUrl, engineName);
        String xmlPayload = buildQueueListPayload(engineName);

        try {
            JsonNode response = callIbpsApi(url, xmlPayload, sessionId);
            return parseQueueListResponse(response);
        } catch (Exception e) {
            log.error("Failed to fetch user queues: {}", e.getMessage());
            return createErrorResponse("Failed to fetch user queues: " + e.getMessage());
        }
    }

    /**
     * Fetches work items from Common Queues (shared/group queues).
     *
     * @param sessionId The user's session ID
     * @return JSON with common queue work items
     */
    public JsonNode getCommonQueueItems(long sessionId) {
        log.info("Fetching Common Queue items for session: {}", sessionId);

        // First get user's queues
        JsonNode queuesResponse = getUserQueues(sessionId);
        if (!queuesResponse.path("success").asBoolean(false)) {
            return queuesResponse;
        }

        JsonNode queues = queuesResponse.path("queues");
        if (!queues.isArray() || queues.isEmpty()) {
            ObjectNode result = jsonMapper.createObjectNode();
            result.put("success", true);
            result.set("workItems", jsonMapper.createArrayNode());
            result.put("totalCount", 0);
            result.put("message", "No queues found for user");
            return result;
        }

        // Fetch work items from each queue
        List<JsonNode> allWorkItems = new ArrayList<>();
        for (JsonNode queue : queues) {
            int queueId = queue.path("queueId").asInt(0);
            String queueName = queue.path("queueName").asText("");

            if (queueId > 0) {
                JsonNode workItems = getQueueWorkItems(queueId, queueName, sessionId);
                if (workItems.path("success").asBoolean(false)) {
                    JsonNode items = workItems.path("workItems");
                    if (items.isArray()) {
                        for (JsonNode item : items) {
                            allWorkItems.add(item);
                        }
                    }
                }
            }
        }

        // Build response
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("success", true);
        ArrayNode workItemsArray = jsonMapper.createArrayNode();
        allWorkItems.forEach(workItemsArray::add);
        result.set("workItems", workItemsArray);
        result.put("totalCount", allWorkItems.size());

        log.info("Retrieved {} Common Queue items", allWorkItems.size());
        return result;
    }

    /**
     * Fetches work items from a specific queue.
     *
     * @param queueId   The queue ID
     * @param queueName The queue name (for response)
     * @param sessionId The user's session ID
     * @return JSON with work items
     */
    public JsonNode getQueueWorkItems(int queueId, String queueName, long sessionId) {
        log.debug("Fetching work items from queue: {} ({})", queueName, queueId);

        String engineName = getEngineName();
        if (engineName == null) {
            return createErrorResponse("Failed to retrieve engine name");
        }

        String url = String.format(fetchWorkListUrl, engineName, queueId);
        String xmlPayload = buildWorkListPayload(engineName, queueId);

        try {
            JsonNode response = callIbpsApi(url, xmlPayload, sessionId);
            return parseWorkListResponse(response, queueName);
        } catch (Exception e) {
            log.error("Failed to fetch work items from queue {}: {}", queueId, e.getMessage());
            return createErrorResponse("Failed to fetch work items: " + e.getMessage());
        }
    }

    /**
     * Fetches ALL work items (My Queue + Common Queues combined).
     *
     * @param sessionId The user's session ID
     * @return JSON with all work items
     */
    public JsonNode getAllWorkItems(long sessionId) {
        log.info("Fetching all work items (My Queue + Common Queue) for session: {}", sessionId);

        List<JsonNode> allWorkItems = new ArrayList<>();

        // Get My Queue items
        JsonNode myQueueResponse = getMyQueueItems(sessionId);
        if (myQueueResponse.path("success").asBoolean(false)) {
            JsonNode items = myQueueResponse.path("workItems");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    allWorkItems.add(item);
                }
            }
        }

        // Get Common Queue items
        JsonNode commonQueueResponse = getCommonQueueItems(sessionId);
        if (commonQueueResponse.path("success").asBoolean(false)) {
            JsonNode items = commonQueueResponse.path("workItems");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    allWorkItems.add(item);
                }
            }
        }

        // Build combined response
        ObjectNode result = jsonMapper.createObjectNode();
        result.put("success", true);

        ArrayNode workItemsArray = jsonMapper.createArrayNode();
        allWorkItems.forEach(workItemsArray::add);
        result.set("workItems", workItemsArray);

        result.put("myQueueCount", myQueueResponse.path("totalCount").asInt(0));
        result.put("commonQueueCount", commonQueueResponse.path("totalCount").asInt(0));
        result.put("totalCount", allWorkItems.size());

        log.info("Retrieved {} total work items", allWorkItems.size());
        return result;
    }

    // --- Helper Methods ---

    private JsonNode callIbpsApi(String url, String xmlPayload, long sessionId) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_XML));
        headers.set("sessionId", String.valueOf(sessionId));

        HttpEntity<String> request = new HttpEntity<>(xmlPayload, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

        return xmlMapper.readTree(response.getBody());
    }

    private String buildMyQueuePayload(String engineName) {
        return "<?xml version=\"1.0\"?>" +
                "<WMFetchWorkList_Input>" +
                "<Option>WMFetchWorkList</Option>" +
                "<EngineName>" + engineName + "</EngineName>" +
                "<MyQueueFlag>Y</MyQueueFlag>" +
                "<QueueId>0</QueueId>" +
                "<DataFlag>Y</DataFlag>" +
                "<CountFlag>Y</CountFlag>" +
                "<NoOfRecordsToFetch>100</NoOfRecordsToFetch>" +
                "</WMFetchWorkList_Input>";
    }

    private String buildQueueListPayload(String engineName) {
        return "<?xml version=\"1.0\"?>" +
                "<WMGetQueueList_Input>" +
                "<Option>WMGetQueueList</Option>" +
                "<EngineName>" + engineName + "</EngineName>" +
                "<QueueAssociation>2</QueueAssociation>" +
                "<DataFlag>Y</DataFlag>" +
                "</WMGetQueueList_Input>";
    }

    private String buildWorkListPayload(String engineName, int queueId) {
        return "<?xml version=\"1.0\"?>" +
                "<WMFetchWorkList_Input>" +
                "<Option>WMFetchWorkList</Option>" +
                "<EngineName>" + engineName + "</EngineName>" +
                "<QueueId>" + queueId + "</QueueId>" +
                "<DataFlag>Y</DataFlag>" +
                "<CountFlag>Y</CountFlag>" +
                "<NoOfRecordsToFetch>100</NoOfRecordsToFetch>" +
                "</WMFetchWorkList_Input>";
    }

    private JsonNode parseWorkListResponse(JsonNode response, String queueName) {
        ObjectNode result = jsonMapper.createObjectNode();

        // Log the actual response structure for debugging
        log.debug("Raw response structure: {}",
                response.toString().substring(0, Math.min(500, response.toString().length())));

        // Check if response is wrapped in WMFetchWorkList_Output
        JsonNode actualResponse = response;
        if (response.has("WMFetchWorkList_Output")) {
            actualResponse = response.path("WMFetchWorkList_Output");
        }

        String mainCode = actualResponse.path("Exception").path("MainCode").asText("1");
        if (!"0".equals(mainCode)) {
            result.put("success", false);
            result.put("error", "API returned error code: " + mainCode);
            return result;
        }

        result.put("success", true);

        ArrayNode workItems = jsonMapper.createArrayNode();
        // XmlMapper creates "Instruments" wrapper, with "Instrument" array inside
        JsonNode instruments = actualResponse.path("Instruments").path("Instrument");

        // If direct path doesn't work, try just "Instrument"
        if (instruments.isMissingNode() || instruments.isNull()) {
            instruments = actualResponse.path("Instrument");
        }

        log.debug("Instruments node type: {}, isEmpty: {}", instruments.getNodeType(), instruments.isEmpty());

        if (instruments.isArray()) {
            for (JsonNode item : instruments) {
                workItems.add(createWorkItemNode(item, queueName));
            }
        } else if (instruments.isObject() && !instruments.isEmpty()) {
            // Single item case
            workItems.add(createWorkItemNode(instruments, queueName));
        } else {
            // Try alternative paths - Jackson XmlMapper might use different naming
            log.debug("Trying alternative paths. Response keys: {}",
                    response.fieldNames().hasNext() ? response : "empty");

            // Iterate through all children to find work items
            response.fields().forEachRemaining(entry -> {
                log.debug("Field: {} -> Type: {}", entry.getKey(), entry.getValue().getNodeType());
            });
        }

        // Sort work items: My Queue first, then by process name
        ArrayNode sortedWorkItems = sortWorkItems(workItems);

        result.set("workItems", sortedWorkItems);
        result.put("totalCount", actualResponse.path("TotalCount").asInt(sortedWorkItems.size()));
        result.put("retrievedCount", actualResponse.path("RetrievedCount").asInt(sortedWorkItems.size()));

        return result;
    }

    /**
     * Creates a work item node with fields required for mobile display.
     * Fields ordered as: urn, activityName, entryDateTime, queueName, status,
     * lockedBy, workitemId
     */
    private ObjectNode createWorkItemNode(JsonNode item, String defaultQueueName) {
        ObjectNode workItem = jsonMapper.createObjectNode();

        // Mobile display fields in the required order
        workItem.put("urn", item.path("URN").asText());
        workItem.put("activityName", item.path("ActivityName").asText());
        workItem.put("entryDateTime", item.path("EntryDateTime").asText());
        workItem.put("queueName", item.path("QueueName").asText(defaultQueueName));
        workItem.put("status", item.path("WorkitemState").asText());
        workItem.put("lockedBy", item.path("LockedByName").asText());
        workItem.put("workitemId", item.path("WorkItemId").asText());

        // Additional fields for sorting/processing (not shown in mobile but useful)
        workItem.put("processName", item.path("RouteName").asText());
        workItem.put("processInstanceId", item.path("ProcessInstanceId").asText());

        return workItem;
    }

    /**
     * Sorts work items: My Queue items first, then by process name.
     */
    private ArrayNode sortWorkItems(ArrayNode workItems) {
        List<JsonNode> itemList = new ArrayList<>();
        workItems.forEach(itemList::add);

        itemList.sort((a, b) -> {
            String queueA = a.path("queueName").asText("");
            String queueB = b.path("queueName").asText("");

            // My Queue items first
            boolean isMyQueueA = queueA.toLowerCase().contains("myqueue");
            boolean isMyQueueB = queueB.toLowerCase().contains("myqueue");

            if (isMyQueueA && !isMyQueueB)
                return -1;
            if (!isMyQueueA && isMyQueueB)
                return 1;

            // Then sort by process name
            String processA = a.path("processName").asText("");
            String processB = b.path("processName").asText("");
            return processA.compareToIgnoreCase(processB);
        });

        ArrayNode sorted = jsonMapper.createArrayNode();
        itemList.forEach(sorted::add);
        return sorted;
    }

    private JsonNode parseQueueListResponse(JsonNode response) {
        ObjectNode result = jsonMapper.createObjectNode();

        String mainCode = response.path("Exception").path("MainCode").asText("1");
        if (!"0".equals(mainCode)) {
            result.put("success", false);
            result.put("error", "API returned error code: " + mainCode);
            return result;
        }

        result.put("success", true);

        ArrayNode queues = jsonMapper.createArrayNode();
        JsonNode queueList = response.path("Queue");

        if (queueList.isArray()) {
            for (JsonNode queue : queueList) {
                ObjectNode q = jsonMapper.createObjectNode();
                q.put("queueId", queue.path("QueueId").asInt());
                q.put("queueName", queue.path("QueueName").asText());
                q.put("processName", queue.path("ProcessName").asText());
                q.put("queueType", queue.path("QueueType").asText());
                q.put("workItemCount", queue.path("WorkitemCount").asInt(0));
                queues.add(q);
            }
        } else if (queueList.isObject() && !queueList.isEmpty()) {
            ObjectNode q = jsonMapper.createObjectNode();
            q.put("queueId", queueList.path("QueueId").asInt());
            q.put("queueName", queueList.path("QueueName").asText());
            q.put("processName", queueList.path("ProcessName").asText());
            q.put("queueType", queueList.path("QueueType").asText());
            q.put("workItemCount", queueList.path("WorkitemCount").asInt(0));
            queues.add(q);
        }

        result.set("queues", queues);
        result.put("totalCount", queues.size());

        return result;
    }
}
