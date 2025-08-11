package com.ex.final22c.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ex.final22c.data.Perfume;

@Repository
public interface PerfumeRepository extends JpaRepository<Perfume, Integer> {
    
}
