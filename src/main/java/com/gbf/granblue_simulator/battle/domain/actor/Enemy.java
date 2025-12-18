package com.gbf.granblue_simulator.battle.domain.actor;

import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
public class Enemy extends Actor {

    private Integer currentForm; // 폼 번호, 초기값 1

    @Enumerated(EnumType.STRING)
    private MoveType currentStandbyType; // 현재 스탠바이, 기본값 null

    @Enumerated(EnumType.STRING)
    private MoveType nextIncantStandbyType; // 다음 영창기 스탠바이, 영창기는 로직 내부에서 설정해야 하므로 따로 필드 설정

    private Integer omenValue; // omen cancel value
    private Integer omenCancelCondIndex; // 랜덤 조건중 인덱스

    private Integer latestTriggeredHp; // 마지막으로 발동한 hp 트리거 (중복 방지용 이전 HP 트리거 기록)

    public void setCurrentForm(Integer currentForm) {
        this.currentForm = currentForm;
    }

    public void setCurrentStandbyType(MoveType nextStandbyType) {
        this.currentStandbyType = nextStandbyType;
    }

    public void setNextIncantStandbyType(MoveType nextIncantStandbyType) { this.nextIncantStandbyType = nextIncantStandbyType; }

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
