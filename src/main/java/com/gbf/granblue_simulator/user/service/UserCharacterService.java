package com.gbf.granblue_simulator.user.service;


import com.gbf.granblue_simulator.user.domain.UserCharacter;
import com.gbf.granblue_simulator.user.repository.UserCharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserCharacterService {

    private final UserCharacterRepository repository;

    public List<UserCharacter> saveAll(List<UserCharacter> userCharacters) {
        return repository.saveAll(userCharacters);
    }

    public UserCharacter findById(Long characterId) {
        return repository.findById(characterId).orElseThrow(() -> new IllegalArgumentException("없는 캐릭터"));
    }

    public UserCharacter findByUserIdAndBaseCharacterId(Long userId, Long baseCharacterId) {
        return repository.findByUserIdAndBaseCharacterId(userId, baseCharacterId);
    }

    ;

}
