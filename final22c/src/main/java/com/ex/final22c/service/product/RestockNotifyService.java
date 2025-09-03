package com.ex.final22c.service.product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ex.final22c.CoolSmsSender;
import com.ex.final22c.data.product.RestockAlert;
import com.ex.final22c.repository.productRepository.RestockAlertRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RestockNotifyService {

    private final RestockAlertRepository restockAlertRepository;
    private final CoolSmsSender coolSms;

    /** 유저 단위 쿨다운(분): 이 시간 내 이미 보냈으면 이번 전체 스킵 */
    private static final long USER_COOLDOWN_MINUTES   = 15;
    /** 동일 상품 재알림 쿨다운(시간): 이 시간 내 이미 보낸 상품은 번들에서 제외 */
    private static final long PRODUCT_COOLDOWN_HOURS  = 24;
    /** 대표로 노출할 최대 개수(3개 초과 시 “외 n개”) */
    private static final int  MAX_LIST_NAMES          = 2;

    /** 커밋 후 새 트랜잭션에서 실행 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyForProduct(Long productId) {

        // 1) 이번에 입고된 상품에 대해 신청자 목록(REQUESTED)
        List<RestockAlert> seedAlerts = restockAlertRepository.findRequestedByProductId(productId);
        if (seedAlerts.isEmpty()) return;

        // userNo -> 시드 알림들
        Map<Long, List<RestockAlert>> byUser = new LinkedHashMap<>();
        for (RestockAlert ra : seedAlerts) {
            Long userNo = ra.getUser().getUserNo();
            byUser.computeIfAbsent(userNo, k -> new ArrayList<>()).add(ra);
        }

        var okAll = new ArrayList<Long>();
        var ngAll = new ArrayList<Long>();

        LocalDateTime now = LocalDateTime.now();

        for (var entry : byUser.entrySet()) {
            Long userNo = entry.getKey();

            // 2) 유저 단위 쿨다운(스팸 방지): 최근 15분 내 발송 이력 있으면 전체 스킵
            boolean recentUser = restockAlertRepository.existsUserRecentNotified(
                userNo, now.minusMinutes(USER_COOLDOWN_MINUTES)
            );
            if (recentUser) continue;

            // 3) 이 유저가 신청했고, 현재 재고가 있는 모든 알림(REQUESTED + stock>0)
            List<RestockAlert> bundle = restockAlertRepository.findUserRequestedWithStock(userNo);
            if (bundle.isEmpty()) continue;

            // 3-1) 동일 상품 재알림 쿨다운: 최근 24시간 내 NOTIFIED 이력이 있으면 제외
            List<RestockAlert> filtered = new ArrayList<>();
            Set<Long> seenProductIds = new LinkedHashSet<>(); // 동일 상품 중복 제거
            for (RestockAlert ra : bundle) {
                Long pid = ra.getProduct().getId();
                if (seenProductIds.contains(pid)) continue; // 동일 상품 중복 방지

                boolean recentSameProduct = restockAlertRepository.existsRecentNotified(
                    pid, userNo, now.minusHours(PRODUCT_COOLDOWN_HOURS)
                );
                if (!recentSameProduct) {
                    filtered.add(ra);
                    seenProductIds.add(pid);
                }
            }
            if (filtered.isEmpty()) continue;

            // 4) 메시지 본문 만들기(브랜드 + 상품명)
            List<String> names = new ArrayList<>();
            for (RestockAlert ra : filtered) {
                names.add(composeName(ra));
            }
            String text = buildMessageCompact(names);

            // 5) 수신자 번호(Users.phone 우선, 없으면 snapshot)
            String phone = pickPhone(filtered.get(0)); // 동일 유저
            if (phone == null || phone.isBlank()) {
                for (RestockAlert ra : filtered) ngAll.add(ra.getRestockAlertId());
                continue;
            }

            // 6) 발송
            boolean sent = coolSms.sendPlainText(phone, text);

            if (sent) {
                for (RestockAlert ra : filtered) okAll.add(ra.getRestockAlertId());
                // 최신 번호 스냅샷 동기화(선택)
                maybeUpdateSnapshot(filtered.get(0));
            } else {
                for (RestockAlert ra : filtered) ngAll.add(ra.getRestockAlertId());
            }
        }

        // 7) 상태 일괄 갱신
        if (!okAll.isEmpty()) restockAlertRepository.bulkMarkNotified(okAll);
        if (!ngAll.isEmpty()) restockAlertRepository.bulkMarkFailed(ngAll);
    }

    /** 브랜드 + 상품명 (용량은 상품명에 이미 포함돼 있다고 가정) */
    private String composeName(RestockAlert ra) {
        String brand = (ra.getProduct().getBrand() != null)
                ? ra.getProduct().getBrand().getBrandName() : null;
        String name  = ra.getProduct().getName();

        if ((brand == null || brand.isBlank()) && name != null) return name.trim();
        if ((name  == null || name.isBlank())  && brand != null) return brand.trim();
        if (brand == null && name == null) return "";
        return ("(" + brand + ") " + name).trim();
    }

    /** 3개 이하면 불릿 전체 나열 / 초과 시 대표 MAX_LIST_NAMES개 + “외 n개 재입고” */
    private String buildMessageCompact(List<String> names) {
        final String title = "[22°C 재입고 알림]\n";
        if (names == null || names.isEmpty()) {
            return title + "관심 상품이 재입고되었습니다.";
        }
        if (names.size() <= 3) {
            StringBuilder sb = new StringBuilder(title);
            for (String n : names) sb.append("- ").append(n).append('\n');
            sb.append("위 상품이 재입고되었습니다.");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder(title);
            int show = Math.min(MAX_LIST_NAMES, names.size());
            for (int i = 0; i < show; i++) {
                if (i > 0) sb.append(", ");
                sb.append(names.get(i));
            }
            int remain = names.size() - show;
            sb.append(" 외 ").append(remain).append("개 재입고");
            return sb.toString();
        }
    }

    /** Users.phone 우선, 없으면 snapshot */
    private String pickPhone(RestockAlert ra) {
        String usersPhone = (ra.getUser() != null) ? ra.getUser().getPhone() : null;
        if (usersPhone != null && !usersPhone.isBlank()) return usersPhone;
        return ra.getPhoneSnapshot();
    }

    /** 발송 성공 시 스냅샷을 최신 번호로 동기화(선택) */
    private void maybeUpdateSnapshot(RestockAlert ra) {
        String usersPhone = (ra.getUser() != null) ? ra.getUser().getPhone() : null;
        if (usersPhone != null && !usersPhone.isBlank()
                && !usersPhone.equals(ra.getPhoneSnapshot())) {
            ra.setPhoneSnapshot(usersPhone);
        }
    }
}