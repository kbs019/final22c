package com.ex.final22c;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.mybatis.spring.annotation.MapperScan(
    basePackages = { 
        "com.ex.final22c.repository.productMapper", 
        "com.ex.final22c.repository.cart"
    },
    annotationClass = org.apache.ibatis.annotations.Mapper.class
)
public class Final22cApplication {
    public static void main(String[] args) {
        SpringApplication.run(Final22cApplication.class, args);
    }
}
