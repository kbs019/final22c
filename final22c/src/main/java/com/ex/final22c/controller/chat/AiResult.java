package com.ex.final22c.controller.chat;

import java.util.List;
import java.util.Map;


// ai 응답 DTO
public record AiResult(
    String answer,                    // 요약/설명
    String sql,                       // 실행한 SQL(보기용)
    List<Map<String,Object>> rows,    // 표 데이터(선택)
    ChartPayload chart                // 차트 페이로드(있으면 프런트가 Chart.js로 그림)
) {
    public record ChartPayload(
        List<String> labels,
        List<Number> values,
        List<Number> quantities,      // 툴팁용(선택)
        String valueCol,              // y축/범례 라벨 (예: "순매출(원)")
        String title                  // 차트 제목
    ) {}
}