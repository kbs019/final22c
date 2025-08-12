package com.ex.final22c.service.mypage;

import org.springframework.stereotype.Service;

import com.ex.final22c.data.user.UserAddress;
import com.ex.final22c.form.UsersAddressForm;
import com.ex.final22c.repository.mypage.UserAddressRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;

    // 단일 INSERT만 할 거면 @Transactional 없어도 무방 (붙여도 문제 없음)
    public void insertUserAddress(Long userNo, UsersAddressForm form) {
        String yn = Boolean.TRUE.equals(form.getIsDefault()) ? "Y" : "N";

        UserAddress userAddress = UserAddress.builder()
                .userNo(userNo)
                .addressName(form.getAddressName())
                .recipient(form.getRecipient())
                .phone(form.getPhone())
                .zonecode(form.getZonecode())
                .roadAddress(form.getRoadAddress())
                .detailAddress(form.getDetailAddress())
                .isDefault(yn)
                .build();

        userAddressRepository.save(userAddress);
    }
}