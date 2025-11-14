package com.gbf.granblue_simulator.domain.base.actor;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Inheritance(strategy = InheritanceType.JOINED)
public class BaseCharacter extends BaseActor {
}
