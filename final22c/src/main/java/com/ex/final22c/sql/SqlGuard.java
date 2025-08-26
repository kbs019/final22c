package com.ex.final22c.sql;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlGuard {
    private SqlGuard(){}

    // 허용 테이블 화이트리스트 (필요시 추가)
    private static final Set<String> ALLOWED_TABLES = Set.of(
        "USERS", "ORDERS", "ORDERDETAIL", "PAYMENT", "PRODUCT",
        "BRAND", "GRADE", "MAINNOTE", "VOLUME"
    );

    private static final Pattern FROM_JOIN_TBL =
        Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([\\w\\.\\\"]+)");
    private static final Pattern POS_PARAM = Pattern.compile("\\?");
    private static final Pattern FENCE_SQL_START = Pattern.compile("(?is)^```sql\\s*");
    private static final Pattern FENCE_END = Pattern.compile("(?s)```\\s*$");

    /** 기본 검사: SELECT만 + 금지어 + 테이블 화이트리스트 + ? 금지 + 다중문 금지 */
    public static String ensureSelect(String sql){
        if (sql == null || sql.isBlank())
            throw new IllegalArgumentException("SQL이 비어 있습니다.");

        String s = stripCodeFence(sql).trim();
        s = stripTrailingSemicolon(s);
        String u = s.toUpperCase();

        // 0) 내부에 세미콜론(;) 있으면 다중 스테이트먼트로 간주
        if (s.indexOf(';') >= 0)
            throw new IllegalArgumentException("세미콜론(;)이 포함된 다중 스테이트먼트는 허용되지 않습니다.");

        // 1) SELECT만 허용
        if (!u.startsWith("SELECT"))
            throw new IllegalArgumentException("SELECT만 허용됩니다.");

        // 2) 금지 키워드
        String[] banned = {" UPDATE ", " DELETE ", " INSERT ", " MERGE ", " DROP ", " ALTER ", " CREATE ", " TRUNCATE "};
        for (String b : banned) {
            if (u.contains(b)) throw new IllegalArgumentException("금지된 문구: " + b.trim());
        }

        // 3) Positional parameter(?) 금지 → named param(:id 등)만 허용
        if (POS_PARAM.matcher(s).find())
            throw new IllegalArgumentException("Positional parameter(?)는 허용되지 않습니다. :id 같은 named parameter를 사용하세요.");

        // 4) 테이블 화이트리스트 검사 (스키마/따옴표/별칭 무시)
        Matcher m = FROM_JOIN_TBL.matcher(s);
        while (m.find()) {
            String raw = m.group(1);                 // e.g. "SCOTT.\"PRODUCT\"" or PRODUCT
            String norm = normalizeTableName(raw);   // → PRODUCT
            if (!ALLOWED_TABLES.contains(norm)) {
                throw new IllegalArgumentException("허용되지 않은 테이블 접근: " + norm);
            }
        }

        return s;
    }

    /** 최대 n행 보장: 이미 FETCH/ROWNUM/OFFSET 있으면 존중, 없으면 변환/래핑 */
    public static String ensureLimit(String sql, int maxRows){
        String s = stripTrailingSemicolon(stripCodeFence(sql).trim());
        String u = s.toUpperCase();

        // 이미 한정이 있으면 그대로
        if (u.contains(" FETCH FIRST ") || u.contains(" ROWNUM ") || u.contains(" OFFSET "))
            return s;

        // MySQL 스타일 LIMIT을 ANSI로 변환
        s = s.replaceAll("(?i)LIMIT\\s+(\\d+)\\s*,\\s*(\\d+)", "OFFSET $1 ROWS FETCH NEXT $2 ROWS ONLY");
        s = s.replaceAll("(?i)LIMIT\\s+(\\d+)\\s+OFFSET\\s+(\\d+)", "OFFSET $2 ROWS FETCH NEXT $1 ROWS ONLY");
        s = s.replaceAll("(?i)LIMIT\\s+(\\d+)", "FETCH FIRST $1 ROWS ONLY");

        String u2 = s.toUpperCase();
        if (u2.contains(" FETCH FIRST ") || u2.contains(" OFFSET "))
            return s;

        // 11g 호환 안전 래핑
        return "SELECT * FROM (" + s + ") WHERE ROWNUM <= " + maxRows;
    }

    /* ===== helpers ===== */
    private static String stripCodeFence(String s){
        String t = s.trim();
        t = FENCE_SQL_START.matcher(t).replaceFirst("");
        t = FENCE_END.matcher(t).replaceFirst("");
        return t;
    }
    private static String stripTrailingSemicolon(String s){
        String t = s.trim();
        while (t.endsWith(";")) t = t.substring(0, t.length()-1).trim();
        return t;
    }
    private static String normalizeTableName(String token) {
        // 따옴표 제거, 스키마 분리, 첫 토큰만 취득
        String x = token.replace("\"", "");
        int dot = x.lastIndexOf('.');
        if (dot >= 0) x = x.substring(dot + 1);
        // 괄호/공백 이후는 별칭이므로 제거
        int sp = x.indexOf(' ');
        if (sp >= 0) x = x.substring(0, sp);
        int par = x.indexOf('(');
        if (par >= 0) x = x.substring(0, par);
        return x.toUpperCase();
    }
}
