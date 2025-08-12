package com.ex.final22c.data.perfume;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
// import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name="mark", uniqueConstraints = @UniqueConstraint(name="uk_mark_user_perfume", columnNames = {"userNo", "perfumeNo"}))
public class Mark {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mark_seq_gen")
    @SequenceGenerator(name="mark_seq_gen", sequenceName="mark_seq", allocationSize = 1)
    @Column(name="markNo")
    private int markNo;

    @Column(name="isMarked", length=1)
    private String isMarked;

    @Column(name="markCount")
    private int markCount;

    @ManyToOne
    @JoinColumn(name="perfumeNo", nullable=false)
    private Perfume perfume;

    @ManyToOne
    @JoinColumn(name="userNo", nullable=false)
    private Users user;
}
