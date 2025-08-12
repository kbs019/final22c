package com.ex.final22c.data.perfume;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table( name="rec", uniqueConstraints = @UniqueConstraint(name="uk_rec_user_review", columnNames = {"userNo", "reviewNo"}) )
public class Rec {
    
    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator="rec_seq_gen" )
    @SequenceGenerator( name="rec_seq_gen", sequenceName="rec_seq", allocationSize=1 )
    @Column(name="recNo")
    private int recNo;

    @Column(name="isRec", length=1)
    private String isRec;

    @Column(name="recCount")
    private int recCount;

    @ManyToOne
    @JoinColumn(name="userNo", nullable=false)
    private Users user;

    @ManyToOne
    @JoinColumn(name="reviewNo", nullable=false)
    private Review review;
}
