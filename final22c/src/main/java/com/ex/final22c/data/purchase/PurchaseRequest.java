package com.ex.final22c.data.purchase;

import com.ex.final22c.data.product.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name="purchaseRequest")
@NoArgsConstructor
public class PurchaseRequest {
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "purchaseRequest_seq_gen")
	@SequenceGenerator(name = "purchaseRequest_seq_gen", sequenceName = "purchaseRequest_seq", allocationSize = 1)
    @Column(name = "prId")
	private long prId;
	
	@ManyToOne
    @JoinColumn(name = "id")
    private Product product;  // Product FK

    private int qty;
}