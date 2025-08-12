package com.ex.final22c.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter   
@Setter
public class UsersAddressForm {
    private Long userNo; // 사용자 번호, 서비스에서 설정할 예정

    @NotBlank(message = "배송지명은 필수 입력 항목입니다.")
    private String addressName;     // 배송지명 (예: 집, 회사)

    @NotBlank(message = "수령인은 필수 입력 항목입니다.")
    private String recipient;       // 수령인

    @NotBlank(message = "전화번호는 필수 입력 항목입니다.")
    private String phone;           // 전화번호

    @NotBlank(message = "우편번호는 필수 입력 항목입니다.")
    private String zonecode;        // 다음API: zonecode (새 우편번호)

    @NotBlank(message = "도로명 주소는 필수 입력 항목입니다.")
    private String roadAddress;     // 다음API: roadAddress

    @NotBlank(message = "상세주소는 필수 입력 항목입니다.")
    private String detailAddress;   // 상세주소

    private Boolean isDefault; // 기본 배송지 여부
}
