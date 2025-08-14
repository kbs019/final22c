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
public class Volume {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "volume_seq_gen")
    @SequenceGenerator(name = "volume_seq_gen", sequenceName = "volume_seq", allocationSize = 1)
    private int volumeNo;

    @Column(name = "volumeName", length = 100)
    private String volumeName;

    @OneToMany(mappedBy = "volume")
    private List<Product> productList = new ArrayList<>();
}
