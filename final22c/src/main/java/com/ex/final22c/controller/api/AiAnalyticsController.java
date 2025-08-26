// src/main/java/com/ex/final22c/controller/api/AiAnalyticsController.java
package com.ex.final22c.controller.api;

import com.ex.final22c.service.chat.ChatOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai")
public class AiAnalyticsController {

    private final ChatOrchestratorService orchestrator;

    @PostMapping(value = "/query", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AiResult query(@RequestBody AiQuery req, Principal principal) {
        var ans = orchestrator.handle(req.message(), principal);

        // 2) 표 데이터
        List<Map<String,Object>> rows = ans.rows() == null ? List.of() : ans.rows();
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());

        // 3) 차트 라벨/값 컬럼 자동(or 요청 지정)
        String labelCol = pickLabelCol(req.labelCol(), columns, rows);
        String valueCol = pickValueCol(req.valueCol(), columns, rows);

     // 4) 상위 N 자르기(옵션)
        if (req.topN() != null && req.topN() > 0 && !rows.isEmpty()) {
            rows = rows.stream()
                    .sorted((a, b) -> Double.compare(
                            num(b.get(valueCol)).doubleValue(),
                            num(a.get(valueCol)).doubleValue()
                    ))
                    .limit(req.topN())
                    .collect(Collectors.toList());
        }

        // 5) 차트 시리즈
        var labels = rows.stream().map(r -> String.valueOf(r.get(labelCol))).toList();
        var values = rows.stream().map(r -> num(r.get(valueCol))).toList();

        return new AiResult(
                ans.answer(),          // 요약/설명
                ans.sql(),              // 실행 SQL
                columns,                // 컬럼 목록
                rows,                   // 표 데이터
                new Chart(labelCol, valueCol, labels, values)
        );
    }

    /* ===== DTO ===== */
    public record AiQuery(String message, String labelCol, String valueCol, Integer topN) {}
    public record AiResult(String answer, String sql, List<String> columns, List<Map<String,Object>> rows, Chart chart) {}
    public record Chart(String labelCol, String valueCol, List<String> labels, List<Number> values) {}

    /* ===== helpers ===== */
    private static final Pattern NUM = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private static String pickLabelCol(String want, List<String> cols, List<Map<String,Object>> rows) {
        if (want != null && cols.contains(want)) return want;
        for (String c : cols) if (isStringCol(rows, c) && c.toUpperCase().matches(".*(NAME|PRODUCT|BRAND|TITLE).*")) return c;
        for (String c : cols) if (isStringCol(rows, c)) return c;
        return cols.isEmpty() ? "LABEL" : cols.get(0);
    }
    private static String pickValueCol(String want, List<String> cols, List<Map<String,Object>> rows) {
        if (want != null && cols.contains(want)) return want;
        for (String c : cols) {
            String u = c.toUpperCase();
            if (isNumberCol(rows, c) && (u.contains("SALES")||u.contains("AMOUNT")||u.contains("TOTAL")
                    ||u.contains("QUANTITY")||u.contains("COUNT")||u.contains("PRICE"))) return c;
        }
        for (String c : cols) if (isNumberCol(rows, c)) return c;
        return cols.isEmpty() ? "VALUE" : cols.get(0);
    }
    private static boolean isStringCol(List<Map<String,Object>> rows, String col) {
        for (var r : rows) { var v = r.get(col); if (v != null) return v instanceof String; }
        return false;
    }
    private static boolean isNumberCol(List<Map<String,Object>> rows, String col) {
        for (var r : rows) {
            var v = r.get(col);
            if (v != null) return (v instanceof Number) || NUM.matcher(v.toString()).matches();
        }
        return false;
    }
    private static Number num(Object o) {
        if (o instanceof Number n) return n;
        if (o == null) return 0;
        try { return Double.valueOf(o.toString()); } catch (Exception e) { return 0; }
    }
}
