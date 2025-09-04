package com.ex.final22c.data.user;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_preference")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {
    
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_preference_seq_gen")
    @SequenceGenerator(name = "user_preference_seq_gen", sequenceName = "user_preference_seq", allocationSize = 1)
    @Column(name = "preferenceId")
    private Long preferenceId;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userNo", nullable = false)
    private Users user;

    @Column(name = "userName")
    private String userName;

    // 설문조사 답변 (JSON 형태로 저장)
    @Lob
    @Column(name = "surveyAnswers")
    private String surveyAnswers;
    
    
    // AI 분석 결과 (JSON 형태로 저장)
    @Lob
    @Column(name = "aiAnalysis")
    private String aiAnalysis;
    
    // 추천된 상품 ID들 (콤마로 구분)
    @Lob
    @Column(name = "recommendedProducts")
    private String recommendedProducts;
    
    @CreationTimestamp
    @Column(name = "createdAt")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;
}