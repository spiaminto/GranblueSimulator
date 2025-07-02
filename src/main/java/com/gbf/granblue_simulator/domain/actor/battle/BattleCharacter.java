package com.gbf.granblue_simulator.domain.actor.battle;

import com.gbf.granblue_simulator.domain.actor.Character;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Inheritance(strategy = InheritanceType.JOINED)
public class BattleCharacter extends BattleActor {

    public static BattleCharacter of(Character character) {
        return BattleCharacter.builder()
                .name(character.getName())
                .actor(character)
                .build();
    }

}

