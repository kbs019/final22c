package com.ex.final22c.data.product;

import java.time.LocalDateTime;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
    name = "restockAlert",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_restockAlert_user_product", columnNames = {"id", "userNo", "status"})
    }
)
public class RestockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "restockAlert_seq_gen")
    @SequenceGenerator(name = "restockAlert_seq_gen", sequenceName = "restockAlert_seq", allocationSize = 1)
    @Column(name = "restockAlertId")
    private Long restockAlertId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userNo")
    private Users user;

    // 신청 시각
    @Column(name = "requestedReg", nullable = false)
    private LocalDateTime requestedReg;

    // 통지 완료 시각(없으면 미통지)
    @Column(name = "notifiedReg")
    private LocalDateTime notifiedReg;

    // 통지 대상 연락처 스냅샷(선택) — 이후 번호 변경/탈퇴에도 증적 보존용
    @Column(name = "phoneSnapshot", length = 32)
    private String phoneSnapshot;

    // 상태(UNIQUE 키에 들어가므로 중복 방지에 사용)
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @PrePersist
    void onCreate() {
        if (this.requestedReg == null) this.requestedReg = LocalDateTime.now();
        if (this.status == null) this.status = "REQUESTED";
    }
}
