package com.ex.final22c.data.perfume;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class PerfumeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator="perfume_review_seq_gen")
    @SequenceGenerator(name = "perfume_review_seq_gen", sequenceName="perfume_review_seq", allocationSize = 1)
    private int reviewNo;
}
