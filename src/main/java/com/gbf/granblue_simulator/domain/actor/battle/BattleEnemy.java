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

    private Integer currentForm; // 폼 번호, 초기값 1

    @Enumerated(EnumType.STRING)
    private MoveType nextStandbyType; // 다음 스탠바이, 기본값 null

    private Integer omenValue; // omen cancel value
    private Integer omenCancelCondIndex; // 랜덤 조건중 인덱스

    private Integer latestTriggeredHp; // 마지막으로 발동한 hp 트리거 (중복 방지용 이전 HP 트리거 기록)


    public BattleEnemy of(Enemy enemy) {
        return BattleEnemy.builder()
                .name(enemy.getName())
                .currentForm(1)
                .actor(enemy)
                .build();
    }

    public void setCurrentForm(Integer currentForm) {
        this.currentForm = currentForm;
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
