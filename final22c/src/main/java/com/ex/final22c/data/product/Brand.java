package com.ex.final22c.data.product;

import java.util.ArrayList;
import java.util.List;

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
@Setter
@Getter
public class Brand {

    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator = "brand_seq_gen" )
    @SequenceGenerator( name = "brand_seq_gen", sequenceName = "brand_seq", allocationSize = 1 )
    @Column(name = "brandNo")
    private Long id;

    @Column(name = "brandName", length = 500, nullable = false)
    private String brandName;

    @Column(name = "imgName", length = 500)
    private String imgName;

    @Column(name = "imgPath", length = 500)
    private String imgPath;

    @OneToMany(mappedBy = "brand")
    private List<Product> productList = new ArrayList<>();
}
