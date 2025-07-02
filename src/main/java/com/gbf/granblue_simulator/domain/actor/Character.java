package com.gbf.granblue_simulator.domain.actor;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode(callSuper = false) @ToString(callSuper = true)
@Inheritance(strategy = InheritanceType.JOINED)
public class Character extends Actor {
}
