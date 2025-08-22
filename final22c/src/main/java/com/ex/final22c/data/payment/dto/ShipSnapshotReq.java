package com.ex.final22c.data.payment.dto;

public record ShipSnapshotReq(
	    Long addressNo,
	    String addressName,
	    String recipient,
	    String phone,
	    String zonecode,
	    String roadAddress,
	    String detailAddress,
	    String memo
	) {}

