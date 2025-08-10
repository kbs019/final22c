package com.ex.final22c.data;

import org.hibernate.annotations.DynamicInsert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "perfume")
@DynamicInsert              // DB 에서 정한 Default 값을 활용하고 싶다면 설정
public class Perfume {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="perfume_seq_gen")
    @SequenceGenerator(name = "perfume_seq_gen", sequenceName="perfume_seq", allocationSize = 1)
    private int perfumeNo;

    @Column(length = 500)
    private String imgName;

    @Column(length = 500)        // columnDefinition 은 이미 DB 에서 default 를 설정해주었기 때문에 사용하지 않아도 된다.
    private String imgPath;

    @Column(length = 100)
    private String perfumeName;

    @Column(length = 100)
    private String brand;

    private int price;

    private int count;

    @Column(length = 4000)
    private String description;

    @Column(length = 500)
    private String grade;

    @Column(length = 500)
    private String mainNote;

    @Column(length = 500)
    private String singleNote;

    @Column(length = 500)
    private String topNote;

    @Column(length = 500)
    private String middleNote;

    @Column(length = 500)
    private String baseNote;

    @Column(length = 1)
    private String isPicked;

    private int sellPrice;
    private int buyPrice;

    // 기본값 및 파생값 계산
    // insert 시 매번 실행되는 메서드 실행 ( count 가 0 이라면, count 에 10 대입 (default) / isPicked 가 null 이라면, 기본값 "N" 을 대입 / 이후, 가격 계산해주기 )
    @PrePersist         
    public void prePersist() {
        if (this.count == 0) { this.count = 10; }       // DB DEFAULT 대신 자바에서 보장
        if (this.isPicked == null) { this.isPicked = "N"; } // DB DEFAULT 대신 자바에서 보장
        recalcPrices();
    }

    // price 의 가격을 수정할 수 있기 때문에 -- 각 행의 정보를 수정할 때마다 price 값에 맞춰서 판매가 / 매입가 수정
    @PreUpdate
    public void preUpdate() {
        recalcPrices();
    }

    // price 변수에 대안 set() 를 수정
    public void setPrice(int price) {
        this.price = price;
        recalcPrices();
    }

    // price 컬럼에 값이 들어오는 동시에 판매가와 매입가를 설정
    private void recalcPrices() {
        if (this.price != 0) {
            this.sellPrice = (int)Math.floor(this.price * 0.9);     // 팔 때, 10% 할인             (관리자 입장)
            this.buyPrice  = (int)Math.floor(this.price * 0.7);     // 살 때, 30% 싼 가격에 구매    (관리자 입장)
        }
    }
}
