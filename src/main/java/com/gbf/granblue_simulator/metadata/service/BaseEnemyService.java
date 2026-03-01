package com.gbf.granblue_simulator.metadata.service;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.repository.BaseEnemyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class BaseEnemyService {

    private final BaseEnemyRepository repository;

    public Optional<BaseEnemy> findById(Long id) {
        return repository.findById(id);
    }

    public List<BaseEnemy> findFirstFormEnemies() {
        return repository.findAllByFormOrder(1);
    }

}
