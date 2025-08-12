package com.ex.final22c.data.order;

import java.time.LocalDateTime;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Getter
@Setter
@Table(name = "orders")
public class Order {
	@Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="order_seq_gen")
    @SequenceGenerator(name = "order_seq_gen", sequenceName="order_seq", allocationSize = 1)
	@Column(name = "orderId")
	private int orderId; // 주문 번호, 시퀀스

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "userNo")
	private Users user;
	
	@Column(name = "totalAmount", nullable=false)
	private int totalAmount; // 총 금액
	
	@Column(name = "status", length = 20, nullable = false)
    private String status; // 결제대기, 완료
	
	@Column(name = "regDate", nullable = false)
	private LocalDateTime regDate; // 주문 일시
	
	@Column(name = "trackingNum", length = 100, unique = true)
	private String trackingNum; // 송장번호 (8자리)
	
	@PrePersist
    public void prePersist() {
        if (this.regDate == null) {
            this.regDate = LocalDateTime.now();
        }
        if (this.status == null) {
            this.status = "PENDING";
        }
    }
}
