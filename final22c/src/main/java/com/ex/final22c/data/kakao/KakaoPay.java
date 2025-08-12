package com.ex.final22c.data.kakao;
import lombok.Data;

@Data
public class KakaoPay {
	private String tid; // 결제 고유번호
	private String next_redirect_pc_url;
	private String partner_order_id;
}
