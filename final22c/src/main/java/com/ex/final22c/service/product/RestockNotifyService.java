package com.ex.final22c.service.product;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.CoolSmsSender;
import com.ex.final22c.data.product.Product;
import com.ex.final22c.data.product.RestockAlert;
import com.ex.final22c.repository.productRepository.ProductRepository;
import com.ex.final22c.repository.productRepository.RestockAlertRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RestockNotifyService {

    private final RestockAlertRepository restockAlertRepository;
    private final ProductRepository productRepository;
    private final CoolSmsSender coolSms;

    // 과도한 중복 발송 방지(예: 24시간)
    private static final long COOLDOWN_HOURS = 24;
    // 대표로 노출할 최대 개수
    private static final int  MAX_LIST_NAMES  = 2; 

    // 커밋 후 새 트랜잭션에서 실행
    @Transactional
    public void notifyForProduct(Long productId) {

        // 1) 이번에 입고된 상품에 대해 신청자 목록
        List<RestockAlert> seedAlerts = restockAlertRepository.findRequestedByProductId(productId);
        if (seedAlerts.isEmpty()) return;

        // userNo -> 시드 알림들
        Map<Long, List<RestockAlert>> byUser = new HashMap<>();
        for (RestockAlert ra : seedAlerts) {
            Long userNo = ra.getUser().getUserNo();
            byUser.computeIfAbsent(userNo, k -> new ArrayList<>()).add(ra);
        }

        var okAll = new java.util.ArrayList<Long>();
        var ngAll = new java.util.ArrayList<Long>();

        for (var entry : byUser.entrySet()) {
            Long userNo = entry.getKey();

            // 2) 해당 유저가 신청했고 현재 재고가 있는 모든 알림(시드+그 외) 조회
            List<RestockAlert> bundle = restockAlertRepository.findUserRequestedWithStock(userNo);
            if (bundle.isEmpty()) continue;

            // 2-1) 쿨다운: "대표 상품" 기준으로 최근 발송이 있었는지 체크(과도한 중복 방지)
            RestockAlert first = bundle.get(0);
            boolean recent = restockAlertRepository.existsRecentNotified(
                first.getProduct().getId(),
                userNo,
                java.time.LocalDateTime.now().minusHours(COOLDOWN_HOURS)
            );
            if (recent) continue;

            // 3) 본문 만들기(상품명/옵션 조합)
            List<String> names = new ArrayList<>();
            for (RestockAlert ra : bundle) {
                names.add(composeName(ra)); // 브랜드/상품/옵션을 한 줄로
            }
            String text = buildMessageCompact(names);

            // 4) 수신자 번호(Users.phone 우선, 없으면 snapshot)
            String phone = pickPhone(bundle.get(0)); // 동일 유저이므로 아무거나 사용
            if (phone == null || phone.isBlank()) {
                // 번호가 없으면 전부 실패 처리
                for (RestockAlert ra : bundle) ngAll.add(ra.getRestockAlertId());
                continue;
            }

            boolean sent = coolSms.sendPlainText(phone, text);
            if (sent) {
                // 전부 NOTIFIED 처리
                for (RestockAlert ra : bundle) okAll.add(ra.getRestockAlertId());
                // 최신 번호 스냅샷 동기화(선택)
                maybeUpdateSnapshot(bundle.get(0));
            } else {
                for (RestockAlert ra : bundle) ngAll.add(ra.getRestockAlertId());
            }
        }

        if (!okAll.isEmpty()) restockAlertRepository.bulkMarkNotified(okAll);
        if (!ngAll.isEmpty()) restockAlertRepository.bulkMarkFailed(ngAll);
    }

    private String composeName(RestockAlert ra) {
        // 네 엔티티 필드명에 맞게 조합(예시는 brand.name / product.name / volume.label)
        String brand = (ra.getProduct().getBrand() != null) ? ra.getProduct().getBrand().getBrandName() : null;
        String name  = ra.getProduct().getName();

        StringBuilder sb = new StringBuilder();
        if (brand != null && !brand.isBlank()) sb.append(brand).append(' ');
        if (name  != null && !name.isBlank())  sb.append(name).append(' ');
        return sb.toString().trim();
    }

    private String buildMessageCompact(List<String> names) {
        if (names.isEmpty()) {
            return "[22°C 재입고 알림]\n관심 상품이 재입고되었습니다.";
        }
        if (names.size() <= 3) {
            // 전부 나열
            StringBuilder sb = new StringBuilder("[22°C 재입고 알림]\n");
            for (String n : names) sb.append(n).append('\n');
            sb.append("가 재입고되었습니다.");
            return sb.toString();
        } else {
            // 대표 MAX_LIST_NAMES개 + 외 n개
            StringBuilder sb = new StringBuilder("[22°C 재입고 알림]\n");
            for (int i = 0; i < Math.min(MAX_LIST_NAMES, names.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(names.get(i));
            }
            int remain = names.size() - Math.min(MAX_LIST_NAMES, names.size());
            sb.append(" 외 ").append(remain).append("개가 재입고되었습니다.");
            return sb.toString();
        }
    }

    private String pickPhone(RestockAlert ra) {
        String usersPhone = (ra.getUser() != null) ? ra.getUser().getPhone() : null;
        if (usersPhone != null && !usersPhone.isBlank()) return usersPhone;
        return ra.getPhoneSnapshot();
    }

    private void maybeUpdateSnapshot(RestockAlert ra) {
        String usersPhone = (ra.getUser() != null) ? ra.getUser().getPhone() : null;
        if (usersPhone != null && !usersPhone.isBlank()
            && !usersPhone.equals(ra.getPhoneSnapshot())) {
            ra.setPhoneSnapshot(usersPhone); // 같은 트랜잭션에서 flush로 반영
        }
    }
}
