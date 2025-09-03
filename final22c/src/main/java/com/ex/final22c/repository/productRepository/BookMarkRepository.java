package com.ex.final22c.repository.productRepository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.product.BookMark;

@Repository
public interface BookMarkRepository extends JpaRepository<BookMark, Long> {
    boolean existsByUser_UserNameAndProduct_Id(String userName, Long productId);

    void deleteByUser_UserNameAndProduct_Id(String userName, Long productId);

    List<BookMark> findByUser_UserNameOrderByCreateDateDesc(String userName);
}
