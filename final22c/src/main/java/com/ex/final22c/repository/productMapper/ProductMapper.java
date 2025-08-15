package com.ex.final22c.repository.productMapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ex.final22c.data.product.Product;

@Mapper
public interface ProductMapper {

    List<Product> search(@Param("q") String q,
                        @Param("grades") List<String> grades,
                        @Param("accords") List<String> accords,
                        @Param("brands") List<String> brands,
                        @Param("volumes") List<String> volumes);

}
