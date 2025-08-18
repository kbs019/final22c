package com.ex.final22c.repository.cart;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ex.final22c.data.cart.CartLine;

@Mapper
public interface CartMapper {
    List<CartLine> selectCartLinesByUserNo( @Param("userNo") long userNo );
    List<CartLine> selectCartLinesByUsername( @Param("username") String username );
}
