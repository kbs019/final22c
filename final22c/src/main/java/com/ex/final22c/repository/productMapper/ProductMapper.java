package com.ex.final22c.repository.productMapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProductMapper {

    // ===== 옵션(필터용) =====
    List<Map<String, Object>> selectBrandOptions();
    List<Map<String, Object>> selectGradeOptions();
    List<Map<String, Object>> selectMainNoteOptions();
    List<Map<String, Object>> selectVolumeOptions();

    // ===== 상품 카운트/목록 (필터 + 키워드 공통) =====
    long countProducts(@Param("brandIds")    List<Long> brandIds,
                       @Param("gradeIds")    List<Long> gradeIds,
                       @Param("mainNoteIds") List<Long> mainNoteIds,
                       @Param("volumeIds")   List<Long> volumeIds,
                       @Param("keyword")     String keyword);

    List<Map<String, Object>> selectProducts(@Param("brandIds")    List<Long> brandIds,
                                             @Param("gradeIds")    List<Long> gradeIds,
                                             @Param("mainNoteIds") List<Long> mainNoteIds,
                                             @Param("volumeIds")   List<Long> volumeIds,
                                             @Param("keyword")     String keyword);

    // ===== 브랜드 페이지용 =====
    List<Map<String, Object>> selectBrands();                 // id, name, imgUrl
    Map<String, Object>       selectBrandById(@Param("brandNo") Long brandNo);
}
