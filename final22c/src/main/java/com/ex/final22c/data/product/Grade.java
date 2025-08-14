package com.ex.final22c.data.product;

import java.util.*;

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
public class Grade {

    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator = "grade_seq_gen" )
    @SequenceGenerator( name = "grade_seq_gen", sequenceName = "grade_seq", allocationSize = 1 )
    private int gradeNo;

    @Column(name = "gradeName", length = 500)
    private String gradeName;

    @OneToMany(mappedBy = "grade")
    private List<Product> productList = new ArrayList<>();
}
