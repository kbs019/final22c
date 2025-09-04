package com.ex.final22c.sql;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM SQL → 대시보드 규칙 정렬:
 * - ORDERS 테이블이 포함된 쿼리에만 날짜/상태 필터 적용
 * - WHERE의 날짜 관련 술어를 제거(특히 REGDATE 비교/Between, TRUNC/EXTRACT/ADD_MONTHS/SYSDATE 등)
 * - WHERE에는 최종적으로 2줄만 남김:  {QUALIFIER}.REGDATE >= :start AND {QUALIFIER}.REGDATE < :end
 * - SELECT/GROUP BY의 TRUNC는 보존(버킷팅)
 * - ORDERS의 별칭/테이블명을 동적으로 감지(없으면 ORDERS 사용)
 */
public final class SqlNormalizer {
    private SqlNormalizer(){}

    /* 코드펜스/백틱 제거 */
    private static final Pattern FENCE_START   = Pattern.compile("(?is)^```\\s*sql\\s*");
    private static final Pattern FENCE_END     = Pattern.compile("(?s)```\\s*$");
    private static final Pattern ANY_BACKTICKS = Pattern.compile("`+");

    /* WHERE 블록 */
    private static final Pattern WHERE_BLOCK = Pattern.compile("(?is)\\bWHERE\\b([\\s\\S]*?)(?=\\bGROUP\\s+BY\\b|\\bORDER\\s+BY\\b|$)");

    /* ORDERS 별칭 감지: FROM/JOIN 모두 지원 */
    private static final Pattern ORDERS_ALIAS_FROM = Pattern.compile("(?is)\\bFROM\\s+ORDERS\\s+(?:AS\\s+)?([A-Z_][\\w$]*)\\b");
    private static final Pattern ORDERS_ALIAS_JOIN = Pattern.compile("(?is)\\bJOIN\\s+ORDERS\\s+(?:AS\\s+)?([A-Z_][\\w$]*)\\b");

    /* ORDERS 테이블 존재 여부 검사 */
    private static final Pattern ORDERS_TABLE_CHECK = Pattern.compile("(?is)\\b(?:FROM|JOIN)\\s+ORDERS\\b");

    /**
     * ORDERS 테이블이 포함되어 있는지 확인
     */
    private static boolean hasOrdersTable(String sql) {
        if (sql == null) return false;
        return ORDERS_TABLE_CHECK.matcher(sql).find();
    }

    private static String detectOrdersQualifier(String sqlUpper) {
        Matcher m1 = ORDERS_ALIAS_FROM.matcher(sqlUpper);
        if (m1.find()) return m1.group(1).toUpperCase();
        Matcher m2 = ORDERS_ALIAS_JOIN.matcher(sqlUpper);
        if (m2.find()) return m2.group(1).toUpperCase();
        // 별칭이 없고 테이블명으로 접근한다면 ORDERS 사용
        if (sqlUpper.contains("ORDERS.")) return "ORDERS";
        // 그 외엔 기존 관습(alias o)에 맞춰 o를 시도
        return "O";
    }

    private static boolean hasStatusFilter(String sUpper, String qualifier){
        String q = Pattern.quote(qualifier.toUpperCase());
        return Pattern.compile("(?is)\\b" + q + "\\.STATUS\\s+IN\\s*\\(").matcher(sUpper).find()
            || Pattern.compile("(?is)\\bORDERS\\.STATUS\\s+IN\\s*\\(").matcher(sUpper).find();
    }

    public static String enforceDateRangeWhere(String sql, boolean ensureStatusFilter) {
        if (sql == null) return "";

        // 0) 펜스/백틱 제거
        String s = sql.trim();
        s = FENCE_START.matcher(s).replaceFirst("");
        s = FENCE_END.matcher(s).replaceFirst("");
        s = ANY_BACKTICKS.matcher(s).replaceAll("");
        String sUpper = s.toUpperCase();

        // ✅ 핵심 수정: ORDERS 테이블이 없으면 그대로 반환
        if (!hasOrdersTable(s)) {
            return s;
        }

        // 1) ORDERS qualifier 결정
        String qualifier = detectOrdersQualifier(sUpper);           // 예: "O" 또는 "ORDERS"
        String qQuoted  = Pattern.quote(qualifier);

        // 2) WHERE 블록 추출
        Matcher m = WHERE_BLOCK.matcher(s);
        if (!m.find()) {
            StringBuilder out = new StringBuilder(s);
            out.append(" WHERE 1=1");
            if (ensureStatusFilter && !hasStatusFilter(sUpper, qualifier)) {
                out.append(" AND ").append(qualifier).append(" STATUS".replace(" ", "."))
                   .append(" IN ('PAID','CONFIRMED','REFUNDED')");
            }
            out.append(" AND ").append(qualifier).append(" REGDATE".replace(" ", "."))
               .append(" >= :start AND ").append(qualifier).append(" REGDATE".replace(" ", "."))
               .append(" < :end");
            return out.toString();
        }

        String whereBody = m.group(1);

        // 3) 날짜 술어 제거 (qualifier 또는 ORDERS 양쪽 모두 매치)
        String regdatePrefix = "(?:(?:" + qQuoted + ")|ORDERS)\\."; //  (QUALIFIER.|ORDERS.)
        // BETWEEN
        whereBody = whereBody.replaceAll("(?is)\\b(?:AND|OR)?\\s*"+regdatePrefix+"REGDATE\\s+BETWEEN[\\s\\S]*?(?=(?:\\bAND\\b|\\bOR\\b|$))", "");
        // 비교(=, >, <, >=, <=)
        whereBody = whereBody.replaceAll("(?is)\\b(?:AND|OR)?\\s*"+regdatePrefix+"REGDATE\\s*[=><]{1,2}\\s*[\\s\\S]*?(?=(?:\\bAND\\b|\\bOR\\b|$))", "");
        // TRUNC(REGDATE) 비교
        whereBody = whereBody.replaceAll("(?is)\\b(?:AND|OR)?\\s*TRUNC\\s*\\(\\s*"+regdatePrefix+"REGDATE[\\s\\S]*?\\)\\s*[=><]{1,2}[\\s\\S]*?(?=(?:\\bAND\\b|\\bOR\\b|$))", "");
        // EXTRACT(...) 포함 술어
        whereBody = whereBody.replaceAll("(?is)\\b(?:AND|OR)?\\s*[^\\)]*EXTRACT\\s*\\([^)]*\\)[\\s\\S]*?(?=(?:\\bAND\\b|\\bOR\\b|$))", "");

        // 꼬리 정리
        whereBody = whereBody.replaceAll(",\\s*\\)", ")");

        // 4) STATUS 보장
        if (ensureStatusFilter && !hasStatusFilter(whereBody.toUpperCase(), qualifier) && !hasStatusFilter(sUpper, qualifier)) {
            whereBody += " AND " + qualifier + ".STATUS IN ('PAID','CONFIRMED','REFUNDED')";
        }

        // 5) 표준 반열림 범위 추가
        whereBody += " AND " + qualifier + ".REGDATE >= :start AND " + qualifier + ".REGDATE < :end";

        // 6) 접속사/공백 정리
        whereBody = whereBody
                .replaceAll("(?i)\\bAND\\s*(?:AND|OR)\\b", " AND ")
                .replaceAll("(?i)\\bOR\\s*(?:AND|OR)\\b", " OR ")
                .replaceAll("\\(\\s*\\)", "")
                .replaceAll("\\s{2,}", " ")
                .trim()
                .replaceAll("(?i)^(?:AND|OR)\\s+", "");

        // 7) WHERE 교체
        StringBuilder out = new StringBuilder(s);
        out.replace(m.start(1), m.end(1), " " + whereBody + " ");
        return out.toString();
    }
}