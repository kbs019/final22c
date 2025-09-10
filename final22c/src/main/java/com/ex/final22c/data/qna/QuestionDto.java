package com.ex.final22c.data.qna;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuestionDto {
    private String title;
    private String content;
    private Long qcId;
    private String createDate;  // createDate는 String 타입
    private String answer;      // answer 필드 추가 (String 타입)
    private String answerCreateDate;  // 답변 작성일 추가 (String 타입)
    private String status;

    // answer를 받아서 설정하는 메서드
    public void setAnswer(String answerContent) {
        this.answer = answerContent;  // Answer 내용만 저장
    }

    // answerCreateDate를 받아서 설정하는 메서드
    public void setAnswerCreateDate(String answerCreateDate) {
        this.answerCreateDate = answerCreateDate;  // Answer의 작성일을 String으로 설정
    }
}

