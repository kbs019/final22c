package com.ex.final22c.service.chat;

public record ChartSpec(
	    String sql,             // 단일 SELECT. 결과 컬럼: label, value, (optional) quantity
	    String title,           // 차트 제목(선택)
	    String valueColLabel,   // y축/범례 라벨(선택)
	    Integer topN,           // 상위 N(선택)
	    String type,            // "bar" | "line" | "pie" | "doughnut" (선택)
	    String format           // "currency" | "count" | "percent" (선택)
	) {}