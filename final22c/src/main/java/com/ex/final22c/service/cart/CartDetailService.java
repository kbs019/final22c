package com.ex.final22c.service.cart;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ex.final22c.data.cart.CartDetail;
import com.ex.final22c.repository.cartDetail.CartDetailRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartDetailService {

    private final CartDetailRepository cartDetailRepository;

    // 로그인 사용자의 장바구니 항목 단건 조회 (권한 검증 포함)
    public CartDetail findMine( String userName, Long cartDetailId ){
        return cartDetailRepository
            .findByIdAndOwnerFetch(cartDetailId, userName)
            .orElseThrow( () -> new IllegalArgumentException("항목이 없거나 접근 권한이 없습니다.") );
    }

    // 수량 변경 요청을 재고에 맞춰 보정하고 저장
    public int clampAndUpdateQty( String userName, Long cartDetailId, int requestQty ){
        CartDetail cd = findMine( userName, cartDetailId );

        int stock = Math.max( 0, Optional.ofNullable(cd.getProduct())
                                            .map( p -> p.getCount() )
                                            .orElse(0));

        int clamped = Math.max( 1, Math.min(requestQty, stock) );

        cd.setQuantity(clamped);

        return clamped;
    }

    //  체크아웃 전에 여러 항목 소유자 & 재고 검증이 필요할 때 사용
    public List<CartDetail> findAllMineByIds( String userName, List<Long> ids ){
        if( ids == null || ids.isEmpty() ){ return List.of(); }
        return cartDetailRepository.findAllByIdsAndOwnerFetch(userName, ids);
    }

    // 선택 삭제 (소유자 기준으로만 삭제)
    public int removeMine( String userName, List<Long> ids ){
        if( ids == null || ids.isEmpty() ){ return 0; }
        return cartDetailRepository.deleteByIdsAndOwner(userName, ids);
    }
}
