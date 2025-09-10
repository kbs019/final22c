package com.ex.final22c.data.qna;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestionDto {
    private Long qId;
    private String title;
    private String content;
    private Long qcId;
    private LocalDateTime createDate;
    private String answer;      // answer 필드 추가 (String 타입)
    private LocalDateTime answerCreateDate;  // 답변 작성일 추가 (String 타입)
    private String status;

    // answer를 받아서 설정하는 메서드
    public void setAnswer(String answerContent) {
        this.answer = answerContent;  // Answer 내용만 저장
    }
}
