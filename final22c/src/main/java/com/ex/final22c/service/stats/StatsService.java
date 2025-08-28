package com.ex.final22c.service.stats;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ex.final22c.repository.orderDetail.OrderDetailRepository;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatsService {
    private final OrderDetailRepository orderDetailRepository;
    private static final List<String> OK = List.of("PAID", "CONFIRMED", "REFUNDED");
    private static final ZoneId Z = ZoneId.of("Asia/Seoul");

    // unit: DAY/WEEK/MONTH/YEAR
    public Map<String, Object> buildSeries(Long id, String unit,
            @Nullable String date, @Nullable Integer year) {
        return switch (unit) {
            case "DAY" -> seriesDay(id, parseDateOrToday(date));
            case "WEEK" -> seriesWeek(id, parseYearMonthOrNow(date)); // date="YYYY-MM"
            case "MONTH" -> seriesMonth(id, (year != null) ? Year.of(year) : Year.now(Z));
            case "YEAR" -> seriesYear(id, Year.now(Z));
            default -> seriesDay(id, LocalDate.now(Z));
        };
    }

    // [일간] 기준일 ±3일(총 7일)
    private Map<String, Object> seriesDay(Long id, LocalDate base) {
        LocalDate start = base.minusDays(3);
        LocalDate endEx = base.plusDays(4);
        var rows = orderDetailRepository.dailyTotalsWithStatuses(id, OK, start.atStartOfDay(), endEx.atStartOfDay());

        // DB 결과 -> 날짜별 합계 맵
        Map<LocalDate, Integer> m = new HashMap<>();
        for (Object[] r : rows)
            m.put(toLocalDate(r[0]), ((Number) r[1]).intValue());

        // 라벨/값(0채움)
        List<String> labels = new ArrayList<>(7);
        List<Integer> data = new ArrayList<>(7);
        for (LocalDate d = start; d.isBefore(endEx); d = d.plusDays(1)) {
            labels.add(d.getMonthValue() + "/" + d.getDayOfMonth());
            data.add(m.getOrDefault(d, 0));
        }
        return Map.of("unit", "DAY", "labels", labels, "data", data,
                "meta", Map.of("center", base.toString()));
    }

    // [주간] 선택 월 내 1주~5주 (월요일 시작, 최대 5주만 표기)
    private Map<String, Object> seriesWeek(Long id, YearMonth ym) {
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEndEx = ym.plusMonths(1).atDay(1);
        var rows = orderDetailRepository.weeklyTotalsWithStatuses(id, OK,
                monthStart.atStartOfDay(), monthEndEx.atStartOfDay());

        // 키: 주 시작(월요일)
        Map<LocalDate, Integer> byWeek = new HashMap<>();
        for (Object[] r : rows)
            byWeek.put(toLocalDate(r[0]), ((Number) r[1]).intValue());

        // 해당 월 안의 "월요일들"을 나열 → 1~5주로 자르기
        List<LocalDate> weekStarts = new ArrayList<>();
        LocalDate w = monthStart.with(java.time.DayOfWeek.MONDAY);
        if (w.isBefore(monthStart))
            w = w.plusWeeks(1);
        while (w.isBefore(monthEndEx)) {
            weekStarts.add(w);
            w = w.plusWeeks(1);
        }
        if (weekStarts.size() > 5)
            weekStarts = weekStarts.subList(0, 5); // 요구사항에 맞춰 5주 컷

        List<String> labels = new ArrayList<>();
        List<Integer> data = new ArrayList<>();
        int idx = 1;
        for (LocalDate ws : weekStarts) {
            labels.add(idx++ + "주");
            data.add(byWeek.getOrDefault(ws, 0));
        }
        // 5주 미만인 달 0 채움
        while (labels.size() < 5) {
            labels.add(labels.size() + 1 + "주");
            data.add(0);
        }

        return Map.of("unit", "WEEK", "labels", labels, "data", data,
                "meta", Map.of("month", ym.toString())); // "YYYY-MM"
    }

    // [월간] 선택 연도 1~12월
    private Map<String, Object> seriesMonth(Long id, Year year) {
        LocalDate s = year.atMonth(1).atDay(1);
        LocalDate e = year.plusYears(1).atMonth(1).atDay(1);
        var rows = orderDetailRepository.monthlyTotalsWithStatuses(id, OK, s.atStartOfDay(), e.atStartOfDay());

        Map<YearMonth, Integer> byMonth = new HashMap<>();
        for (Object[] r : rows) {
            LocalDate d = toLocalDate(r[0]);
            byMonth.put(YearMonth.from(d), ((Number) r[1]).intValue());
        }
        List<String> labels = new ArrayList<>(12);
        List<Integer> data = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            labels.add(m + "월");
            data.add(byMonth.getOrDefault(YearMonth.of(year.getValue(), m), 0));
        }
        return Map.of("unit", "MONTH", "labels", labels, "data", data,
                "meta", Map.of("year", year.getValue()));
    }

    // [연간] 기준연도 포함 최근 5년
    private Map<String, Object> seriesYear(Long id, Year baseYear) {
        int endY = baseYear.getValue();
        int startY = endY - 4;
        LocalDate s = LocalDate.of(startY, 1, 1);
        LocalDate e = LocalDate.of(endY + 1, 1, 1);
        var rows = orderDetailRepository.yearlyTotalsWithStatuses(id, OK, s.atStartOfDay(), e.atStartOfDay());

        Map<Integer, Integer> byYear = new HashMap<>();
        for (Object[] r : rows) {
            byYear.put(toLocalDate(r[0]).getYear(), ((Number) r[1]).intValue());
        }
        List<String> labels = new ArrayList<>(5);
        List<Integer> data = new ArrayList<>(5);
        for (int y = startY; y <= endY; y++) {
            labels.add(String.valueOf(y));
            data.add(byYear.getOrDefault(y, 0));
        }
        return Map.of("unit", "YEAR", "labels", labels, "data", data);
    }

    private LocalDate parseDateOrToday(@Nullable String s) {
        return (s == null || s.isBlank()) ? LocalDate.now(Z) : LocalDate.parse(s);
    }

    private YearMonth parseYearMonthOrNow(@Nullable String s) {
        return (s == null || s.isBlank()) ? YearMonth.now(Z) : YearMonth.parse(s);
    }

    private LocalDate toLocalDate(Object o) {
        if (o instanceof java.sql.Timestamp ts)
            return ts.toLocalDateTime().toLocalDate();
        if (o instanceof java.sql.Date d)
            return d.toLocalDate();
        if (o instanceof java.util.Date d)
            return d.toInstant().atZone(Z).toLocalDate();
        throw new IllegalArgumentException("date type: " + o);
    }
}
