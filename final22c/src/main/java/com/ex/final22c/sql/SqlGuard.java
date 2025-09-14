package com.ex.final22c.sql;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlGuard {
    private SqlGuard(){}

    private static final Set<String> ALLOWED_TABLES = Set.of(
        "USERS","ORDERS","ORDERDETAIL","PAYMENT","PRODUCT",
        "BRAND","GRADE","MAINNOTE","VOLUME","REFUND","REFUNDDETAIL",
        "CART","CARTDETAIL","REVIEW","PURCHASE","PURCHASEDETAIL"
    );

    private static final Pattern FROM_JOIN_TBL =
        Pattern.compile("(?i)\\b(?:FROM|JOIN)\\s+([\\w\\.\\\"]+)");

    private static final Pattern POS_PARAM = Pattern.compile("\\?");

    private static final Pattern FENCE_SQL_START = Pattern.compile("(?is)^```sql\\s*");
    private static final Pattern FENCE_END       = Pattern.compile("(?s)```\\s*$");
    private static final Pattern BLOCK_COMMENT   = Pattern.compile("(?s)/\\*.*?\\*/");
    private static final Pattern LINE_COMMENT    = Pattern.compile("(?m)^\\s*--.*?$");

    private static final Pattern WHERE_BLOCK = Pattern.compile("(?is)\\bWHERE\\b([\\s\\S]*?)(?:\\bGROUP\\s+BY\\b|\\bORDER\\s+BY\\b|$)");
    private static final Pattern TRUNC_ANY   = Pattern.compile("(?i)\\bTRUNC\\s*\\(");

    private static final Pattern ALLOWED_DATE_COL =
        Pattern.compile("\\b[a-zA-Z_][\\w]*\\.(REGDATE|ORDERDATE|CREATEDATE|UPDATEDATE|APPROVEDAT|REG)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern OUTER_ALIAS_REF =
        Pattern.compile("\\)\\s*WHERE\\s+[a-zA-Z_][\\w]*\\.", Pattern.CASE_INSENSITIVE);

    private static final Pattern DATE_TOKEN_LEFT = Pattern.compile("\\{\\{\\s*date\\s*:\\s*[a-zA-Z_][\\w]*\\s*}}");

    public static String ensureSelect(String sql){
        if (sql == null || sql.isBlank())
            throw new IllegalArgumentException("SQL이 비어 있습니다.");

        String s = stripFencesAndComments(sql).trim();
        s = stripTrailingSemicolon(s);
        String u = s.toUpperCase();

        if (s.indexOf(';') >= 0)
            throw new IllegalArgumentException("세미콜론(;)이 포함된 다중 스테이트먼트는 허용되지 않습니다.");

        if (!u.startsWith("SELECT"))
            throw new IllegalArgumentException("SELECT만 허용됩니다.");

        String[] banned = {" UPDATE ", " DELETE ", " INSERT ", " MERGE ", " DROP ", " ALTER ", " CREATE ", " TRUNCATE "};
        for (String b : banned) if (u.contains(b)) throw new IllegalArgumentException("금지된 문구: " + b.trim());

        if (POS_PARAM.matcher(s).find())
            throw new IllegalArgumentException("위치 파라미터(?)는 허용되지 않습니다. 이름 있는 바인딩을 사용하세요.");

        Matcher m = FROM_JOIN_TBL.matcher(s);
        while (m.find()) {
            String raw = m.group(1);
            String norm = normalizeTableName(raw);
            if (!ALLOWED_TABLES.contains(norm)) {
                throw new IllegalArgumentException("허용되지 않은 테이블 접근: " + norm);
            }
        }

        Matcher wm = WHERE_BLOCK.matcher(s);
        while (wm.find()) {
            String whereChunk = wm.group(1);
            if (TRUNC_ANY.matcher(whereChunk).find()) {
                throw new IllegalArgumentException("WHERE 절에서 TRUNC 사용은 금지입니다. 날짜는 >= :start AND < :end 형태로 비교하세요.");
            }
        }

        if (DATE_TOKEN_LEFT.matcher(s).find())
            throw new IllegalArgumentException("치환되지 않은 날짜 토큰({{date:...}})이 남아 있습니다.");

        if (OUTER_ALIAS_REF.matcher(s).find())
            throw new IllegalArgumentException("서브쿼리 바깥 WHERE에서 테이블 별칭을 참조하지 마세요.");

        Matcher dm = Pattern.compile("\\b[a-zA-Z_][\\w]*\\.[A-Z_][A-Z0-9_]*\\b").matcher(u);
        while (dm.find()) {
            String token = dm.group();
            if (token.matches(".*(DATE|REG|APPROVEDAT)\\b") && !ALLOWED_DATE_COL.matcher(token).find()) {
                throw new IllegalArgumentException("허용되지 않은 날짜 컬럼 표현: " + token);
            }
        }
        return s;
    }

    public static String ensureLimit(String sql, int maxRows){
        String s = stripTrailingSemicolon(stripFencesAndComments(sql).trim());
        String u = s.toUpperCase();

        if (u.contains(" FETCH FIRST ") || u.contains(" ROWNUM ") || u.contains(" OFFSET "))
            return s;

        s = s.replaceAll("(?i)LIMIT\\s+(\\d+)\\s*,\\s*(\\d+)", "OFFSET $1 ROWS FETCH NEXT $2 ROWS ONLY");
        s = s.replaceAll("(?i)LIMIT\\s+(\\d+)\\s+OFFSET\\s+(\\d+)", "OFFSET $2 ROWS FETCH NEXT $1 ROWS ONLY");
        s = s.replaceAll("(?i)LIMIT\\s+(\\d+)", "FETCH FIRST $1 ROWS ONLY");

        String u2 = s.toUpperCase();
        if (u2.contains(" FETCH FIRST ") || u2.contains(" OFFSET "))
            return s;

        return "SELECT * FROM (" + s + ") WHERE ROWNUM <= " + maxRows;
    }

    /* helpers */

    private static String stripFencesAndComments(String s){
        String t = s.trim();
        t = FENCE_SQL_START.matcher(t).replaceFirst("");
        t = FENCE_END.matcher(t).replaceFirst("");
        t = BLOCK_COMMENT.matcher(t).replaceAll(" ");
        t = LINE_COMMENT.matcher(t).replaceAll("");
        return t;
    }
    private static String stripTrailingSemicolon(String s){
        String t = s.trim();
        while (t.endsWith(";")) t = t.substring(0, t.length()-1).trim();
        return t;
    }
    private static String normalizeTableName(String token) {
        String x = token.replace("\"", "");
        int dot = x.lastIndexOf('.');
        if (dot >= 0) x = x.substring(dot + 1);
        int sp = x.indexOf(' ');
        if (sp >= 0) x = x.substring(0, sp);
        int par = x.indexOf('(');
        if (par >= 0) x = x.substring(0, par);
        return x.toUpperCase();
    }
}
