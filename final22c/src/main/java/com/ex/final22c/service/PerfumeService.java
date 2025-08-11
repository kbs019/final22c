package com.ex.final22c.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ex.final22c.data.Perfume;
import com.ex.final22c.repository.PerfumeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerfumeService {

    private final PerfumeRepository perfumeRepository;

    public List<Perfume> showList(){
        return perfumeRepository.findAll();
    }
}
