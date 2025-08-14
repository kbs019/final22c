package com.ex.final22c.data.product;

import java.util.*;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Review {
    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator = "review_seq_gen" )
    @SequenceGenerator( name = "review_seq_gen", sequenceName = "grade_seq", allocationSize = 1 )
    private Long id;

    // 상품에 대한 리뷰 - N:1 (FK : product_id)
    @ManyToOne
    private Product product;

    // 리뷰를 쓴 사용자 - N:1 (FK : writer_id)
    @ManyToOne
    private Users writer;

    @Column(length = 4000)
    private String content;

    private int rating;

    // 리뷰에 공감한 사용자들 - N:N (조인 테이블 생성 - 컬럼: review_id, users_id)
    @ManyToMany
    private Set<Users> likers = new HashSet<>();
}
