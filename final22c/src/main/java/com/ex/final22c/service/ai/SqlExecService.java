package com.ex.final22c.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SqlExecService {
    private final JdbcTemplate jdbcTemplate;

    public List<Map<String,Object>> runSelect(String sql) {
        return jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setQueryTimeout(5); // 5초 타임아웃(데모)
            return ps;
        }, rs -> {
            var md = rs.getMetaData();
            int cols = md.getColumnCount();
            var out = new ArrayList<Map<String,Object>>();
            while (rs.next()) {
                var row = new LinkedHashMap<String,Object>();
                for (int i=1; i<=cols; i++) {
                    row.put(md.getColumnLabel(i), rs.getObject(i));
                }
                out.add(row);
            }
            return out;
        });
    }

    /** 마크다운 표로 보여주기(데모용) */
    public String formatAsMarkdownTable(List<Map<String,Object>> rows) {
        if (rows == null || rows.isEmpty()) return "_(결과 없음)_";
        var cols = new ArrayList<>(rows.get(0).keySet());

        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        for (var c : cols) sb.append(c).append(" | ");
        sb.append("\n| ");
        for (int i=0; i<cols.size(); i++) sb.append("--- | ");
        sb.append("\n");

        for (var r : rows) {
            sb.append("| ");
            for (var c : cols) sb.append(String.valueOf(r.get(c))).append(" | ");
            sb.append("\n");
        }
        return sb.toString();
    }
}
