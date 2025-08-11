package com.ex.final22c.data;

import lombok.Getter;

@Getter
public enum UserRole {
	ADMIN("ROLE_ADMIN"),USER("ROLE_USER");
	
	private String value;
	
	// 생성자
	UserRole(String value){
		this.value = value;
	}
}
