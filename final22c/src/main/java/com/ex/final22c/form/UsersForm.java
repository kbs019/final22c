package com.ex.final22c.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    // 추가 적인 유효성 검사 진행 할 수 있음.
}