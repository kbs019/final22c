package com.ex.final22c.repository.qna;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.qna.Question;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    long countByStatus(String status);

    List<Question> findAllByOrderByCreateDateDesc();

    // 단순 리스트(기존 사용분 유지)
    List<Question> findByWriterUserName(String userName);

    // 페이징 + 최신순(Controller에서 Sort.DESC(createDate)로 전달)
    List<Question> findByWriter_UserName(String userName);

    Page<Question> findByWriter_UserName(String userName, Pageable pageable);
    Page<Question> findByWriter_UserNameAndAnswerIsNull(String userName, Pageable pageable);
    Page<Question> findByWriter_UserNameAndAnswerIsNotNull(String userName, Pageable pageable);

    // 상세(with answer) – 단일 버전만 유지
    @Query("""
           select q
           from Question q
           left join fetch q.answer a
           where q.qId = :qId
           """)
    Optional<Question> findByIdWithAnswer(@Param("qId") Long qId);
}
