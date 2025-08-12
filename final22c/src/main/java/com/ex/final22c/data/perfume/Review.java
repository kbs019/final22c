package com.ex.final22c.data.perfume;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "review", uniqueConstraints = @UniqueConstraint(name = "uk_review_user_perfume", columnNames = {"userNo", "perfumeNo"}))
public class Review {
    
    // reviewNo 라는 시퀀스를 대입한 PK
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="review_seq_gen")
    @SequenceGenerator(name = "review_seq_gen", sequenceName="review_seq", allocationSize = 1)
    @Column(name="reviewNo")
    private int reviewNo;

    // 리뷰 내용
    @Column(length = 4000, nullable = false)
    private String content;

    // 리뷰 작성일
    @CreationTimestamp
    private LocalDateTime createDate;

    // 평정 (최소 1 ~ 최대 10)
    @Min(1)
    @Max(10)
    @Column(nullable = false)
    private int gradePoint;

    // 하나의 향수에 여러 리뷰가 달랄 수 있음
    @ManyToOne
    @JoinColumn(name="perfumeNo", nullable=false)
    private Perfume perfume;

    // 한명의 유저는 하나의 리뷰만 작성할 수 있음
    @ManyToOne
    @JoinColumn(name="userNo", nullable=false)
    private Users user;

    // 여러개의 공감이 하나의 리뷰에 있을 수 있다.
    @OneToMany(mappedBy="review", cascade = CascadeType.REMOVE)
    private Set<Rec> recs = new HashSet<>();
}
