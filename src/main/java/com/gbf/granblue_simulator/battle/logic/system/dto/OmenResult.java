package com.gbf.granblue_simulator.battle.logic.system.dto;

import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Omen;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelCond;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelType;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class OmenResult {
    private OmenType type; // empty (NONE) 와 구분하기 위해 필수
    private MoveType standbyMoveType; // 브레이크시, 브레이크된 전조 타입을 알기위해 필수
    private boolean isOmenBreak; // 필수

    private String name;
    private String omenInfo; // 표시 텍스트
    private String chargeAttackInfo; // 표시 텍스트
    private String motion;

    private List<OmenCancelCondDto> omenCancelCond; // 없음 과 구분하기 위해 null 초기화

    public static OmenResult from(Enemy enemy) {
        Omen omen = enemy.getOmen();
        List<Integer> cancelConditionIndexes = omen.getCancelConditionIndexes();
        List<OmenCancelType> cancelTypes = cancelConditionIndexes.stream().map(index -> omen.getBaseOmen().getOmenCancelConds().get(index).getType()).toList();
        BaseOmen baseOmen = omen.getBaseOmen();
        return OmenResult.builder()
                .type(baseOmen.getOmenType())
                .name(baseOmen.getName())
                .omenInfo(baseOmen.getInfo())
                .chargeAttackInfo(enemy.getFirstMove(omen.getStandbyType().getChargeAttackType()).getBaseMove().getInfo())
                .standbyMoveType(omen.getStandbyType())
                .motion(omen.getMotionType().getMotion())
                .isOmenBreak(false)
                .omenCancelCond(cancelConditionIndexes.stream().map(cancelConditionIndex -> {
                    int index = cancelConditionIndexes.indexOf(cancelConditionIndex);
                    OmenCancelCond cancelCondition = omen.getBaseOmen().getOmenCancelConds().get(cancelConditionIndex);
                    return OmenCancelCondDto.builder()
                            .index(cancelConditionIndex)
                            .remainValue(omen.getRemainValues().get(index)) // cancelConditionIndex 의 index 와 같음
                            .cancelType(cancelCondition.getType())
                            .info(cancelCondition.getInfo())
                            .updateTiming(cancelCondition.getType().getUpdateTiming())
                            .build();
                }).toList())
                .build();
    }

    public static OmenResult breakOmen(MoveType standbyMoveType) {
        return OmenResult.builder()
                .type(OmenType.NONE)
                .isOmenBreak(true)
                .standbyMoveType(standbyMoveType)
                .build();
    }

    @Builder
    @Data
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class OmenCancelCondDto {
        private int index;
        private int remainValue;
        private String info;
        private String updateTiming;
        private OmenCancelType cancelType;
    }
}
