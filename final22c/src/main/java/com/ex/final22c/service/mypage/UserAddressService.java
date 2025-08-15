package com.ex.final22c.service.mypage;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.user.UserAddress;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.form.UsersAddressForm;
import com.ex.final22c.repository.mypage.UserAddressRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserAddressService {
    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;

    @Transactional
    public void insertUserAddress(Long userNo, UsersAddressForm form){
        Users user = userRepository.findById(userNo)
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));

        if ("Y".equals(form.getIsDefault())) {
            userAddressRepository.clearDefaultByUserNo(userNo);
        }

        UserAddress addr = UserAddress.builder()
                .addressName(form.getAddressName())
                .recipient(form.getRecipient())
                .phone(form.getPhone())
                .zonecode(form.getZonecode())
                .roadAddress(form.getRoadAddress())
                .detailAddress(form.getDetailAddress())
                // isDefault는 null일 때 @PrePersist에서 'N'
                .isDefault(Boolean.TRUE.equals(form.getIsDefault()) ? "Y" : "N") // 'Y' 또는 null
                .build();

        // 연관관계 설정 (둘 중 하나 택1, 동시에 중복 호출하지 말 것)
        // 1) 편의 메서드 사용
        user.addAddress(addr);
        userRepository.save(user); // cascade=ALL 이므로 address도 저장

        // 2) 또는 직접 주인 세팅 후 address 저장
        // addr.setUser(user);
        // userAddressRepository.save(addr);
    }

    // 기본 배송지 설정
    @Transactional
    public void setDefault(Long userNo, Long addressNo) {
        userAddressRepository.clearDefaultByUserNo(userNo);
        userAddressRepository.markDefault(userNo, addressNo);
    }

    // 사용자 주소 목록 조회
    public List<UserAddress> getUserAddressesList(Long userNo) {
        return userAddressRepository.findByUser_UserNo(userNo);
    }
}
