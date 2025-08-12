package com.ex.final22c.service.perfume;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ex.final22c.DataNotFoundException;
import com.ex.final22c.data.perfume.Perfume;
import com.ex.final22c.repository.perfume.PerfumeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerfumeService {

    private final PerfumeRepository perfumeRepository;

    public List<Perfume> showList(){
        return perfumeRepository.findAll();
    }

    public Perfume getPerfume( int perfumeNo ){
        Optional<Perfume> perfume = this.perfumeRepository.findById(perfumeNo);
        if( perfume.isPresent() ){
            return perfume.get();
        }else{
            throw new DataNotFoundException("해당하는 향수의 정보를 찾을 수 없습니다.");
        }
    }
}
