package com.ex.final22c.data.product;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table( name = "stock", uniqueConstraints = { @UniqueConstraint(name = "uk_stock_serial", columnNames = "serial_no") } )
public class Stock {

    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator = "stock_seq_gen" )
    @SequenceGenerator( name="stock_seq_gen", sequenceName = "stock_seq", allocationSize = 1 )
    @Column( name = "stockId" )
    private Long stockId;               // 재고 식별 번호

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id", nullable = false)
    private Product product;            // 상품 식별 번호

    @NaturalId(mutable = false)
    @Column( name = "serialNo", nullable = false, unique = true )
    private String serialNo;            // 시리얼 번호

    @Column( name = "status", length = 100, nullable = false )
    private String status;              // 상태 ( 판매중 / 결제완료 / 판매완료 / 환불됨 )   --  결제취소 시, 판매중으로 변경

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        if (this.serialNo == null || this.serialNo.isBlank()) {
            this.serialNo = java.util.UUID.randomUUID().toString();
        }
        if( this.status == null || this.status.isBlank() ){
            this.status = "active";
        }
    }
}
