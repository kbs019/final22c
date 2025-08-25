package com.ex.final22c.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SqlExecService {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate; // ⬅️ 추가

    /** 순수 SQL(바인딩 없음) 실행 */
    public List<Map<String,Object>> runSelect(String sql) {
        return jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setQueryTimeout(5);     // 5초 타임아웃(데모)
            ps.setFetchSize(200);      // 네트워크 왕복 줄이기(옵션)
            return ps;
        }, rs -> {
            var md = rs.getMetaData();
            int cols = md.getColumnCount();
            var out = new ArrayList<Map<String,Object>>();
            while (rs.next()) {
                var row = new LinkedHashMap<String,Object>();
                for (int i=1; i<=cols; i++) {
                    Object v = rs.getObject(i);
                    row.put(md.getColumnLabel(i), normalize(v));
                }
                out.add(row);
            }
            return out;
        });
    }

    /** 네임드 파라미터(:userNo, :limit ...) 바인딩 실행 */
    public List<Map<String,Object>> runSelectNamed(String sql, Map<String,Object> params){
        var src = new MapSqlParameterSource(params == null ? Map.of() : params);
        return namedJdbcTemplate.query(sql, src, (rs, rn) -> {
            var md = rs.getMetaData();
            int cols = md.getColumnCount();
            var row = new LinkedHashMap<String,Object>();
            for (int i=1; i<=cols; i++) {
                Object v = rs.getObject(i);
                row.put(md.getColumnLabel(i), normalize(v));
            }
            return row;
        });
    }

    /** 마크다운 표 (데모용) */
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

    /** 드라이버가 주는 타입을 화면/JSON 친화적으로 정리 */
    private Object normalize(Object v){
        if (v == null) return null;
        if (v instanceof Timestamp ts) {
            // Oracle TIMESTAMP를 ISO 문자열로
            return OffsetDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault()).toString();
        }
        if (v instanceof byte[] b) {
            // BLOB 등은 길이만
            return "(bytes:" + b.length + ")";
        }
        return v;
    }
}
