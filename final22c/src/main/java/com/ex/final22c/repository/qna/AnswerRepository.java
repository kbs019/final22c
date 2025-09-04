package com.ex.final22c.repository.qna;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.qna.Answer;

@Repository
public interface AnswerRepository extends JpaRepository<Answer,Long>{

}
