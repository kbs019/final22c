package com.ex.final22c.sql;

public final class SqlGuard {
    private SqlGuard(){}

    /** 기본 검사: SELECT만 + 금지어 + 허용 테이블 */
    public static String ensureSelectOnly(String sql){
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL이 비어 있습니다.");
        String s = stripCodeFence(sql).trim();
        s = stripTrailingSemicolon(s);
        String u = s.toUpperCase();

        if (!u.startsWith("SELECT")) throw new IllegalArgumentException("SELECT만 허용됩니다.");

        String[] banned = {" UPDATE ", " DELETE ", " INSERT ", " MERGE ", " DROP ", " ALTER ", " CREATE ", " TRUNCATE "};
        for (String b : banned) if (u.contains(b)) throw new IllegalArgumentException("금지된 문구: " + b.trim());

        // 허용 테이블: USERS, ORDERS, ORDERDETAIL, PAYMENT
        for (String tok : new String[]{" FROM ", " JOIN "}) {
            int idx = -1;
            while ((idx = u.indexOf(tok, idx + 1)) >= 0) {
                String after = u.substring(idx + tok.length()).trim();
                if (after.isEmpty()) continue;
                String table = after.split("[\\s\\(]")[0].replace("\"","");
                if (!(table.equals("USERS") || table.equals("ORDERS") || table.equals("ORDERDETAIL") || table.equals("PAYMENT"))) {
                    throw new IllegalArgumentException("허용되지 않은 테이블 접근: " + table);
                }
            }
        }
        return s;
    }

    /** 11g/12c 모두 안전하게 최대 n행만: 없으면 (SELECT …) WHERE ROWNUM <= n 로 래핑 */
    public static String ensureLimit(String sql, int maxRows){
        String s = stripTrailingSemicolon(stripCodeFence(sql).trim());
        String u = s.toUpperCase();
        if (u.contains(" FETCH FIRST ") || u.contains(" ROWNUM ") || u.contains(" OFFSET ")) return s;

        // MySQL 스타일 LIMIT 변환 (LIMIT n OFFSET m, LIMIT m,n, LIMIT n)
        s = s.replaceAll("(?i)LIMIT\\s+(\\d+)\\s*,\\s*(\\d+)", "OFFSET $1 ROWS FETCH NEXT $2 ROWS ONLY");
        s = s.replaceAll("(?i)LIMIT\\s+(\\d+)\\s+OFFSET\\s+(\\d+)", "OFFSET $2 ROWS FETCH NEXT $1 ROWS ONLY");
        s = s.replaceAll("(?i)LIMIT\\s+(\\d+)", "FETCH FIRST $1 ROWS ONLY");

        // 변환 후에도 LIMIT/없음 → 11g 호환 래핑
        String u2 = s.toUpperCase();
        if (u2.contains(" FETCH FIRST ") || u2.contains(" OFFSET ")) return s;
        // 안전 래핑 (ORDER BY 포함 쿼리도 OK)
        return "SELECT * FROM (" + s + ") WHERE ROWNUM <= " + maxRows;
    }

    /* helpers */
    private static String stripCodeFence(String s){
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("(?is)^```sql\\s*", "");
            t = t.replaceFirst("(?s)```\\s*$", "");
        }
        return t;
    }
    private static String stripTrailingSemicolon(String s){
        String t = s.trim();
        while (t.endsWith(";")) t = t.substring(0, t.length()-1).trim();
        return t;
    }
}