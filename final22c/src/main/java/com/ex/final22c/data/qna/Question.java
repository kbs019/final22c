package com.ex.final22c.data.qna;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import com.ex.final22c.data.user.Users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "question")
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "question_seq_gen")
    @SequenceGenerator( name = "question_seq_gen", sequenceName = "question_seq", allocationSize = 1 )
    private Long qId;
    
	@Column(name="title",length=200)
	private String title;
	
	@Lob
	@Column(name="content",columnDefinition="CLOB")
	private String content;
	
	@ManyToOne(fetch = FetchType.LAZY)
	private Users writer;
	
	@CreationTimestamp
	@Column(name="createDate")
	private LocalDateTime createDate;
	
    @Column(name = "status")
    private String status;
	
	@ManyToOne(fetch = FetchType.LAZY) 
	private QuestionCategory qc;

	@OneToOne(cascade=CascadeType.REMOVE, fetch = FetchType.EAGER) 
	private Answer answer;

}
