package com.gbf.granblue_simulator.user.repository;

import com.gbf.granblue_simulator.user.domain.UserCharacter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserCharacterRepository extends JpaRepository<UserCharacter, Long> {
    UserCharacter findByUserIdAndBaseCharacterId(Long userId, Long baseCharacterId);

}
