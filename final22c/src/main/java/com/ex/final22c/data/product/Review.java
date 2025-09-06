package com.ex.final22c.data.product;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Review {

    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator = "review_seq_gen" )
    @SequenceGenerator( name = "review_seq_gen", sequenceName = "review_seq", allocationSize = 1 )
    @Column(name = "reviewId")
    private Long reviewId;

    // 상품 (N:1)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    // 작성자 (N:1)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Users writer;

    @Column(length = 4000, nullable = false)
    private String content;

    @CreationTimestamp
    private LocalDateTime createDate;

    @Column(name = "status")
    private String status;                          // ACTIVE - HIDDEN

    // 1~5
    @Column(name = "rating", nullable = false)
    private int rating;

    // 공감(좋아요) N:N
    @ManyToMany
    private Set<Users> likers = new HashSet<>();

    @PrePersist
    public void addReview(){
        if( this.status == null ){ this.status = "ACTIVE"; }
    }
}
