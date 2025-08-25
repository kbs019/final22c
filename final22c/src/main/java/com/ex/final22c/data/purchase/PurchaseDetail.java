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
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name="purchaseDetail")
public class PurchaseDetail {
	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE,generator="purchaseDetail_seq_gen")
	@SequenceGenerator(name="purchaseDetail_seq_gen", sequenceName="purchaseDetail_seq", allocationSize=1)
	@Column(name="pdId")
	private long pdId;
	
	@ManyToOne
	@JoinColumn(name="id")
	private Product product;
	
	@Column(name="qty")
	private int qty;
	
	@Column(name="totalPrice")
	private int totalPrice;
	
    @ManyToOne
    @JoinColumn(name = "purchaseId")
    private Purchase purchase;
}
