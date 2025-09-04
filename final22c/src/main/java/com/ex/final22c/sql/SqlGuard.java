package com.ex.final22c.sql;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlGuard {
    private SqlGuard(){}

    // 허용 테이블 화이트리스트 (필요시 추가)
    private static final Set<String> ALLOWED_TABLES = Set.of(
        "USERS", "ORDERS", "ORDERDETAIL", "PAYMENT", "PRODUCT",
        "BRAND", "GRADE", "MAINNOTE", "VOLUME", "REFUND", "REFUNDDETAIL",
        "CART", "CARTDETAIL", "REVIEW", "PURCHASE", "PURCHASEDETAIL"
    );

    // FROM/JOIN 뒤의 테이블 토큰 추출
    private static final Pattern FROM_JOIN_TBL =
        Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([\\w\\.\\\"]+)");

    // 위치 파라미터 금지
    private static final Pattern POS_PARAM = Pattern.compile("\\?");

    // 코드펜스 제거용
    private static final Pattern FENCE_SQL_START = Pattern.compile("(?is)^```sql\\s*");
    private static final Pattern FENCE_END = Pattern.compile("(?s)```\\s*$");

    // 금지 패턴
    private static final Pattern EXTRACT_ANY = Pattern.compile("(?i)\\bEXTRACT\\s*\\(");
    // WHERE 블록 내부에서의 TRUNC 사용 감지 (SELECT/GROUP BY에서의 TRUNC는 허용)
    private static final Pattern WHERE_BLOCK = Pattern.compile("(?is)\\bWHERE\\b([\\s\\S]*?)(?:\\bGROUP\\s+BY\\b|\\bORDER\\s+BY\\b|$)");
    private static final Pattern TRUNC_ANY   = Pattern.compile("(?i)\\bTRUNC\\s*\\(");

    /** 기본 검사: SELECT만 + 금지어 + 테이블 화이트리스트 + ? 금지 + 다중문 금지 + 날짜 규칙 */
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

        // 4) 날짜 규칙 강제
        // 4-1) EXTRACT 사용 금지 (대시보드와 일관성/성능/가드오탐 방지)
        if (EXTRACT_ANY.matcher(s).find()) {
            throw new IllegalArgumentException("날짜 필터는 EXTRACT 대신 범위 비교(>=, <)와 TRUNC(SELECT/GROUP BY만) 규칙을 사용하세요.");
        }
        // 4-2) WHERE 절 내부의 TRUNC 금지 (인덱스/파티션 활용 위해)
        Matcher where = WHERE_BLOCK.matcher(s);
        if (where.find()) {
            String whereBody = where.group(1);
            if (TRUNC_ANY.matcher(whereBody).find()) {
                throw new IllegalArgumentException("WHERE 절에서 TRUNC 사용은 금지입니다. 날짜는 >= :start AND < :end 같은 범위 비교를 사용하세요.");
            }
        }

        // 5) 테이블 화이트리스트 검사
        //    (이제 EXTRACT를 금지했으므로 FROM 오탐 위험이 사실상 사라졌지만, 기존 로직 유지)
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
