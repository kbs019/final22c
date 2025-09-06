package com.ex.final22c.sql;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class DateColumnResolver {
    private final JdbcTemplate jdbc;

    // 네가 준 스키마 기준(전부 대문자)
    private static final Map<String, List<String>> PREFERRED = Map.ofEntries(
        Map.entry("ORDERS",        List.of("REGDATE")),
        Map.entry("ORDERDETAIL",   List.of("REGDATE", "CREATEDATE", "REG")),
        Map.entry("PAYMENT",       List.of("APPROVEDAT", "REG")),
        Map.entry("REVIEW",        List.of("CREATEDATE")),
        Map.entry("REFUND",        List.of("CREATEDATE", "UPDATEDATE")),
        Map.entry("REFUNDDETAIL",  List.of("CREATEDATE", "UPDATEDATE")),
        Map.entry("CART",          List.of("CREATEDATE")),
        Map.entry("CARTDETAIL",    List.of("CREATEDATE")),
        Map.entry("PURCHASE",      List.of("REG")),
        Map.entry("PURCHASEDETAIL",List.of("CREATEDATE", "REG")),
        Map.entry("USERS",         List.of("REG"))
    );

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public Optional<String> resolve(String tableUpper) {
        if (tableUpper == null) return Optional.empty();
        String key = tableUpper.toUpperCase(Locale.ROOT);
        if (cache.containsKey(key)) return Optional.of(cache.get(key));

        Set<String> cols = new HashSet<>(jdbc.query(
            "SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ?",
            (rs, i) -> rs.getString(1),
            key
        ));
        for (String cand : PREFERRED.getOrDefault(key, List.of())) {
            if (cols.contains(cand)) {
                cache.put(key, cand);
                return Optional.of(cand);
            }
        }
        List<String> any = jdbc.query(
            "SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND (DATA_TYPE='DATE' OR DATA_TYPE LIKE 'TIMESTAMP%')",
            (rs, i) -> rs.getString(1),
            key
        );
        if (!any.isEmpty()) {
            cache.put(key, any.get(0));
            return Optional.of(any.get(0));
        }
        return Optional.empty();
    }
}
