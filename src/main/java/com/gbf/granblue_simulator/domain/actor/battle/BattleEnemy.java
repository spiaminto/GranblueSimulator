package com.gbf.granblue_simulator.domain.actor.battle;

import com.gbf.granblue_simulator.domain.actor.Enemy;
import com.gbf.granblue_simulator.domain.move.MoveType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
//@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
public class BattleEnemy extends BattleActor {

    private Integer phase;

    private Integer standby; //현재 스탠바이, 기본값 0

    @Enumerated(EnumType.STRING)
    private MoveType nextChargeAttackType; // 스탠바이시 다음 발생할 특수기타입
    @Enumerated(EnumType.STRING)
    private MoveType nextStandbyType;

    private Integer omenValue; // omen cancel value
    private Integer omenCancelCondIndex; // 랜덤 조건중 인덱스

    private Integer latestTriggeredHp; // hp 트리거 중복 방지용


    public BattleEnemy of(Enemy enemy) {
        return BattleEnemy.builder()
                .name(enemy.getName())
                .phase(1)
                .standby(0)
                .actor(enemy)
                .build();
    }

    public void setNextChargeAttackType(MoveType nextChargeAttackType) {
        this.nextChargeAttackType = nextChargeAttackType;
    }

    public void setNextStandbyType(MoveType nextStandbyType) {
        this.nextStandbyType = nextStandbyType;
    }

    public void setOmenValue(Integer omenValue) {
        this.omenValue = omenValue;
    }

    public void setOmenCancelCondIndex(Integer omenCancelCondIndex) {
        this.omenCancelCondIndex = omenCancelCondIndex;
    }

    public void setLatestTriggeredHp(Integer triggeredHp) {
        this.latestTriggeredHp = triggeredHp;
    }


}
