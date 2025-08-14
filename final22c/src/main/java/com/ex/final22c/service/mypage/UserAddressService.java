package com.ex.final22c.service.mypage;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.user.UserAddress;
import com.ex.final22c.form.UsersAddressForm;
import com.ex.final22c.repository.mypage.UserAddressRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAddressService {
    private final UserAddressRepository repo;

    // @Transactional(readOnly = true)
    // public List<UserAddress> list(Long userNo) {
    //     return repo.findByUserNoOrderByIsDefaultDescAddressNoDesc(userNo);
    // }

    @Transactional
    public void insertUserAddress(Long userNo, UsersAddressForm form) {
        UserAddress ua = UserAddress.builder()
            .userNo(userNo)
            .addressName(form.getAddressName())
            .recipient(form.getRecipient())
            .phone(form.getPhone())
            .zonecode(form.getZonecode())
            .roadAddress(form.getRoadAddress())
            .detailAddress(form.getDetailAddress())
            // .isDefault(null) // <- 생략해도 됨. null이면 @PrePersist에서 'N'
            .build();

        repo.save(ua);
    }

    @Transactional
    public void setDefault(Long userNo, Long addressNo) {
        repo.clearDefaultByUserNo(userNo);
        repo.markDefault(userNo, addressNo);
    }

    public List<UserAddress> getUserAddressesList(Long userNo) {
        return repo.findByUserNoOrderByAddressNoDesc(userNo);
    }
}
