package com.ex.final22c.data.qna;


import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "answer")
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "answer_seq_gen")
    @SequenceGenerator( name = "answer_seq_gen", sequenceName = "answer_seq", allocationSize = 1 )
    private Long aId;
    
	@Column(name="content")
	private String content;
	
	@ManyToOne(fetch = FetchType.LAZY)
	private Users writer;
	
	@CreationTimestamp
	@Column(name="createDate")
	private LocalDateTime createDate;

	@OneToOne(fetch = FetchType.LAZY) 
	private Question question;

}
