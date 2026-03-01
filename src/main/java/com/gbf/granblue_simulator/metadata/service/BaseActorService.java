package com.gbf.granblue_simulator.metadata.service;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.repository.BaseActorRepository;
import com.gbf.granblue_simulator.metadata.repository.BaseCharacterRepository;
import com.gbf.granblue_simulator.metadata.repository.BaseEnemyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class BaseActorService {

    private final BaseCharacterRepository baseCharacterRepository;
    private final BaseEnemyRepository baseEnemyRepository;
    private final BaseActorRepository baseActorRepository;

    public Optional<BaseActor> findById(Long id) {
        return baseActorRepository.findById(id);
    }

    public List<BaseEnemy> findByRootNameEn(String rootNameEn) {
        return baseEnemyRepository.findByRootNameEn(rootNameEn);
    };

}
