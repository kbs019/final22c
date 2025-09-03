package com.ex.final22c.service.product;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.RestockAlert;
import com.ex.final22c.data.user.Users;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.productRepository.RestockAlertRepository;
import com.ex.final22c.repository.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RestockAlertService {

    private final RestockAlertRepository restockAlertRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public RestockAlert subscribe(Long productId, Long userNo, String phoneSnapshot) {
        Objects.requireNonNull(productId);
        Objects.requireNonNull(userNo);

        // 이미 REQUESTED 있으면 그대로 반환(중복 방지)
        RestockAlert exists = restockAlertRepository.findTopByProduct_IdAndUser_UserNoAndStatus(productId, userNo,
                "REQUESTED");
        if (exists != null)
            return exists;

        Product p = productRepository.getReferenceById(productId);
        Users u = userRepository.getReferenceById(userNo);

        RestockAlert ra = new RestockAlert();
        ra.setProduct(p);
        ra.setUser(u);
        ra.setStatus("REQUESTED"); // enum 미사용 → 문자열
        ra.setRequestedReg(LocalDateTime.now());
        if (phoneSnapshot != null)
            ra.setPhoneSnapshot(phoneSnapshot);
        return restockAlertRepository.save(ra);
    }

    public int notifyPendingForProduct(Long productId, Function<String, Boolean> smsSender) {
        // 재고 입력 시 호출: 대기중(REQUESTED) 일부/전체에 문자 전송하고 상태 전환
        List<RestockAlert> pendings = restockAlertRepository
                .findTop500ByProduct_IdAndStatusOrderByRequestedRegAsc(productId, "REQUESTED");
        int ok = 0;
        for (RestockAlert a : pendings) {
            String phone = (a.getPhoneSnapshot() != null) ? a.getPhoneSnapshot()
                    : (a.getUser() != null ? a.getUser().getPhone() : null);
            if (phone == null)
                continue;

            boolean sent = smsSender.apply(phone);
            if (sent) {
                a.setStatus("NOTIFIED");
                a.setNotifiedReg(LocalDateTime.now());
                ok++;
            }
        }
        return ok; // 트랜잭션 끝에 flush
    }

    // 이미 신청을 한 적이 있는가 확인
    @Transactional(readOnly = true)
    public boolean isRequested(Long productId, Long userNo) {
        return restockAlertRepository.existsByProduct_IdAndUser_UserNoAndStatus(productId, userNo, "REQUESTED");
    }

    // 신청 취소
    public boolean cancel(Long productId, Long userNo) {
        RestockAlert a = restockAlertRepository.findTopByProduct_IdAndUser_UserNoAndStatus(productId, userNo, "REQUESTED");
        if (a == null)
            return false;
        a.setStatus("CANCELED");
        a.setNotifiedReg(null);
        return true;
    }
}