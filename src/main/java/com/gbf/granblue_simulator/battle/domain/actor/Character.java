package com.gbf.granblue_simulator.battle.domain.actor;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseCharacter;
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
public class Character extends Actor {

}

