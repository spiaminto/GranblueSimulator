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

    private Integer omenValue; // omen cancel value, 기본적으로 다음 전조가 발생할때 까지 값이 유지됨
    @Transient
    private Integer prevOmenValue; // 턴제 전조 발동시 사용할 이전 전조 마지막값 (특수기 사용시 초기화), 기본적으로 전조 발동시 omenValue 가 반드시 초기값으로 초기화 되므로 임시저장후 사용

    private Integer omenCancelCondIndex; // 랜덤 조건중 인덱스

    private Integer latestTriggeredHp; // 마지막으로 발동한 hp 트리거 (중복 방지용 이전 HP 트리거 기록)

    public void initPrevOmenValue() {
        if (this.prevOmenValue == null) {
            this.prevOmenValue = this.omenValue;
        }
    }

    public void updateCurrentForm(Integer currentForm) {
        this.currentForm = currentForm;
    }

    public void updateCurrentStandbyType(MoveType standbyType) {
        this.currentStandbyType = standbyType;
    }

    public void updateNextIncantStandbyType(MoveType nextIncantStandbyType) {
        if (this.nextIncantStandbyType != null && this.getMove(nextIncantStandbyType).getOmen().isTriggerPrimary()) return; // 영창기(우선) 등록되어있을시 무시
        this.nextIncantStandbyType = nextIncantStandbyType; }

    public void updateOmenValue(Integer omenValue) {
        this.omenValue = omenValue;
    }

    public void updateOmenCancelCondIndex(Integer omenCancelCondIndex) {
        this.omenCancelCondIndex = omenCancelCondIndex;
    }

    public void updateLatestTriggeredHp(Integer triggeredHp) {
        this.latestTriggeredHp = triggeredHp;
    }


}
