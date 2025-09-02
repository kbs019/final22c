package com.ex.final22c.service.stats;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.repository.orderDetail.OrderDetailRepository;
import com.ex.final22c.repository.purchaseRepository.PurchaseDetailRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SalesStatService {
    private final OrderDetailRepository orderDetailRepository;
    private final PurchaseDetailRepository purchaseDetailRepository;

    /** 최상단: 전체 매출 타임시리즈 (unit=DAY|WEEK|MONTH) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> totalSeries(LocalDate from, LocalDate to, String unit) {
        // 날짜 범위: [from 00:00:00, to+1 00:00:00)
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        List<Object[]> rRows; // revenue series
        List<Object[]> cRows; // cogs series

        String u = (unit == null ? "DAY" : unit.toUpperCase());
        switch (u) { // ★ YEAR 분기 추가
            case "WEEK" -> {
                rRows = orderDetailRepository.revenueSeriesByWeek(fromDt, toDt);
                cRows = purchaseDetailRepository.cogsSeriesByWeek(fromDt, toDt);
            }
            case "MONTH" -> {
                rRows = orderDetailRepository.revenueSeriesByMonth(fromDt, toDt);
                cRows = purchaseDetailRepository.cogsSeriesByMonth(fromDt, toDt);
            }
            case "YEAR" -> { // ★
                rRows = orderDetailRepository.revenueSeriesByYear(fromDt, toDt);
                cRows = purchaseDetailRepository.cogsSeriesByYear(fromDt, toDt);
            }
            default -> {
                rRows = orderDetailRepository.revenueSeriesByDay(fromDt, toDt);
                cRows = purchaseDetailRepository.cogsSeriesByDay(fromDt, toDt);
            }
        }

        // 날짜 -> 값 맵 구성 (단위별 라벨 포맷 적용) ★
        Map<String, Long> rev = new LinkedHashMap<>();
        for (Object[] row : rRows) {
            String dateStr = toKey(row[0], u);
            Number v = (Number) row[1];
            long val = (v == null) ? 0L : v.longValue();
            rev.put(dateStr, val);
        }

        Map<String, Long> cogs = new LinkedHashMap<>();
        for (Object[] row : cRows) {
            String dateStr = toKey(row[0], u);
            Number v = (Number) row[1];
            long val = (v == null) ? 0L : v.longValue();
            cogs.put(dateStr, val);
        }

        // 키 합집합 정렬 후 병합
        Set<String> keys = new TreeSet<>();
        keys.addAll(rev.keySet());
        keys.addAll(cogs.keySet());

        List<Map<String, Object>> out = new ArrayList<>();
        for (String d : keys) {
            long r = rev.getOrDefault(d, 0L);
            long c = cogs.getOrDefault(d, 0L);
            long p = r - c;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            m.put("revenue", r);
            m.put("cogs", c);
            m.put("profit", p);
            out.add(m);
        }
        return out;
    }

    // --- 옵션 ---
    @Transactional(readOnly = true)
    public List<Map<String,Object>> brandOptions() {
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object[] r : orderDetailRepository.brandOptions()) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("brandNo", ((Number) r[0]).longValue());
            m.put("brandName", (String) r[1]);
            out.add(m);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<String> productCapacitiesByBrand(Long brandNo) {
        return orderDetailRepository.capacitiesByBrand(brandNo);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> productsByBrandCapacity(Long brandNo, String capacity) {
        List<Map<String,Object>> out = new ArrayList<>();
        for (Object[] r : orderDetailRepository.productsByBrandCapacity(brandNo, capacity)) {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) r[0]).longValue());
            m.put("name", (String) r[1]);
            out.add(m);
        }
        return out;
    }

    // --- 공용: rows -> List<Map> 변환 ---
    private List<Map<String,Object>> mergeSeries(List<Object[]> revRows, List<Object[]> cogsRows, String unit) {
        Map<String, Long> rev = new LinkedHashMap<>();
        for (Object[] row : revRows) {
            String k = toKey(row[0], unit);
            long v = ((Number) row[1]).longValue();
            rev.put(k, v);
        }
        Map<String, Long> cogs = new LinkedHashMap<>();
        for (Object[] row : cogsRows) {
            String k = toKey(row[0], unit);
            long v = ((Number) row[1]).longValue();
            cogs.put(k, v);
        }
        Set<String> keys = new TreeSet<>();
        keys.addAll(rev.keySet());
        keys.addAll(cogs.keySet());
        List<Map<String,Object>> out = new ArrayList<>();
        for (String d : keys) {
            long r = rev.getOrDefault(d,0L);
            long c = cogs.getOrDefault(d,0L);
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("date", d); m.put("revenue", r); m.put("cogs", c); m.put("profit", r-c);
            out.add(m);
        }
        return out;
    }

    private String toKey(Object d0, String unit) {
        LocalDate d;
        if (d0 instanceof Timestamp ts) d = ts.toLocalDateTime().toLocalDate();
        else if (d0 instanceof java.util.Date dt) d = new Timestamp(dt.getTime()).toLocalDateTime().toLocalDate();
        else d = LocalDate.parse(String.valueOf(d0).substring(0,10));
        return switch (unit.toUpperCase()) {
            case "MONTH" -> d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            case "YEAR"  -> d.format(java.time.format.DateTimeFormatter.ofPattern("yyyy"));
            default -> d.toString();
        };
    }

    // --- 상품 시리즈 ---
    @Transactional(readOnly = true)
    public List<Map<String,Object>> productSeries(Long productId, LocalDate from, LocalDate to, String unit) {
        LocalDateTime f = from.atStartOfDay(), t = to.plusDays(1).atStartOfDay();
        List<Object[]> r, c;
        switch ((unit==null?"DAY":unit.toUpperCase())) {
            case "WEEK" -> {
                r = orderDetailRepository.revenueSeriesByWeekForProduct(f,t,productId);
                c = purchaseDetailRepository.cogsSeriesByWeekForProduct(f,t,productId);
            }
            case "MONTH" -> {
                r = orderDetailRepository.revenueSeriesByMonthForProduct(f,t,productId);
                c = purchaseDetailRepository.cogsSeriesByMonthForProduct(f,t,productId);
            }
            case "YEAR" -> {
                r = orderDetailRepository.revenueSeriesByYearForProduct(f,t,productId);
                c = purchaseDetailRepository.cogsSeriesByYearForProduct(f,t,productId);
            }
            default -> {
                r = orderDetailRepository.revenueSeriesByDayForProduct(f,t,productId);
                c = purchaseDetailRepository.cogsSeriesByDayForProduct(f,t,productId);
            }
        }
        return mergeSeries(r,c,unit);
    }

    // --- 브랜드 시리즈 ---
    @Transactional(readOnly = true)
    public List<Map<String,Object>> brandSeries(Long brandNo, LocalDate from, LocalDate to, String unit) {
        LocalDateTime f = from.atStartOfDay(), t = to.plusDays(1).atStartOfDay();
        List<Object[]> r, c;
        switch ((unit==null?"DAY":unit.toUpperCase())) {
            case "WEEK" -> {
                r = orderDetailRepository.revenueSeriesByWeekForBrand(f,t,brandNo);
                c = purchaseDetailRepository.cogsSeriesByWeekForBrand(f,t,brandNo);
            }
            case "MONTH" -> {
                r = orderDetailRepository.revenueSeriesByMonthForBrand(f,t,brandNo);
                c = purchaseDetailRepository.cogsSeriesByMonthForBrand(f,t,brandNo);
            }
            case "YEAR" -> {
                r = orderDetailRepository.revenueSeriesByYearForBrand(f,t,brandNo);
                c = purchaseDetailRepository.cogsSeriesByYearForBrand(f,t,brandNo);
            }
            default -> {
                r = orderDetailRepository.revenueSeriesByDayForBrand(f,t,brandNo);
                c = purchaseDetailRepository.cogsSeriesByDayForBrand(f,t,brandNo);
            }
        }
        return mergeSeries(r,c,unit);
    }
}