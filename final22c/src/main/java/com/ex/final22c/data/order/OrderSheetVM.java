package com.ex.final22c.data.order;

import java.util.List;

/* 주문서 화면에 표시만 할 데이터를 담기 위한 임시 객체이기 때문에
   주문페이지상에서는 orderDetail, order테이블에 값을 저장할 이유가없음
 */
public record OrderSheetVM(
    List<OrderLine> items,
    int totalQty,
    int totalAmount
) {}