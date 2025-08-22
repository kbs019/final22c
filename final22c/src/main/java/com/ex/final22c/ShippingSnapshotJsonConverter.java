package com.ex.final22c;

import com.ex.final22c.data.payment.dto.ShipSnapshotReq;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ShippingSnapshotJsonConverter implements AttributeConverter<ShipSnapshotReq, String> {
    private static final ObjectMapper om = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(ShipSnapshotReq attribute) {
        try {
            return attribute == null ? null : om.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 직렬화 실패", e);
        }
    }

    @Override
    public ShipSnapshotReq convertToEntityAttribute(String dbData) {
        try {
            return dbData == null ? null : om.readValue(dbData, ShipSnapshotReq.class);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 역직렬화 실패", e);
        }
    }
}