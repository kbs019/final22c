package com.ex.final22c.sql;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SqlTokenInjector {
    private final DateColumnResolver dateResolver;

    private static final Pattern DATE_TOKEN = Pattern.compile("\\{\\{date:([a-zA-Z0-9_]+)\\}\\}");
    // ex) FROM ORDERS o / JOIN REVIEW r / FROM SCHEMA.ORDERS O
    private static final Pattern TBL_ALIAS = Pattern.compile("(?:FROM|JOIN)\\s+([A-Z0-9_\\.]+)\\s+([A-Z0-9_]+)", Pattern.CASE_INSENSITIVE);

    public String inject(String sql) {
        if (sql == null) return null;

        Map<String, String> aliasToTable = new LinkedHashMap<>();
        Matcher tj = TBL_ALIAS.matcher(sql);
        while (tj.find()) {
            String table = tj.group(1);
            String alias = tj.group(2);
            String tableOnly = table.contains(".") ? table.substring(table.lastIndexOf('.') + 1) : table;
            aliasToTable.put(alias.toUpperCase(Locale.ROOT), tableOnly.toUpperCase(Locale.ROOT));
        }

        Matcher m = DATE_TOKEN.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String aliasRaw = m.group(1);
            String tableUpper = aliasToTable.get(aliasRaw.toUpperCase(Locale.ROOT));
            if (tableUpper == null) {
                throw new IllegalArgumentException("날짜 토큰 alias를 테이블로 매핑하지 못했습니다: " + aliasRaw);
            }
            String dateCol = dateResolver.resolve(tableUpper)
                    .orElseThrow(() -> new IllegalArgumentException("날짜 컬럼을 찾지 못했습니다: table=" + tableUpper));
            String replacement = aliasRaw + "." + dateCol; // 원래 대소문자 보존
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
