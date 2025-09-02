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

    // ===== 상품 카운트 =====
    long countProducts(@Param("brandIds")    List<Long> brandIds,
                       @Param("gradeIds")    List<Long> gradeIds,
                       @Param("mainNoteIds") List<Long> mainNoteIds,
                       @Param("volumeIds")   List<Long> volumeIds,
                       @Param("keyword")     String keyword);

    // (기존 - 다른 화면에서 사용)
    List<Map<String, Object>> selectProducts(@Param("brandIds")    List<Long> brandIds,
                                             @Param("gradeIds")    List<Long> gradeIds,
                                             @Param("mainNoteIds") List<Long> mainNoteIds,
                                             @Param("volumeIds")   List<Long> volumeIds,
                                             @Param("keyword")     String keyword);

    // ===== 리스트용 (정렬 + 페이징) =====
    /**
     * @param sort   정렬키: id(기본), popular(판매량), priceAsc, priceDesc, reviewDesc
     * @param offset 시작 오프셋
     * @param limit  가져올 개수
     */
    List<Map<String, Object>> selectProductsPaged(
            @Param("brandIds")    List<Long> brandIds,
            @Param("gradeIds")    List<Long> gradeIds,
            @Param("mainNoteIds") List<Long> mainNoteIds,
            @Param("volumeIds")   List<Long> volumeIds,
            @Param("keyword")     String keyword,
            @Param("sort")        String sort,
            @Param("offset")      int offset,
            @Param("limit")       int limit
    );

    // ===== 브랜드 페이지용 =====
    List<Map<String, Object>> selectBrands();                 // id, name, imgUrl
    Map<String, Object>       selectBrandById(@Param("brandNo") Long brandNo);
    
 // ===== 추천 시스템용 추가 메서드 =====
    /**
     * 브랜드명과 상품명으로 상품 검색
     */
    List<Map<String, Object>> findByBrandAndName(@Param("brandName") String brandName, 
                                                 @Param("productName") String productName);

    /**
     * 상품명으로 상품 검색 (부분 일치)
     */
    List<Map<String, Object>> findByProductName(@Param("productName") String productName);
}
