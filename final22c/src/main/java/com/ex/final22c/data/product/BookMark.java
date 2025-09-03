package com.ex.final22c.data.product;

import java.time.LocalDateTime;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "BookMark")
public class BookMark {

    @Id
    @GeneratedValue( strategy = GenerationType.SEQUENCE, generator = "bookMark_seq_gen" )
    @SequenceGenerator( name = "bookMark_seq_gen", sequenceName = "bookMark_seq", allocationSize = 1 )
    @Column(name = "bookMarkId")
    private Long bookMarkId;

    @ManyToOne
    private Product product;

    @ManyToOne
    private Users user;

    @Column(name = "createDate")
    private LocalDateTime createDate;

    @PrePersist
    void prepersist(){
        if( createDate == null ){ createDate = LocalDateTime.now(); }
    }
}
