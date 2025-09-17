package com.ex.final22c.service.qna;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ex.final22c.data.qna.Question;
import com.ex.final22c.data.qna.QuestionCategory;
import com.ex.final22c.data.qna.QuestionDto;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.qna.AnswerRepository;
import com.ex.final22c.repository.qna.QuestionCategoryRepository;
import com.ex.final22c.repository.qna.QuestionRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class QnaService {
	private final AnswerRepository answerRepository;
	private final QuestionCategoryRepository questionCategoryRepository;
	private final QuestionRepository questionRepository;
	private final UserRepository usersRepository;
	
	// 질문 카테고리 목록
	public List<QuestionCategory> getAllCategories(){
		return questionCategoryRepository.findAllByOrderByQcIdAsc();
	}
	
	// 질문 등록
    public void saveQuestion(QuestionDto dto, String username) {
        // 작성자 조회
        Users writer = usersRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("사용자 없음"));

        // 선택한 카테고리 조회
        QuestionCategory qc = questionCategoryRepository.findById(dto.getQcId())
                .orElseThrow(() -> new RuntimeException("문의 유형 없음"));

        // Question 엔티티 생성 및 값 세팅
        Question question = new Question();
        question.setTitle(dto.getTitle());
        question.setContent(dto.getContent());
        question.setWriter(writer);
        question.setQc(qc);
        question.setStatus("wait");

        // 저장
        questionRepository.save(question);
    }
}
