package com.ex.final22c.data.order;

public record OrderLine(
    int perfumeNo,
    String name,
    String brand,
    int unitPrice,
    int qty,
    int lineTotal,
    String imgPath,
    String imgName
) {}