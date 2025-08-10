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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userNo", nullable = false)
    private Long userNo; // PK

    @Column(name = "userName", length = 30, nullable = false)
    private String userName; // UK

    @Column(name = "password", length = 20, nullable = false)
    private String password;

    @Column(name = "email", length = 50, unique = true)
    private String email;

    @Column(name = "name", length = 30)
    private String name;

    @Column(name = "birth")
    private LocalDate birth;

    @Column(name = "gender", length = 10)
    private String gender; // 1: 남자, 2: 여자

    @Column(name = "telecom", length = 30)
    private String telecom; // skt, kt, lgu+ 등

    @Column(name = "phone", length = 11, unique = true)
    private String phone;

    @Column(name = "reg", nullable = false)
    private LocalDate reg; // sysdate

    @Column(name = "status", length = 20)
    private String status = "active";

    @Column(name = "banReg")
    private LocalDate banReg;

    @Column(name = "role", length = 20)
    private String role = "user";

    @Column(name = "loginType", length = 10)
    private String loginType;
}
