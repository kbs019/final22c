package com.ex.final22c.data;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
// @Table(
//     name = "USER",
//     uniqueConstraints = {
//         @UniqueConstraint(columnNames = "userName"),
//         @UniqueConstraint(columnNames = "email"),
//         @UniqueConstraint(columnNames = "phone")
//     }
// )
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Users {

    // 유저 번호 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userNo", nullable = false)
    private Long userNo;

    // 아이디 : jpa라서 userName 이라는 이름으로 사용 UK / not null
    @Column(name = "userName", length = 30, nullable = false)
    private String userName; // UK

    // 비밀번호 : 20자 이하로 설정 / not null
    @Column(name = "password", length = 20, nullable = false)
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
    @Column(name = "phone", length = 11, unique = true)
    private String phone;

    // 가입일 : sysdate로 설정 / not null
    @Column(name = "reg", nullable = false)
    private LocalDate reg; // sysdate

    // 상태 : active, inactive, banned 등
    @Column(name = "status", length = 20)
    private String status = "active";

    // 차단일 : sysdate로 설정
    @Column(name = "banReg")
    private LocalDate banReg;

    // 역할 : user, admin 등
    @Column(name = "role", length = 20)
    private String role = "user";

    // 로그인 타입
    @Column(name = "loginType", length = 10)
    private String loginType;
}
