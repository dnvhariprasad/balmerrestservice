package com.balmerlawrie.balmerrestservice.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class MovementRegistryRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Map<String, String> PREFIX_PROCEDURE_MAP = Map.of(
            "office-", "ng_eoff_movementRegister",
            "RTI-", "ng_rti_movementRegister",
            "legal-", "ng_legal_movementRegister",
            "finance-", "ng_finance_movementRegister"
    );

    public static String resolveMovementProcedure(String workitemno) {
        return PREFIX_PROCEDURE_MAP.entrySet().stream()
                .filter(entry -> workitemno.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown prefix: " + workitemno));
    }


    // For INSERT operation
    public Map<String, Object> insertMovementRegistry(String workitemno, String action, String assigneduser, String actionuser, String comments) {
        String procedureName = resolveMovementProcedure(workitemno);
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName(procedureName);

        Map<String, Object> inParams = new HashMap<>();
        inParams.put("query_type", "insert");
        inParams.put("workitemno", workitemno);
        inParams.put("action", action);
        inParams.put("assigneduser", assigneduser);
        inParams.put("actionuser", actionuser);
        inParams.put("comments", comments);
        inParams.put("actiondatetime","");
        return jdbcCall.execute(inParams);
    }


    public Map<String, Object> selectMovementRegistry(String workitemno) {
        String procedureName = resolveMovementProcedure(workitemno);
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName(procedureName)
                .declareParameters(
                        new SqlParameter("query_type", Types.VARCHAR),
                        new SqlParameter("workitemno", Types.VARCHAR)
                );

        Map<String, Object> inParams = new HashMap<>();
        inParams.put("query_type", "select");
        inParams.put("workitemno", workitemno);
        inParams.put("action", "");
        inParams.put("assigneduser", "");
        inParams.put("actionuser", "");
        inParams.put("comments", "");
        inParams.put("actiondatetime","");
        return jdbcCall.execute(inParams);
        //return (List<Map<String, Object>>) result.get("movementregistry");
    }

    public List<Map<String, Object>> backwardMovementRegistry(String workitemno) {

        String procedureName = resolveMovementProcedure(workitemno);

        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(jdbcTemplate)
                .withProcedureName(procedureName)
                .declareParameters(
                        new SqlParameter("query_type", Types.VARCHAR),
                        new SqlParameter("workitemno", Types.VARCHAR)
                );

        Map<String, Object> inParams = new HashMap<>();
        inParams.put("query_type", "BACKWARD");
        inParams.put("workitemno", workitemno);
        inParams.put("action", "");
        inParams.put("assigneduser", "");
        inParams.put("actionuser", "");
        inParams.put("comments", "");
        inParams.put("actiondatetime","");
        Map<String, Object> result = jdbcCall.execute(inParams);
        return (List<Map<String, Object>>) result.getOrDefault("#result-set-1", Collections.emptyList());

    }


}
