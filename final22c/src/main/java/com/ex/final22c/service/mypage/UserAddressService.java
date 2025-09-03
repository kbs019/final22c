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

    /*
     * -----------------------------------------------------------
     * 신규 등록: 첫 주소는 자동으로 기본(Y)
     * - 사용자가 기본지정 의사를 밝히면 그 주소를 기본으로
     * - 주소가 하나도 없을 때는 자동으로 기본으로
     * -----------------------------------------------------------
     */
    @Transactional
    public void insertUserAddress(Long userNo, UsersAddressForm form) {
        Users user = userRepository.findById(userNo)
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));

        final long addressCount = userAddressRepository.countByUser_UserNo(userNo);
        final boolean wantDefault = wantDefault(form) || addressCount == 0;

        if (wantDefault) {
            userAddressRepository.clearDefaultByUserNo(userNo);
        }

        UserAddress addr = UserAddress.builder()
                .addressName(form.getAddressName())
                .recipient(form.getRecipient())
                .phone(form.getPhone())
                .zonecode(form.getZonecode())
                .roadAddress(form.getRoadAddress())
                .detailAddress(form.getDetailAddress())
                .isDefault(wantDefault ? "Y" : "N") // ★ 핵심
                .build();

        addr.setUser(user);
        userAddressRepository.save(addr);
    }

    /* Ajax 등록용: 저장된 주소를 반환 */
    @Transactional
    public UserAddress insertUserAddressReturn(Long userNo, UsersAddressForm form) {
        Users user = userRepository.findById(userNo)
                .orElseThrow(() -> new IllegalArgumentException("회원이 존재하지 않습니다."));

        final long addressCount = userAddressRepository.countByUser_UserNo(userNo);
        final boolean wantDefault = wantDefault(form) || addressCount == 0;

        if (wantDefault) {
            userAddressRepository.clearDefaultByUserNo(userNo);
        }

        UserAddress addr = UserAddress.builder()
                .addressName(form.getAddressName())
                .recipient(form.getRecipient())
                .phone(form.getPhone())
                .zonecode(form.getZonecode())
                .roadAddress(form.getRoadAddress())
                .detailAddress(form.getDetailAddress())
                .isDefault(wantDefault ? "Y" : "N") // ★ 핵심
                .build();

        addr.setUser(user);
        return userAddressRepository.save(addr);
    }

    /* 기본 배송지 단일 지정 */
    @Transactional
    public void setDefault(Long userNo, Long addressNo) {
        userAddressRepository.clearDefaultByUserNo(userNo);
        userAddressRepository.markDefault(userNo, addressNo);
    }

    /* 목록 조회 */
    public List<UserAddress> getUserAddressesList(Long userNo) {
        return userAddressRepository.findByUser_UserNo(userNo);
    }

    /* 기본 배송지 조회 */
    public UserAddress getDefaultAddress(Long userNo) {
        return userAddressRepository.findDefaultByUserNo(userNo).orElse(null);
    }

    /* 수정 */
    @Transactional
    public UserAddress updateUserAddress(Long userNo, Long addressNo, UsersAddressForm form) {
        UserAddress ua = userAddressRepository
                .findByUser_UserNoAndAddressNo(userNo, addressNo)
                .orElseThrow(() -> new IllegalArgumentException("주소를 찾을 수 없습니다."));

        if (notBlank(form.getAddressName()))
            ua.setAddressName(form.getAddressName().trim());
        if (notBlank(form.getRecipient()))
            ua.setRecipient(form.getRecipient().trim());
        if (notBlank(form.getPhone()))
            ua.setPhone(form.getPhone().trim());
        if (notBlank(form.getZonecode()))
            ua.setZonecode(form.getZonecode().trim());
        if (notBlank(form.getRoadAddress()))
            ua.setRoadAddress(form.getRoadAddress().trim());
        if (notBlank(form.getDetailAddress()))
            ua.setDetailAddress(form.getDetailAddress().trim());

        // 기본주소로 지정 요청 시
        if (wantDefault(form)) {
            userAddressRepository.clearDefaultByUserNo(userNo);
            ua.setIsDefault("Y");
        }

        return userAddressRepository.save(ua);
    }

    /* 삭제 */
    @Transactional
    public void deleteUserAddress(Long userNo, Long addressNo) {
        userAddressRepository.deleteByUser_UserNoAndAddressNo(userNo, addressNo);
    }

    /* ---------- helpers ---------- */

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * 폼에서 기본지정 의사 파싱:
     * - isDefault 값이 "Y" / "y" / "true" / "on" 인 경우를 모두 허용
     * (checkbox, hidden, 문자열/불리언 혼용 대응)
     */
    private boolean wantDefault(UsersAddressForm form) {
        String v = String.valueOf(form.getIsDefault());
        return "Y".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v);
    }
}
