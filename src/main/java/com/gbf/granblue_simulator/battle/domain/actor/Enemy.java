package com.gbf.granblue_simulator.battle.domain.actor;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Omen;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Entity
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Enemy extends Actor {

    private Integer currentForm; // 폼 번호, 초기값 1

    @Enumerated(EnumType.STRING)
    private MoveType nextIncantStandbyType; // 다음 영창기 스탠바이, 영창기는 로직 내부에서 설정해야 하므로 따로 필드 설정

    private Integer latestTriggeredHp; // 마지막으로 발동한 hp 트리거 (중복 방지용 이전 HP 트리거 기록)

    @OneToOne(mappedBy = "enemy")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Omen omen;

    @Transient
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Omen transientPrevOmen; // 턴제 전조 발동시 사용할 이전 전조 마지막값 (특수기 사용시 초기화), 기본적으로 전조 발동시 omenValue 가 반드시 초기값으로 초기화 되므로 임시저장후 사용

    public BaseEnemy getBaseEnemy() {
        return (BaseEnemy) this.getBaseActor();
    }

    /**
     * Enemy.BaseEnemy.Omens 중 standbyType 에 맞는 omen 반환. 없으면 null
     */
    public BaseOmen getBaseOmen(MoveType standbyType) {
        return this.getBaseEnemy().getOmens().get(standbyType);
    }

    /**
     * 전조 값 이어갈때 이전 전조 확인용으로 사용
     */
    public boolean isPrevOmenSame(Omen omen) {
        return this.transientPrevOmen != null && this.transientPrevOmen.getStandbyType() == omen.getStandbyType();
    }

    public void updatePrevOmen(Omen omen) {
        if (this.omen != null)
            throw new IllegalArgumentException("transientPrevOmen 은 반드시 omen 엔티티 삭제후 초기화 해야함, enemy.id = " + this.getId() + " omen = " + this.omen);
        this.transientPrevOmen = omen;
    }

    public void updateOmen(Omen omen) {
        this.omen = omen;
    }

    public void updateCurrentForm(Integer currentForm) {
        this.currentForm = currentForm;
    }

    public void updateNextIncantStandbyType(MoveType nextIncantStandbyType) {
        if (nextIncantStandbyType != null
                && this.nextIncantStandbyType != null
                && this.getBaseOmen(this.nextIncantStandbyType).isTriggerPrimary()) return; // 영창기(우선) 등록되어있을시 무시
        this.nextIncantStandbyType = nextIncantStandbyType;
    }

    public void updateLatestTriggeredHp(Integer triggeredHp) {
        this.latestTriggeredHp = triggeredHp;
    }

}
