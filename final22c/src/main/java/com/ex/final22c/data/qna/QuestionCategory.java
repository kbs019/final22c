package com.ex.final22c.data.qna;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "questionCategory")
public class QuestionCategory {
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "questionCategory_seq_gen")
	@SequenceGenerator(name = "questionCategory_seq_gen", sequenceName = "questionCategory_seq", allocationSize = 1)
	private Long qcId;

	@Column(name = "type", length = 1000)
	private String type;


	@OneToMany(mappedBy="qc") 
	private List<Question> questionList = new ArrayList<>();

}
