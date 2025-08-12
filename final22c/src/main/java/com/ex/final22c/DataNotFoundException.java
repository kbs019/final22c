package com.ex.final22c;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value=HttpStatus.NOT_FOUND, reason="entity not found")
public class DataNotFoundException extends RuntimeException {

    	// 생성자임
	public DataNotFoundException(String message) {
		super(message);
	}
}
