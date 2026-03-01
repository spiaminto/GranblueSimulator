package com.gbf.granblue_simulator.metadata.service;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
import com.gbf.granblue_simulator.metadata.repository.BaseCharacterRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class BaseCharacterService {

    private final BaseCharacterRepository repository;

    /**
     * 시스템 에서 사용하는 캐릭터 제외 일반 캐릭터 반환
     */
    public List<BaseCharacter> findAvailableCharacters() {
        return repository.findAll().stream().filter(character -> character.getId() > 50000).toList();
    }

    public List<BaseCharacter> findAllById(List<Long> ids) {
        return repository.findAllById(ids);
    }


}
