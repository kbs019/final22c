package com.ex.final22c.repository.productRepository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.product.BookMark;

@Repository
public interface BookMarkRepository extends JpaRepository<BookMark, Long> {

}
