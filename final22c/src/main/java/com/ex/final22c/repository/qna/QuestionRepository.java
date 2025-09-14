package com.ex.final22c.repository.qna;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.qna.Question;
@Repository
public interface QuestionRepository extends JpaRepository<Question,Long>{

    long countByStatus(String status);

	List<Question> findAllByOrderByCreateDateDesc();

    List<Question> findByWriterUserName(String userName);

    @Query("SELECT q FROM Question q LEFT JOIN FETCH q.answer WHERE q.qId = :questionId")
    Optional<Question> findByIdWithAnswer(@Param("questionId") Long questionId);
}
