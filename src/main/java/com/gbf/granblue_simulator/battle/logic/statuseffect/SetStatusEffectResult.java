package com.gbf.granblue_simulator.battle.logic.statuseffect;

import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Data
@Builder
@Slf4j
public class SetStatusEffectResult {

    @Builder.Default
    private Map<Long, Result> results = new HashMap<>();

    public static SetStatusEffectResult emptyResult() {
        return SetStatusEffectResult.builder().build();
    }

    /**
     * SetStatusEffectResult 끼리 병합
     *
     * @param others 다른 결과들
     */
    public void merge(SetStatusEffectResult... others) {
        for (SetStatusEffectResult other : others) {
            log.info("[merge] other = {}", other);
            if (other == null) continue;
            Map<Long, Result> otherResults = other.getResults();
            for (Long otherResultActorId : otherResults.keySet()) {
                Result otherResult = otherResults.get(otherResultActorId);
                Result thisResult = this.results.get(otherResultActorId);
                if (thisResult != null) {
                    // 기존 결과 있으면 병합
                    thisResult.merge(otherResult);
                } else {
                    // 기존 결과 없으면 복사해서 추가
                    this.results.put(otherResultActorId, otherResult.copy());
                }
            }
        }
    }

    @Data
    @Builder
    public static class Result {
        Long actorId;
        @Builder.Default
        private List<ResultStatusEffectDto> addedStatusEffects = new ArrayList<>();
        @Builder.Default
        private List<ResultStatusEffectDto> removedStatusEffects = new ArrayList<>();
        @Builder.Default
        private List<ResultStatusEffectDto> levelDownedStatusEffects = new ArrayList<>(); // 레벨이 감소되도 표시 (added 에 넣을경우 후처리에서 added 로 간주됨)
        private Integer healValue; // nullable
        private Integer damageValue; // nullable, effectDamage

        public static Result emptyResult() {
            return Result.builder().build();
        }

        private Result copy() {
            return Result.builder()
                    .actorId(this.actorId)
                    .addedStatusEffects(new ArrayList<>(this.addedStatusEffects))
                    .removedStatusEffects(new ArrayList<>(this.removedStatusEffects))
                    .levelDownedStatusEffects(new ArrayList<>(this.levelDownedStatusEffects))
                    .healValue(this.healValue)
                    .damageValue(this.damageValue)
                    .build();
        }

        public Result merge(Result otherResult) {
            if (otherResult == null) return this;
            this.getAddedStatusEffects().addAll(otherResult.getAddedStatusEffects());
            this.getRemovedStatusEffects().addAll(otherResult.getRemovedStatusEffects());
            this.getLevelDownedStatusEffects().addAll(otherResult.getLevelDownedStatusEffects());
            if (otherResult.getHealValue() != null) {
                this.setHealValue(Objects.requireNonNullElse(this.getHealValue(), 0) + otherResult.getHealValue());
            }
            if (otherResult.getDamageValue() != null) {
                this.setDamageValue(Objects.requireNonNullElse(this.getDamageValue(), 0) + otherResult.getDamageValue());
            }
            return this;
        }
    }

}
