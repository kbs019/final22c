package com.ex.final22c.form;


import org.springframework.web.multipart.MultipartFile;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductForm {
	private Long id; 
    private String name;
    private Integer stock;
    private Integer price;
    private Integer count;
    private Double discount;
    private Long brandNo;
    private Integer gradeNo;
    private Integer mainNoteNo;
    private Integer volumeNo;
    private String singleNote;
    private String baseNote;
    private String middleNote;
    private String topNote;
    private String description;
    private MultipartFile imgName;
}
