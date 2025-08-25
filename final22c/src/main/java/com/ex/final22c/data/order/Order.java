package com.ex.final22c.data.order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ex.final22c.ShippingSnapshotJsonConverter;
import com.ex.final22c.data.payment.dto.ShipSnapshotReq;
import com.ex.final22c.data.user.Users;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "orders")
@Getter @Setter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="order_seq_gen")
    @SequenceGenerator(name="order_seq_gen", sequenceName="order_seq", allocationSize=1)
    @Column(name="orderId")
    private long orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userNo")
    private Users user;

    @Column(name="usedPoint", nullable=false)
    private int usedPoint;          // 사용 포인트(승인/취소 처리에 필요)

    @Column(name="totalAmount", nullable=false)
    private int totalAmount;        // 결제요청 시 최종 결제금(스냅샷)

    @Column(name="status", length=20, nullable=false)
    private String status;           // PENDING / PAID / CANCELED / FAILED / CONFIRMED / REQUESTED / REFUNDED

    @Column(name="regDate", nullable=false)
    private LocalDateTime regDate;

    @OneToMany(mappedBy="order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDetail> details = new ArrayList<>();
    
    @Column(name="deliveryStatus")
    private String deliveryStatus;      // ORDERED / SHIPPING / DELIVERED 
    
    @Lob
    @Column(name = "shippingSnapshot")
    @Convert(converter = ShippingSnapshotJsonConverter.class)
    private ShipSnapshotReq shippingSnapshot;

    @PrePersist
    public void prePersist() {
        if (regDate == null) regDate = LocalDateTime.now();
        if (status == null)  status  = "PENDING";
    }

    public void addDetail(OrderDetail d) {
        d.setOrder(this);
        details.add(d);
    }
    /* ===== 상태 헬퍼 ===== */
    public boolean isPending()  { return "PENDING".equalsIgnoreCase(status); }
    public boolean isPaid()     { return "PAID".equalsIgnoreCase(status); }
    public boolean isCanceled() { return "CANCELED".equalsIgnoreCase(status); }
    public boolean isFailed()   { return "FAILED".equalsIgnoreCase(status); }

    public void markPaid()      { this.status = "PAID"; }
    public void markCanceled()  { this.status = "CANCELED"; } // 결제 후 환불/취소
    public void markFailed()    { this.status = "FAILED"; }   // 결제창 단계 실패/중단
}
