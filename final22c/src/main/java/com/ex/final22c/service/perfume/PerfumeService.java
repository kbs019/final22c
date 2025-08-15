package com.ex.final22c.service.perfume;

import com.ex.final22c.DataNotFoundException;
import com.ex.final22c.data.perfume.Perfume;
import com.ex.final22c.repository.perfumeRepository.PerfumeRepository; // JPA
import com.ex.final22c.repository.perfumeMapper.PerfumeMapper;       // MyBatis
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PerfumeService {

    private final PerfumeRepository perfumeRepository; // 기본 조회/단건 JPA
    private final PerfumeMapper perfumeMapper;         // 동적 필터 MyBatis

    public List<Perfume> showList() {
        return perfumeRepository.findAll();
    }

    public Perfume getPerfume(int perfumeNo) {
        return perfumeRepository.findById(perfumeNo)
                .orElseThrow(() -> new DataNotFoundException("해당하는 향수의 정보를 찾을 수 없습니다."));
    }

    public List<Perfume> search(String q,
                                List<String> grades,
                                List<String> accords,
                                List<String> brands,
                                List<String> volumes) {  // Integer 리스트로 받는 게 안전

        String qq = (q == null || q.isBlank()) ? null : q.trim();
        List<String> gs = (grades == null || grades.isEmpty()) ? null : grades;
        List<String> ac = (accords == null || accords.isEmpty()) ? null : accords;
        List<String> bs = (brands == null || brands.isEmpty()) ? null : brands;
        List<String> vs = (volumes == null || volumes.isEmpty()) ? null : volumes;

        return perfumeMapper.search(qq, gs, ac, bs, vs);
    }
}
