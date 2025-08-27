package com.ex.final22c.controller.chat;

import java.util.List;
import java.util.Map;


// ai 응답 DTO
public record AiResult(
	    String answer,
	    String sql,
	    List<Map<String,Object>> rows,
	    ChartPayload chart
	) {
	    public record ChartPayload(
	        List<String> labels,
	        List<Number> values,
	        List<Number> quantities,
	        String valueCol,
	        String title,

	        String type,          // "bar" | "line" | "pie" | "doughnut"
	        Boolean horizontal,   // true면 가로 막대 (indexAxis:'y')
	        String format         // "currency" | "count" | "percent" (y축/툴팁 포맷 힌트)
	    ) {}
	}
