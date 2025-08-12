package com.ex.final22c.service.mypage;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ex.final22c.data.user.UserAddress;
import com.ex.final22c.form.UsersAddressForm;
import com.ex.final22c.repository.mypage.UserAddressRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAddressService {
    private final UserAddressRepository addressRepository;

    public UserAddress insertAddress(Long userNo, UsersAddressForm userAddressForm ) {
        UserAddress userAddress = UserAddress.builder()
                .userNo(userNo)
                .addressName(userAddressForm.getAddressName())
                .recipient(userAddressForm.getRecipient())
                .phone(userAddressForm.getPhone())
                .zonecode(userAddressForm.getZonecode())
                .roadAddress(userAddressForm.getRoadAddress())
                .detailAddress(userAddressForm.getDetailAddress())
                .build();   
                this.addressRepository.save(userAddress);
        
        return userAddress;
    }

    public List<UserAddress> getMyAddresses(Long userNo) {
        return addressRepository.findByUserNo(userNo);
    }
}