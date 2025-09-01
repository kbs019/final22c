package com.ex.final22c.service.stats;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
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

    // ★ 단위별 날짜 포맷터(라벨 안정화)
    private static final DateTimeFormatter F_DAY = DateTimeFormatter.ISO_DATE; // 2025-09-01
    private static final DateTimeFormatter F_MONTH = DateTimeFormatter.ofPattern("yyyy-MM"); // 2025-09
    private static final DateTimeFormatter F_YEAR = DateTimeFormatter.ofPattern("yyyy"); // 2025

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

    // // ★ 단위별 키 문자열 생성(리포지토리에서 TRUNC된 값이 Timestamp/Date로 오더라도 안전)
    // private String toKey(Object d0, String unit) {
    //     LocalDate d;
    //     if (d0 instanceof Timestamp ts)
    //         d = ts.toLocalDateTime().toLocalDate();
    //     else if (d0 instanceof java.util.Date dt)
    //         d = new Timestamp(dt.getTime()).toLocalDateTime().toLocalDate();
    //     else
    //         d = LocalDate.parse(String.valueOf(d0).substring(0, 10)); // "2025-09-01..." 방지

    //     return switch (unit.toUpperCase()) {
    //         case "MONTH" -> d.format(F_MONTH);
    //         case "YEAR" -> d.format(F_YEAR);
    //         // WEEK은 DB가 주 시작일(월요일 등)로 TRUNC해서 주므로 그냥 날짜 문자열로 둠 (프론트에서 주차 라벨로 변환 가능)
    //         default -> d.format(F_DAY);
    //     };
    // }

    /** 하단: 상품별 총매출(이익) 맵(Map<productId, profit>) */
    @Transactional(readOnly = true)
    public Map<Long, Long> productProfit(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        Map<Long, Long> revenue = new HashMap<>();
        List<Object[]> rRows = orderDetailRepository.revenueByProduct(fromDt, toDt);
        for (Object[] row : rRows) {
            Number id = (Number) row[0];
            Number v = (Number) row[1];
            long val = (v == null) ? 0L : v.longValue();
            revenue.put(id.longValue(), val);
        }

        Map<Long, Long> cogs = new HashMap<>();
        List<Object[]> cRows = purchaseDetailRepository.cogsByProduct(fromDt, toDt);
        for (Object[] row : cRows) {
            Number id = (Number) row[0];
            Number v = (Number) row[1];
            long val = (v == null) ? 0L : v.longValue();
            cogs.put(id.longValue(), val);
        }

        Set<Long> keys = new HashSet<>();
        keys.addAll(revenue.keySet());
        keys.addAll(cogs.keySet());

        Map<Long, Long> profit = new HashMap<>();
        for (Long k : keys) {
            long r = revenue.getOrDefault(k, 0L);
            long c = cogs.getOrDefault(k, 0L);
            profit.put(k, r - c);
        }
        return profit;
    }

    /** 하단: 브랜드별 총매출(이익) 맵(Map<brandNo, profit>) */
    @Transactional(readOnly = true)
    public Map<Long, Long> brandProfit(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        Map<Long, Long> revenue = new HashMap<>();
        List<Object[]> rRows = orderDetailRepository.revenueByBrand(fromDt, toDt);
        for (Object[] row : rRows) {
            Number id = (Number) row[0];
            Number v = (Number) row[1];
            long val = (v == null) ? 0L : v.longValue();
            revenue.put(id.longValue(), val);
        }

        Map<Long, Long> cogs = new HashMap<>();
        List<Object[]> cRows = purchaseDetailRepository.cogsByBrand(fromDt, toDt);
        for (Object[] row : cRows) {
            Number id = (Number) row[0];
            Number v = (Number) row[1];
            long val = (v == null) ? 0L : v.longValue();
            cogs.put(id.longValue(), val);
        }

        Set<Long> keys = new HashSet<>();
        keys.addAll(revenue.keySet());
        keys.addAll(cogs.keySet());

        Map<Long, Long> profit = new HashMap<>();
        for (Long k : keys) {
            long r = revenue.getOrDefault(k, 0L);
            long c = cogs.getOrDefault(k, 0L);
            profit.put(k, r - c);
        }
        return profit;
    }

    /** 전체 총매출(= 순매출 합 - 매입원가 합) */
    @Transactional(readOnly = true)
    public long totalProfit(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.plusDays(1).atStartOfDay();

        Long r = orderDetailRepository.revenueTotal(fromDt, toDt);
        Long c = purchaseDetailRepository.cogsTotal(fromDt, toDt);
        long rv = (r == null) ? 0L : r.longValue();
        long cg = (c == null) ? 0L : c.longValue();
        return rv - cg;
    }

    /** 한 번에 내려주는 편의 응답(합계 + 상품별 + 브랜드별) */
    @Transactional(readOnly = true)
    public Map<String, Object> allStats(LocalDate from, LocalDate to) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalProfit", totalProfit(from, to));
        out.put("products", productProfit(from, to));
        out.put("brands", brandProfit(from, to));
        return out;
    }

    // ======================================== 상품별 - 브랜드별 통계 =================================================

    @Transactional(readOnly = true)
    public List<Map<String, Object>> productProfitNamed(LocalDate from, LocalDate to, String q, int limit) {
        LocalDateTime f = from.atStartOfDay(), t = to.plusDays(1).atStartOfDay();

        // revenue: id,name,sum
        List<Object[]> rs = orderDetailRepository.revenueByProductWithName(f, t, q);
        // cogs: id,name,sum
        List<Object[]> cs = purchaseDetailRepository.cogsByProductWithName(f, t, q);

        // id -> {name, revenue, cogs}
        Map<Long, Map<String, Object>> acc = new HashMap<>();
        for (Object[] r : rs) {
            Long id = ((Number) r[0]).longValue();
            String name = (String) r[1];
            long v = ((Number) (r[2] == null ? 0 : r[2])).longValue();
            acc.computeIfAbsent(id, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", name);
                m.put("revenue", 0L);
                m.put("cogs", 0L);
                return m;
            });
            acc.get(id).put("revenue", v);
        }
        for (Object[] r : cs) {
            Long id = ((Number) r[0]).longValue();
            String name = (String) r[1];
            long v = ((Number) (r[2] == null ? 0 : r[2])).longValue();
            acc.computeIfAbsent(id, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("name", name);
                m.put("revenue", 0L);
                m.put("cogs", 0L);
                return m;
            });
            acc.get(id).put("cogs", v);
        }

        // to list (name, profit)
        return acc.values().stream()
                .map(m -> {
                    long profit = ((Number) m.get("revenue")).longValue() - ((Number) m.get("cogs")).longValue();
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("name", (String) m.get("name"));
                    o.put("profit", profit);
                    return o;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("profit"), (Long) a.get("profit")))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> brandProfitNamed(LocalDate from, LocalDate to, Long brandNo) {
        LocalDateTime f = from.atStartOfDay(), t = to.plusDays(1).atStartOfDay();

        List<Object[]> rs = orderDetailRepository.revenueByBrandWithName(f, t, brandNo);
        List<Object[]> cs = purchaseDetailRepository.cogsByBrandWithName(f, t, brandNo);

        Map<Long, Map<String, Object>> acc = new HashMap<>();
        for (Object[] r : rs) {
            Long no = ((Number) r[0]).longValue();
            String name = (String) r[1];
            long v = ((Number) (r[2] == null ? 0 : r[2])).longValue();
            acc.computeIfAbsent(no, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("brandName", name);
                m.put("revenue", 0L);
                m.put("cogs", 0L);
                return m;
            });
            acc.get(no).put("revenue", v);
        }
        for (Object[] r : cs) {
            Long no = ((Number) r[0]).longValue();
            String name = (String) r[1];
            long v = ((Number) (r[2] == null ? 0 : r[2])).longValue();
            acc.computeIfAbsent(no, k -> {
                Map<String, Object> m = new HashMap<>();
                m.put("brandName", name);
                m.put("revenue", 0L);
                m.put("cogs", 0L);
                return m;
            });
            acc.get(no).put("cogs", v);
        }

        return acc.values().stream()
                .map(m -> {
                    long profit = ((Number) m.get("revenue")).longValue() - ((Number) m.get("cogs")).longValue();
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("brandName", (String) m.get("brandName"));
                    o.put("profit", profit);
                    return o;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("profit"), (Long) a.get("profit")))
                .toList();
    }

    // 검색
    @Transactional(readOnly = true)
    public List<Map<String,Object>> searchProductsByName(String q) {
        return orderDetailRepository.searchProductsByName(q).stream().map(r -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", ((Number)r[0]).longValue());
            m.put("name", (String) r[1]);
            return m;
        }).toList();
    }

    // 상품 타임시리즈 (unit 동일 로직 재사용)
    @Transactional(readOnly = true)
    public List<Map<String,Object>> totalSeriesForProduct(Long productId, LocalDate from, LocalDate to, String unit) {
        LocalDateTime f = from.atStartOfDay(), t = to.plusDays(1).atStartOfDay();
        List<Object[]> rRows, cRows;
        switch ((unit==null?"DAY":unit.toUpperCase())) {
            case "WEEK"  -> { rRows = orderDetailRepository.revenueSeriesByWeekForProduct(productId, f, t);
                            cRows = purchaseDetailRepository.cogsSeriesByWeekForProduct(productId, f, t); }
            case "MONTH" -> { rRows = orderDetailRepository.revenueSeriesByMonthForProduct(productId, f, t);
                            cRows = purchaseDetailRepository.cogsSeriesByMonthForProduct(productId, f, t); }
            case "YEAR"  -> { rRows = orderDetailRepository.revenueSeriesByYearForProduct(productId, f, t);
                            cRows = purchaseDetailRepository.cogsSeriesByYearForProduct(productId, f, t); }
            default      -> { rRows = orderDetailRepository.revenueSeriesByDayForProduct(productId, f, t);
                            cRows = purchaseDetailRepository.cogsSeriesByDayForProduct(productId, f, t); }
        }
        return mergeSeries(rRows, cRows, unit);
    }

    // 브랜드 타임시리즈
    @Transactional(readOnly = true)
    public List<Map<String,Object>> totalSeriesForBrand(Long brandNo, LocalDate from, LocalDate to, String unit) {
        LocalDateTime f = from.atStartOfDay(), t = to.plusDays(1).atStartOfDay();
        List<Object[]> rRows, cRows;
        switch ((unit==null?"DAY":unit.toUpperCase())) {
            case "WEEK"  -> { rRows = orderDetailRepository.revenueSeriesByWeekForBrand(brandNo, f, t);
                            cRows = purchaseDetailRepository.cogsSeriesByWeekForBrand(brandNo, f, t); }
            case "MONTH" -> { rRows = orderDetailRepository.revenueSeriesByMonthForBrand(brandNo, f, t);
                            cRows = purchaseDetailRepository.cogsSeriesByMonthForBrand(brandNo, f, t); }
            case "YEAR"  -> { rRows = orderDetailRepository.revenueSeriesByYearForBrand(brandNo, f, t);
                            cRows = purchaseDetailRepository.cogsSeriesByYearForBrand(brandNo, f, t); }
            default      -> { rRows = orderDetailRepository.revenueSeriesByDayForBrand(brandNo, f, t);
                            cRows = purchaseDetailRepository.cogsSeriesByDayForBrand(brandNo, f, t); }
        }
        return mergeSeries(rRows, cRows, unit);
    }

    // // 브랜드 옵션
    // @Transactional(readOnly = true)
    // public List<Map<String,Object>> brandOptions() {
    //     return orderDetailRepository.findAllBrands().stream().map(r -> {
    //         Map<String,Object> m = new LinkedHashMap<>();
    //         m.put("brandNo", ((Number)r[0]).longValue());
    //         m.put("brandName", (String) r[1]);
    //         return m;
    //     }).toList();
    // }

    // // 공통 머지 (기존 totalSeries와 동일 컨셉)
    // private List<Map<String,Object>> mergeSeries(List<Object[]> rRows, List<Object[]> cRows, String unit) {
    //     Map<String, Long> rev = new LinkedHashMap<>();
    //     for (Object[] row : rRows) rev.put(toKey(row[0], unit), ((Number)(row[1]==null?0:row[1])).longValue());
    //     Map<String, Long> cgs = new LinkedHashMap<>();
    //     for (Object[] row : cRows) cgs.put(toKey(row[0], unit), ((Number)(row[1]==null?0:row[1])).longValue());

    //     Set<String> keys = new TreeSet<>(); keys.addAll(rev.keySet()); keys.addAll(cgs.keySet());
    //     List<Map<String,Object>> out = new ArrayList<>();
    //     for (String k : keys) {
    //         long r = rev.getOrDefault(k, 0L), c = cgs.getOrDefault(k, 0L);
    //         Map<String,Object> m = new LinkedHashMap<>();
    //         m.put("date", k); m.put("revenue", r); m.put("cogs", c); m.put("profit", r-c);
    //         out.add(m);
    //     }
    //     return out;
    // }

// ========================================= 23 : 38 ===================================================
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