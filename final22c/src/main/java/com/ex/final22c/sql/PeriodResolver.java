package com.ex.final22c.sql;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PeriodResolver {
    private PeriodResolver(){}

    // KST 고정
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static ResolvedPeriod resolveFromUtterance(String text){
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        ZonedDateTime now = ZonedDateTime.now(KST);

        // 올해 / 금년 / this year / ytd
        if (t.contains("올해") || t.contains("금년") || t.contains("this year") || t.contains("ytd")) {
            ZonedDateTime start = now.withDayOfYear(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
            ZonedDateTime end   = start.plusYears(1);
            String label = now.getYear() + "년";
            return new ResolvedPeriod(start.toLocalDateTime(), end.toLocalDateTime(), label);
        }

        // 이번 달
        if (t.contains("이번달") || t.contains("이달") || t.contains("this month")) {
            ZonedDateTime start = now.withDayOfMonth(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
            ZonedDateTime end   = start.plusMonths(1);
            String label = now.getYear() + "년 " + now.getMonthValue() + "월";
            return new ResolvedPeriod(start.toLocalDateTime(), end.toLocalDateTime(), label);
        }

        // 최근 N개월
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("최근\\s*(\\d+)\\s*개월").matcher(t);
        if (m.find()){
            int n = Math.max(1, Math.min(24, Integer.parseInt(m.group(1))));
            ZonedDateTime end = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS).withHour(0).withMinute(0).withSecond(0).withNano(0).plusDays(1);
            ZonedDateTime start = end.minusMonths(n);
            String label = "최근 " + n + "개월";
            return new ResolvedPeriod(start.toLocalDateTime(), end.toLocalDateTime(), label);
        }

        // 기본: 이번 달
        ZonedDateTime start = now.withDayOfMonth(1).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        ZonedDateTime end   = start.plusMonths(1);
        String label = now.getYear() + "년 " + now.getMonthValue() + "월";
        return new ResolvedPeriod(start.toLocalDateTime(), end.toLocalDateTime(), label);
    }

    public record ResolvedPeriod(java.time.LocalDateTime start, java.time.LocalDateTime end, String label) {}
}
