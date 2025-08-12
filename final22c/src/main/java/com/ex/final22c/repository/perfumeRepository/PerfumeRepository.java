package com.ex.final22c.repository.perfumeRepository;

import com.ex.final22c.data.perfume.Perfume;
import org.springframework.data.jpa.repository.JpaRepository;

// @Repository 붙이지 마세요 (Spring Data JPA가 자동 프록시 생성)
public interface PerfumeRepository extends JpaRepository<Perfume, Integer> {
}
