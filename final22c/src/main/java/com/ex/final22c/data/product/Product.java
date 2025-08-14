package com.ex.final22c.data.product;

import java.util.*;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
    private int price;
    
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

    // 상품을 찜한 사용자들 - N:N (조인 테이블 생성 - 컬럼: product_id, users_id)
    @ManyToMany
    private Set<Users> zzimers = new HashSet<>();

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
}