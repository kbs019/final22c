// src/main/java/com/ex/final22c/repository/perfumeMapper/PerfumeMapper.java
package com.ex.final22c.repository.perfumeMapper;

import com.ex.final22c.data.perfume.Perfume;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface PerfumeMapper {
    List<Perfume> search(@Param("q") String q,
                     @Param("grades") List<String> grades,
                     @Param("accords") List<String> accords,
                     @Param("brands") List<String> brands,
                     @Param("volumes") List<String> volumes);
}
