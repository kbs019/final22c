package com.ex.final22c.data.order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.ex.final22c.ShippingSnapshotJsonConverter;
import com.ex.final22c.data.payment.dto.ShipSnapshotReq;
import com.ex.final22c.data.refund.Refund;
import com.ex.final22c.data.user.Users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "orders")
@Getter @Setter
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="order_seq_gen")
    @SequenceGenerator(name="order_seq_gen", sequenceName="order_seq", allocationSize=1)
    
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
    
    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Refund refund;
    
    @Column(name="deliveryStatus")
    private String deliveryStatus;      // ORDERED / SHIPPING / DELIVERED 
    
    @Column(name = "confirmMileage", nullable = false)
    private int confirmMileage;

    @Lob
    @Column(name = "shippingSnapshot")
    @Convert(converter = ShippingSnapshotJsonConverter.class)
    private ShipSnapshotReq shippingSnapshot;

    // 장바구니 삭제용
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
    	name = "orderSelectedCart",					// 테이블명
    	joinColumns = @JoinColumn(name = "orderId") // FK 컬렴명
	)
    @Column(name = "cartDetailId")					// 값 칼럼명
    private List<Long> selectedCartDetailIds = new ArrayList<>();
    
    public void selectedCartDetailIds(Collection<Long> ids) {
    	selectedCartDetailIds.clear();
    	if (ids != null) selectedCartDetailIds.addAll(ids);
    }
    
    @PrePersist
    public void prePersist() {
        if (regDate == null) regDate = LocalDateTime.now();
        if (status == null)  status  = "PENDING";
        if ( this.confirmMileage == 0 ) { this.confirmMileage = 0; }
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
