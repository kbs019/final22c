package com.ex.final22c.repository.productMapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {
    List<Map<String, Object>> selectBrandOptions();
    List<Map<String, Object>> selectGradeOptions();
    List<Map<String, Object>> selectMainNoteOptions();
    List<Map<String, Object>> selectVolumeOptions();

    long countProducts(@Param("brandIds")    List<Long> brandIds,
                       @Param("gradeIds")    List<Long> gradeIds,
                       @Param("mainNoteIds") List<Long> mainNoteIds,
                       @Param("volumeIds")   List<Long> volumeIds);

    List<Map<String, Object>> selectProducts(@Param("brandIds")    List<Long> brandIds,
                                             @Param("gradeIds")    List<Long> gradeIds,
                                             @Param("mainNoteIds") List<Long> mainNoteIds,
                                             @Param("volumeIds")   List<Long> volumeIds);
}
