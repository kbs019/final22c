package com.ex.final22c.data.product;

import java.util.*;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq_gen")
    @SequenceGenerator( name = "product_seq_gen", sequenceName = "product_seq", allocationSize = 1 )
    private Long id;

    @Column( name = "name", length = 100 )
    private String name;

    @Column(name = "imgName", length = 500)
    private String imgName;

    @Column(name = "imgPath", length = 500)
    private String imgPath;

    @Column(name = "price")
    private int price;                                          // 정가
    
    @Column(name = "count")
    private int count;

    @Column(name = "description", length = 4000)
    private String description;

    @Column(name = "singleNote", length = 4000)
    private String singleNote;

    @Column(name = "topNote", length = 4000)
    private String topNote;

    @Column(name = "middleNote", length = 4000)
    private String middleNote;

    @Column(name = "baseNote", length = 4000)
    private String baseNote;

    // // 상품을 찜한 사용자들 - N:N (조인 테이블 생성 - 컬럼: product_id, users_id)
    // @ManyToMany
    // @JoinTable(
    //     name = "PRODUCT_ZZIMERS",
    //     joinColumns = @JoinColumn(name = "ZZIMEDPRODUCTS_ID", referencedColumnName = "id"),          // 조인테이블의 상품 FK 컬럼명
    //     inverseJoinColumns = @JoinColumn(name = "ZZIMERS_USERNO", referencedColumnName = "userNo")   // 조인테이블의 사용자 FK 컬럼명
    // )
    // private Set<Users> zzimers = new HashSet<>();

    // 상품의 리뷰들 - 1:N
    @OneToMany(mappedBy = "product")
    private List<Review> reviews = new ArrayList<>();

    @ManyToOne
    private Brand brand; // brand_id

    @ManyToOne
    private Volume volume;

    @ManyToOne
    private Grade grade;

    @ManyToOne
    private MainNote mainNote;

    @Column(name="isPicked", length = 1)
    private String isPicked;
    
    @Column(name="status", length = 20)
    private String status;

    @Column(name="sellPrice")
    private int sellPrice;

    @Column(name = "discount")
    private Double discount;                                    

    @Column(name = "costPrice")
    private int costPrice;                                      // 원가 (발주 시)
    
    // 기본값 및 파생값 계산
    // insert 시 매번 실행되는 메서드 실행 ( count 가 0 이라면, count 에 10 대입 (default) / isPicked 가 null 이라면, 기본값 "N" 을 대입 / 이후, 가격 계산해주기 )
    @PrePersist         
    public void prePersist() {
        if (this.isPicked == null) { this.isPicked = "N"; } // DB DEFAULT 대신 자바에서 보장
        if ( this.imgPath == null ) { this.imgPath = "/img/"; }
        if (this.status == null) {this.status = "wait";}
        if ( this.costPrice == 0 ) { this.costPrice = (int) Math.floor(this.price * 0.05); }

        recalcPrices();
    }

    // price 의 가격을 수정할 수 있기 때문에 -- 각 행의 정보를 수정할 때마다 price 값에 맞춰서 판매가 / 매입가 수정
    @PreUpdate
    public void preUpdate() {
        recalcPrices();
    }

    // price 변수에 대한 set() 를 수정
    public void setPrice(int price) {
        this.price = price;
        this.costPrice = (int) Math.floor(this.price * 0.05);
        recalcPrices();
    }

    // discount 변수에 대한 set() 를 수정
    public void setDiscount(Double discount) {
        this.discount = discount;
        recalcPrices();
    }

    // price 컬럼에 값이 들어오는 동시에 판매가와 매입가를 설정
    private void recalcPrices() {
        // 기존의 가격이 없거나 0 이하라면 -- sellPrice = 0
        if( this.price <= 0 ){
            this.sellPrice = 0;
            return;
        }

        double effectiveRate;
        if( this.discount != null ){
            double d = this.discount.doubleValue();
            // 0.0 ~ 1.0 또는 1.0 초과의 값은 % 로 간주하여 모두 지원한다.

            if( d > 1.0 ){      // d 가 1.0 초과라면, % 로 간주
                d = d / 100.0;      // 100.0 으로 나눈다.
            }

            d = Math.max( 0.0, Math.min( d, 1.0 ) );

            effectiveRate = 1.0 - d;    // 최종 판매 배율 = 1.0 - (할인률)
        } else {
            effectiveRate = 1.0;        // discount 가 없다면, 정가에 판매 (할인 미적용)
        }

        this.sellPrice = (int) Math.floor(this.price * effectiveRate);
    }
}