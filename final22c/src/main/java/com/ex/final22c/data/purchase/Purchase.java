package com.ex.final22c.data.purchase;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import lombok.Getter;
import lombok.Setter;


@Entity
@Getter
@Setter
public class Purchase {
	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE,generator="purchase_seq_gen")
	@SequenceGenerator(name="purchase_seq_gen", sequenceName="purchase_seq", allocationSize=1)
	@Column(name="purchaseId")
	private long purchaseId;
	
	@OneToMany(mappedBy="purchase", cascade = CascadeType.ALL)
	private List<PurchaseDetail> purchaseDetail = new ArrayList<>();
	
	@Column(name="count")
	private int count;
	
	@Column(name="totalPrice")
	private int totalPrice;
	
	@Column(name="reg")
	private LocalDateTime reg;
}
