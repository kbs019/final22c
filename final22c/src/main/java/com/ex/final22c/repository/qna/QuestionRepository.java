package com.ex.final22c.repository.qna;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.qna.Question;
@Repository
public interface QuestionRepository extends JpaRepository<Question,Long>{
	List<Question> findAllByOrderByCreateDateDesc();
}
