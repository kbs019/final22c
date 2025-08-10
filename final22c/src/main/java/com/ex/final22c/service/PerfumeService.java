package com.ex.final22c.service;

import org.springframework.stereotype.Service;

import com.ex.final22c.repository.PerfumeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerfumeService {

    private PerfumeRepository perfumeRepository;
}
