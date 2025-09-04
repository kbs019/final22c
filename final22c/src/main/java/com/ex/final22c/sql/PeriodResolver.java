package com.ex.final22c.sql;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PeriodResolver {
    private PeriodResolver(){}

    // KST 고정
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 키워드 패턴
    private static final Pattern P_RECENT_DAYS   = Pattern.compile("최근\\s*(\\d+)\\s*일");
    private static final Pattern P_RECENT_WEEKS  = Pattern.compile("최근\\s*(\\d+)\\s*(?:주|주일)");
    private static final Pattern P_RECENT_MONTHS = Pattern.compile("최근\\s*(\\d+)\\s*(?:개월|달)");
    private static final Pattern P_RECENT_YEARS  = Pattern.compile("최근\\s*(\\d+)\\s*년");

    private static final Pattern P_YEAR_MONTH = Pattern.compile("(\\d{4})[\\./-]?\\s*(0?[1-9]|1[0-2])(?:\\s*월)?");
    private static final Pattern P_YEAR_ONLY  = Pattern.compile("(\\d{4})\\s*년?");
    private static final Pattern P_MONTH_ONLY = Pattern.compile("(?<!\\d)(0?[1-9]|1[0-2])\\s*월(?!\\d)"); // 연도 미지정 → 올해로

    // 분기: 2024 Q3 / Q2 2023 / 2023년 4분기 / 4분기 2023
    private static final Pattern P_QUARTER_1 = Pattern.compile("(\\d{4})\\s*[ -]?Q([1-4])", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_QUARTER_2 = Pattern.compile("Q([1-4])\\s*(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_QUARTER_3 = Pattern.compile("(\\d{4})\\s*년\\s*([1-4])\\s*분기");
    private static final Pattern P_QUARTER_4 = Pattern.compile("([1-4])\\s*분기\\s*(\\d{4})");

    private static final Pattern P_ALLTIME = Pattern.compile(
            "(?i)(전체\\s*기간|전체|누적|전기간|all\\s*-?time|total|lifetime)"
    );

    public static ResolvedPeriod resolveFromUtterance(String text){
        String t = text == null ? "" : text.toLowerCase(Locale.ROOT);
        ZonedDateTime now = ZonedDateTime.now(KST);

        // 0) 전체기간
        if (P_ALLTIME.matcher(t).find()) return allTime();

        // 1) today / yesterday
        if (containsAny(t, "오늘", "today")) {
            return ofDay(now.toLocalDate(), "오늘");
        }
        if (containsAny(t, "어제", "yesterday")) {
            return ofDay(now.minusDays(1).toLocalDate(), "어제");
        }

        // 2) this/last week
        if (containsAny(t, "이번주", "금주", "this week")) {
            LocalDate[] range = weekRange(now, 0);
            return ofDateRange(range[0], range[1].plusDays(1), "이번 주");
        }
        if (containsAny(t, "지난주", "저번주", "last week", "previous week")) {
            LocalDate[] range = weekRange(now, -1);
            return ofDateRange(range[0], range[1].plusDays(1), "지난 주");
        }

        // 3) this/last month
        if (containsAny(t, "이번달", "이달", "this month")) {
            LocalDate start = now.withDayOfMonth(1).toLocalDate();
            LocalDate end   = start.plusMonths(1);
            return ofDateRange(start, end, labelMonth(start));
        }
        if (containsAny(t, "지난달", "전월", "last month", "previous month")) {
            LocalDate start = now.withDayOfMonth(1).minusMonths(1).toLocalDate();
            LocalDate end   = start.plusMonths(1);
            return ofDateRange(start, end, labelMonth(start));
        }

        // 4) this/last quarter
        if (containsAny(t, "이번분기", "this quarter")) {
            LocalDate start = quarterStart(now.toLocalDate());
            return ofDateRange(start, start.plusMonths(3), labelQuarter(start));
        }
        if (containsAny(t, "지난분기", "last quarter", "previous quarter")) {
            LocalDate start = quarterStart(now.toLocalDate()).minusMonths(3);
            return ofDateRange(start, start.plusMonths(3), labelQuarter(start));
        }

        // 5) this year / last year / ytd
        if (containsAny(t, "올해", "금년", "this year")) {
            LocalDate start = LocalDate.of(now.getYear(), 1, 1);
            LocalDate end   = start.plusYears(1);
            return ofDateRange(start, end, now.getYear() + "년");
        }
        if (containsAny(t, "작년", "지난해", "last year", "previous year")) {
            LocalDate start = LocalDate.of(now.getYear() - 1, 1, 1);
            LocalDate end   = start.plusYears(1);
            return ofDateRange(start, end, start.getYear() + "년");
        }
        if (containsAny(t, "ytd")) { // 올해 1/1 ~ 오늘(포함)
            LocalDate start = LocalDate.of(now.getYear(), 1, 1);
            LocalDate end   = now.toLocalDate().plusDays(1);
            return ofDateRange(start, end, now.getYear() + "년 YTD");
        }

        // 6) 최근 N일/주/개월/년
        Matcher md = P_RECENT_DAYS.matcher(t);
        if (md.find()) {
            int n = clamp(md.group(1), 1, 365);
            LocalDate end = now.toLocalDate().plusDays(1);
            return ofDateRange(end.minusDays(n), end, "최근 " + n + "일");
        }
        Matcher mw = P_RECENT_WEEKS.matcher(t);
        if (mw.find()) {
            int n = clamp(mw.group(1), 1, 104);
            LocalDate end = now.toLocalDate().plusDays(1);
            return ofDateRange(end.minusWeeks(n), end, "최근 " + n + "주");
        }
        Matcher mm = P_RECENT_MONTHS.matcher(t);
        if (mm.find()) {
            int n = clamp(mm.group(1), 1, 60);
            LocalDate end = now.toLocalDate().plusDays(1);
            return ofDateRange(end.minusMonths(n), end, "최근 " + n + "개월");
        }
        Matcher my = P_RECENT_YEARS.matcher(t);
        if (my.find()) {
            int n = clamp(my.group(1), 1, 10);
            LocalDate end = now.toLocalDate().plusDays(1);
            return ofDateRange(end.minusYears(n), end, "최근 " + n + "년");
        }

        // 7) 분기 지정 (2024 Q3 / Q2 2023 / 2023년 4분기 / 4분기 2023)
        Matcher q1 = P_QUARTER_1.matcher(t);
        if (q1.find()) {
            int year = Integer.parseInt(q1.group(1));
            int q    = Integer.parseInt(q1.group(2));
            LocalDate start = quarterStart(LocalDate.of(year, 1, 1).withMonth((q - 1) * 3 + 1));
            return ofDateRange(start, start.plusMonths(3), year + "년 " + q + "분기");
        }
        Matcher q2 = P_QUARTER_2.matcher(t);
        if (q2.find()) {
            int q    = Integer.parseInt(q2.group(1));
            int year = Integer.parseInt(q2.group(2));
            LocalDate start = quarterStart(LocalDate.of(year, 1, 1).withMonth((q - 1) * 3 + 1));
            return ofDateRange(start, start.plusMonths(3), year + "년 " + q + "분기");
        }
        Matcher q3 = P_QUARTER_3.matcher(t);
        if (q3.find()) {
            int year = Integer.parseInt(q3.group(1));
            int q    = Integer.parseInt(q3.group(2));
            LocalDate start = LocalDate.of(year, (q - 1) * 3 + 1, 1);
            return ofDateRange(start, start.plusMonths(3), year + "년 " + q + "분기");
        }
        Matcher q4 = P_QUARTER_4.matcher(t);
        if (q4.find()) {
            int q    = Integer.parseInt(q4.group(1));
            int year = Integer.parseInt(q4.group(2));
            LocalDate start = LocalDate.of(year, (q - 1) * 3 + 1, 1);
            return ofDateRange(start, start.plusMonths(3), year + "년 " + q + "분기");
        }

        // 8) 절대: YYYY-MM / YYYY.MM / YYYY년 M월
        Matcher ym = P_YEAR_MONTH.matcher(t);
        if (ym.find()) {
            int year  = Integer.parseInt(ym.group(1));
            int month = Integer.parseInt(ym.group(2));
            LocalDate start = LocalDate.of(year, month, 1);
            return ofDateRange(start, start.plusMonths(1), labelMonth(start));
        }

        // 9) 절대: YYYY
        Matcher yonly = P_YEAR_ONLY.matcher(t);
        if (yonly.find()) {
            int year = Integer.parseInt(yonly.group(1));
            LocalDate start = LocalDate.of(year, 1, 1);
            return ofDateRange(start, start.plusYears(1), year + "년");
        }

        // 10) 절대: M월 (연도 미지정 → 올해)
        Matcher mon = P_MONTH_ONLY.matcher(t);
        if (mon.find()) {
            int m = Integer.parseInt(mon.group(1));
            int year = now.getYear();
            LocalDate start = LocalDate.of(year, m, 1);
            return ofDateRange(start, start.plusMonths(1), labelMonth(start));
        }

        // 기본: 이번 달
        LocalDate start = now.withDayOfMonth(1).toLocalDate();
        LocalDate end   = start.plusMonths(1);
        return ofDateRange(start, end, labelMonth(start));
    }

    /* ===== Helpers ===== */

    public static ResolvedPeriod allTime() {
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end   = LocalDate.now(KST).plusDays(1);
        return ofDateRange(start, end, "전체 기간");
    }

    private static ResolvedPeriod ofDay(LocalDate day, String label){
        return ofDateRange(day, day.plusDays(1), label);
    }

    private static ResolvedPeriod ofDateRange(LocalDate start, LocalDate endExclusive, String label){
        LocalDateTime s = start.atStartOfDay();
        LocalDateTime e = endExclusive.atStartOfDay();
        return new ResolvedPeriod(s, e, label);
    }

    private static LocalDate[] weekRange(ZonedDateTime now, int offsetWeeks){
        WeekFields wf = WeekFields.ISO; // 월~일
        LocalDate base = now.toLocalDate().plusWeeks(offsetWeeks);
        LocalDate monday = base.with(wf.dayOfWeek(), 1);
        LocalDate sunday = base.with(wf.dayOfWeek(), 7);
        return new LocalDate[]{monday, sunday};
        // endExclusive는 호출부에서 +1 day 처리
    }

    private static LocalDate quarterStart(LocalDate any){
        int q = ((any.getMonthValue()-1)/3) + 1;
        int startMonth = (q-1)*3 + 1;
        return LocalDate.of(any.getYear(), startMonth, 1);
    }

    private static String labelMonth(LocalDate firstDay){
        return firstDay.getYear() + "년 " + firstDay.getMonthValue() + "월";
    }
    private static String labelQuarter(LocalDate firstDay){
        int q = ((firstDay.getMonthValue()-1)/3) + 1;
        return firstDay.getYear() + "년 " + q + "분기";
    }

    private static boolean containsAny(String s, String... keys){
        for (String k : keys) if (s.contains(k.toLowerCase(Locale.ROOT))) return true;
        return false;
    }
    private static int clamp(String n, int min, int max){
        int v = Integer.parseInt(n);
        return Math.max(min, Math.min(max, v));
    }

    public record ResolvedPeriod(LocalDateTime start, LocalDateTime end, String label) {}
}
