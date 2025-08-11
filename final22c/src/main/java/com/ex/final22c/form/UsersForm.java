package com.ex.final22c.form;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UsersForm {
    @NotBlank(message = "사용자 이름은 필수 입력 항목입니다.")
    @Size(min = 2, max = 30)
    private String userName;

    @NotBlank(message = "비밀번호는 필수 입력 항목입니다.")
    private String password1;

    @NotBlank(message = "비밀번호 확인은 필수 입력 항목입니다.")
    private String password2;

    @NotBlank(message = "이메일은 필수 입력 항목입니다.")
    @Email
    private String email;

    @NotBlank(message = "이름은 필수 입력 항목입니다.")
    private String name;
    
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "생년월일은 필수 입력 항목입니다.")
    private LocalDate birth;
    
    @NotBlank(message = "성별은 필수 입력 항목입니다.")
    private String gender;
    
    @NotBlank(message = "통신사는 필수 입력 항목입니다.")
    private String telecom;
    
    @NotBlank(message = "전화번호는 필수 입력 항목입니다.")
    private String phone;

}