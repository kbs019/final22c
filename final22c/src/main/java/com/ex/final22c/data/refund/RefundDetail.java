package com.ex.final22c.data.refund;

import com.ex.final22c.data.order.OrderDetail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "refundDetail")
@Getter
@Setter
public class RefundDetail {

    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator = "refundDetail_seq_gen" )
    @SequenceGenerator( name = "refundDetail_seq_gen", sequenceName = "refundDetail_seq", allocationSize = 1 )
    @Column( name = "refundDetailId" )
    private Long refundDetailId;                                // 환불상세내역 식별 번호

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Refund refund;                                      // 환불내역 참조

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderDetailId", unique = true)
    private OrderDetail orderDetail;                            // 어떤 주문상세내역에 대한 환불인가

    @Column(name = "quantity")
    private int quantity;                                       // 한 행에대한 환불요청수량

    @Column(name = "refundQty")
    private int refundQty;                                      // 몇 개를 환불했는가 (승인 숫자)
    
    @Column(name = "unitRefundAmount")
    private int unitRefundAmount;                               // 개당 환불액 (orderDetail 안에 있는 sellPrice)

    @Column(name = "detailRefundAmount")
    private int detailRefundAmount;                             // 환불 합계 (refundQty * unitRefundAmount)
}