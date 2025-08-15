package com.ex.final22c.repository.productRepsotory;

import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.stereotype.Repository;

import com.ex.final22c.data.product.Product;

// @Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

}
