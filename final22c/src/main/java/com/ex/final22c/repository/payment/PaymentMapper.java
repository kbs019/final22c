package com.ex.final22c.repository.payment;

import com.ex.final22c.data.payment.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentMapper {

    int insertReady(@Param("orderId") String orderId,
                    @Param("amount") int amount,
                    @Param("status") String status,
                    @Param("tid") String tid);

    int updateSuccess(@Param("tid") String tid,
                      @Param("aid") String aid,
                      @Param("status") String status,
                      @Param("approvedAt") java.time.LocalDateTime approvedAt);

    Payment findByTid(@Param("tid") String tid);
}