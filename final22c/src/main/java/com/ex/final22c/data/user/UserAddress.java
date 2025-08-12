package com.ex.final22c.data.user;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_address")
@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="userAddress_seq_gen")
    @SequenceGenerator(name = "userAddress_seq_gen", sequenceName="userAddress_seq", allocationSize = 1)
    @Column(nullable = false)
    private Long userNo;             // FK

    @Column(length = 30, nullable = false)
    private String addrName;         // 배송지명 (예: 집, 회사)

    @Column(length = 30, nullable = false)
    private String recipient;        // 수령인

    @Column(length = 10, nullable = false)
    private String zonecode;         // 다음API: zonecode (새 우편번호)

    @Column(length = 100, nullable = false)
    private String roadAddress;      // 다음API: roadAddress

    @Column(length = 100, nullable = false)
    private String detailAddress;    // 상세주소

    @Column(length = 1, nullable = false)
    @Builder.Default
    private String isDefault;
        @PrePersist
    public void prePersist() {
        if (this.isDefault == null) {
            this.isDefault = "N";     // 'Y' 기본배송지, 기본값 'N'
        }
    }
}