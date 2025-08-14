package com.ex.final22c.data.user;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.Review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
// @Table(
// name = "USER",
// uniqueConstraints = {
// @UniqueConstraint(columnNames = "userName"),
// @UniqueConstraint(columnNames = "email"),
// @UniqueConstraint(columnNames = "phone")
// }
// )
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Users {

    // 유저 번호 PK
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "users_seq_gen")
    @SequenceGenerator(name = "users_seq_gen", sequenceName = "users_seq", allocationSize = 1)
    @Column(name = "userNo", nullable = false)
    private Long userNo;

    // 아이디 : jpa라서 userName 이라는 이름으로 사용 UK / not null
    @Column(name = "userName", length = 50, nullable = false)
    private String userName; // UK

    // 비밀번호 : 20자 이하로 설정 / not null
    @Column(name = "password", length = 150, nullable = false)
    private String password;

    // 이메일 : 50자 이하로 설정 / UK / not null
    @Column(name = "email", length = 50, unique = true)
    private String email;

    // 이름 : 30자 이하로 설정
    @Column(name = "name", length = 30)
    private String name;

    // 생년월일 : 8자리로 설정 / not null
    @Column(name = "birth")
    private LocalDate birth;

    // 성별 : 1: 남자, 2: 여자
    @Column(name = "gender", length = 10)
    private String gender;

    // 통신사 : skt, kt, lgu+ 등
    @Column(name = "telecom", length = 30)
    private String telecom; // skt, kt, lgu+ 등

    // 전화번호 : 11자리로 설정 / UK
    @Column(name = "phone", length = 30, unique = true)
    private String phone;

    // 가입일 : sysdate로 설정 / not null
    @Column(name = "reg", nullable = false)
    @CreationTimestamp
    private LocalDate reg; // sysdate

    // 상태 : active, inactive, banned 등
    @Column(name = "status", length = 20)
    private String status;

    // 차단일 : sysdate로 설정
    @Column(name = "banReg")
    private LocalDate banReg;

    // 역할 : user, admin 등
    @Column(name = "role", length = 20)
    private String role;

    // 로그인 타입
    @Column(name = "loginType", length = 10)
    private String loginType;

    // 카카오 UID( 로그인시 db 확인용 )
    @Column(name = "kakaoId", length = 50, unique = true)
    private String kakaoId;

    // 마일리지 : 누적 포인트 (기본 0)
    @Column(name = "mileage", nullable = false)
    private Integer mileage; // 또는 Integer

    // 사용자가 찜한 상품들 - (조인 테이블 생성 - 컬럼: users_id, product_id)
    @ManyToMany(mappedBy = "zzimers")
    @Builder.Default
    private Set<Product> zzimedProducts = new HashSet<>();

    // 사용자가 공감한 리뷰들 - (조인 테이블 생성 - 컬럼: users_id, review_id)
    @ManyToMany(mappedBy = "likers")
    @Builder.Default
    private Set<Review> likedReviews = new HashSet<>();

    // 사용자가 작성한 리뷰들
    @OneToMany(mappedBy = "writer")
    @Builder.Default
    private List<Review> reviews = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (this.role == null) {
            this.role = "user";
        }
        if (this.status == null) {
            this.status = "active";
        }
        if (this.mileage == null) {
            this.mileage = 0;
        }
    }
}