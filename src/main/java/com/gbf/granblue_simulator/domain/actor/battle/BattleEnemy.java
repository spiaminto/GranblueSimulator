package com.gbf.granblue_simulator.domain.actor.battle;

import com.gbf.granblue_simulator.domain.actor.Enemy;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
//@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode(callSuper = false) @ToString(callSuper = true)
public class BattleEnemy extends BattleActor {

    private Integer phase;

    private Integer standby; //현재 스탠바이, 기본값 0

    private Long nextChargeAttackId; // 스탠바이시 다음 차지어택 id, 기본값 0

    public BattleEnemy of(Enemy enemy) {
        return BattleEnemy.builder()
                .name(enemy.getName())
                .phase(1)
                .standby(0)
                .nextChargeAttackId(0L)
                .actor(enemy)
                .build();
    }


}
