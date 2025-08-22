package com.ex.final22c.data.refund;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.ex.final22c.data.order.Order;
import com.ex.final22c.data.payment.Payment;
import com.ex.final22c.data.user.Users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Entity;

@Entity
@Table(name = "refund")
@Getter
@Setter
public class Refund {

    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator = "refund_seq_gen" )
    @SequenceGenerator( name = "refund_seq_gen", sequenceName = "refund_seq", allocationSize = 1 )
    @Column( name = "refundId" )
    private Long refundId;                                      // 환불 식별 번호

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderId", nullable = false)
    private Order order;                                        // 어떤 주문의 환불인가
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userId", nullable = false)
    private Users user;                                         // 환불 요청자

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefundDetail> details = new ArrayList<>();

    @Column(length = 20, nullable = false)
    private String status;                                      // 환불신청 -- 환불완료

    @Column(name = "totalRefundAmount")
    private int totalRefundAmount;        // 환급 총액(스냅샷)

    @Column(name = "reasonText")
    private String reasonText;            // 환불 사유

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paymentId", nullable = false)
    private Payment payment;            // payment.tid 를 꺼내어 사용할 예정

    @Column(name = "pgRefundId", length = 100)
    private String pgRefundId;                      // pg 환불 취소 요청 식별자

    @Lob
    @Column(name = "pgPayloadJson")
    private String pgPayloadJson;                   // pg 응답 원문(JSON) - CLOB 매핑

    @CreationTimestamp
    @Column(name = "createDate")
    private LocalDateTime createDate;   // 환불 신청 시각

    @UpdateTimestamp
    @Column(name = "updateDate")
    private LocalDateTime updateDate;   // 환급 완료 시각

    public void addDetail(RefundDetail d) {
        this.details.add(d);
        d.setRefund(this);
    }
}
