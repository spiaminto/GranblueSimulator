package com.gbf.granblue_simulator.battle.logic.move.dto;

import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class DefaultMoveRequest {
    private final Move move;

    private final Double modifiedDamageRate;
    private final Integer modifiedHitCount;
    private final List<BaseStatusEffect> selectedBaseEffects; // 선택된 효과 없음 과 구분을 위해 null 로 초기화

    private final int toEffectLevel; // 효과 레벨 올려서 적용
    private final boolean isReactivatedChargeAttack; // 재발동한 오의

    /**
     * 기본 요청
     * @param move
     * @return
     */
    public static DefaultMoveRequest from(Move move) {
        return DefaultMoveRequest.builder().move(move).build();
    }

    /**
     * 데미지 배율 변경 요청
     * @param move
     * @param modifiedDamageRate
     * @return
     */
    public static DefaultMoveRequest withDamageRate(Move move, Double modifiedDamageRate) {
        return DefaultMoveRequest.builder().move(move).modifiedDamageRate(modifiedDamageRate).build();
    }

    /**
     * 히트수 변경 요청
     * @param move
     * @param modifiedHitCount
     * @return
     */
    public static DefaultMoveRequest withHitCount(Move move, Integer modifiedHitCount) {
        return DefaultMoveRequest.builder().move(move).modifiedHitCount(modifiedHitCount).build();
    }

    /**
     * 상태효과 선택식 요청
     * @param move
     * @param selectedBaseStatusEffects
     * @return
     */
    public static DefaultMoveRequest withSelectedBaseStatusEffects(Move move, List<BaseStatusEffect> selectedBaseStatusEffects) {
        return DefaultMoveRequest.builder().move(move).selectedBaseEffects(selectedBaseStatusEffects).build();
    }
}
