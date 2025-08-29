package com.ex.final22c;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT) // 409
public class OutOfStockException extends RuntimeException {
    public OutOfStockException() { super("재고가 부족합니다. 다른 사용자와 동시에 결제 중일 수 있어요."); }
    public OutOfStockException(String msg) { super(msg); }
}