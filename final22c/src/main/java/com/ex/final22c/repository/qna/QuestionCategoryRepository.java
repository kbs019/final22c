package com.ex.final22c.repository.qna;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.qna.QuestionCategory;
@Repository
public interface QuestionCategoryRepository extends JpaRepository<QuestionCategory,Long>{
    // ID 순 정렬
    List<QuestionCategory> findAllByOrderByQcIdAsc();
}
