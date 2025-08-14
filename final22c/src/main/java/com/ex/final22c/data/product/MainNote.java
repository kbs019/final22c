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
public class MainNote {

    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator = "mainNote_seq_gen" )
    @SequenceGenerator( name = "mainNote_seq_gen", sequenceName = "mainNote_seq", allocationSize = 1 )
    private int mainNoteNo;
    
    @Column( name = "mainNoteName", length = 500 )
    private String mainNoteName;

    @OneToMany(mappedBy = "mainNote")
    private List<Product> productList = new ArrayList<>();
}
