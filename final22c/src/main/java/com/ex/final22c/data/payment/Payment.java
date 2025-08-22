package com.ex.final22c.data.payment;

import com.ex.final22c.data.order.Order;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_seq_gen")
    @SequenceGenerator(name = "payment_seq_gen", sequenceName = "payment_seq", allocationSize = 1)
    @Column(name = "paymentId")
    private Long paymentId; // 결제 고유 번호 (PK)

    // 주문 FK
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderId")
    private Order order;

    @Column(name = "amount", nullable = false)
    private Integer amount; // 결제 금액

    @Column(name = "status", length = 20, nullable = false)
    private String status; // 성공, 실패, 취소 등

    @Column(name = "tid", length = 100, unique=true)
    private String tid; // 카카오페이 결제 고유번호

    @Column(name = "aid", length = 100)
    private String aid; // 카카오페이 승인 고유번호

    @Column(name = "approvedAt")
    private LocalDateTime approvedAt; // 승인 시간

    @Column(name = "reg", nullable = false)
    private LocalDate reg; // 결제 요청 시각

    @PrePersist
    public void prePersist() {
        if (this.reg == null) {
            this.reg = LocalDate.now();
        }
    }
}
