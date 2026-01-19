package com.gbf.granblue_simulator.battle.logic.system;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.Omen;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelCond;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
@Transactional
public class OmenLogic {

    /**
     * 전조 발생 여부 판단 및 발생
     *
     * @param enemyActor
     * @return Move standby
     */
    public Optional<BaseMove> triggerOmen(Actor enemyActor) {
        Enemy enemy = (Enemy) enemyActor;

        // 1. 다음 전조를 결정
        Optional<BaseMove> standbyOptional = Optional.ofNullable(determineStandbyMove(enemy));
        log.info("[triggerOmen] nextIncantStandbyType = {}, hpRate = {}, ct / max = {} / {}, determinedStandby: standbyOptional = {}", enemy.getNextIncantStandbyType(), enemy.getHpRate(), enemy.getChargeGauge(), enemy.getMaxChargeGauge(), standbyOptional);
        standbyOptional.ifPresent(standby -> {
            // 2. 결정된 다음 전조를 BattleEnemy 엔티티에 set
            setStandbyMove(enemy, standby);
        });

        return standbyOptional;
    }

    /**
     * 전조 값을 갱신 (일반) <br>
     * 주로 전조의 초기값을 갱신할때 사용
     * CHECK 야마토 구현시 1어빌, 바다가르기? 랑 트리제로 뒷면 구현때도 사용할거같음
     *
     * @param enemy
     * @param value
     * @return
     */
    public int updateOmenValue(Enemy enemy, int value) {
        if (value < 0) throw new IllegalArgumentException("[updateOmenValue] value < 0, value = " + value);
        int omenValue = enemy.getOmenValue();
        // 값 갱신
        enemy.updateOmenValue(value);

        log.info("[updateOmenValue][int value] beforeOmenValue = {} , processedOmenValue = {}", omenValue, enemy.getOmenValue());
        return enemy.getOmenValue();
    }

    /**
     * ActorLogicResult 로 전조 값을 갱신
     *
     * @param enemy
     * @param otherResult
     * @return
     */
    public int updateOmenValue(Enemy enemy, ActorLogicResult otherResult) {
        int omenValue = enemy.getOmenValue();

        // ActorLogicResult 에 따른 갱신
        int processedOmenValue = this.updateOmenByOtherResult(enemy, otherResult);

        log.info("[updateOmenValue][ActorLogicResult otherResult] beforeOmenValue = {} , processedOmenValue = {}", omenValue, processedOmenValue);
        return processedOmenValue;
    }

    /**
     * 다음 전조를 결정.
     *
     * @param enemy
     * @return 전조에 따른 standby Move
     */
    protected BaseMove determineStandbyMove(Enemy enemy) {
        // 우선순위대로 전조를 결정
        MoveType nextIncantStandbyType = enemy.getNextIncantStandbyType();
        
        // 0. 다음 영창기 초기화
        enemy.updateNextIncantStandbyType(null); 
        // CHECK nextIncantStandbyType 은 HP 트리거 등이 발동하더라도 초기화 되어야 함. 만약 nextIncantStandbyType 의 발동 조건이 유지되는경우 어차피 다음턴에 다시 발동하므로 조건오염을 방지하기 위해 무조건 초기화
        // CHECK 만약 조건 달성즉시 무조건 발동해야 한다면 영창기(우선) 으로 등록해서 사용
        
        // 1. 영창기 (우선)
        if (nextIncantStandbyType != null && enemy.getMove(nextIncantStandbyType).getOmen().isTriggerPrimary())
            return enemy.getMove(nextIncantStandbyType);

        // 2. HP 트리거
        Omen hpTriggerOmen = this.getValidHpTrigger(enemy);
        if (hpTriggerOmen != null) return hpTriggerOmen.getMove();

        // 3. 영창기
        if (nextIncantStandbyType != null) {
            return enemy.getMove(nextIncantStandbyType);
        }

        // 4. 차지어택
        Omen chargeAttackOmen = this.getValidChargeAttack(enemy);
        if (chargeAttackOmen != null) return chargeAttackOmen.getMove();

        // 없음
        return null;
    }

    /**
     * 결정된 전조를 BattleEnemy 에 set
     *
     * @param enemy
     * @param standby : 결정된 전조 Move
     * @return
     */
    protected BaseMove setStandbyMove(Enemy enemy, BaseMove standby) {
        if (standby == null) return null;
        // 1. 전조 set
        Omen omen = standby.getOmen();
        enemy.updateCurrentStandbyType(standby.getType());
        if (omen.getOmenType() == OmenType.HP_TRIGGER)
            enemy.updateLatestTriggeredHp(enemy.getHpRate()); // 이전 발동한 HP 트리거를 재발동하지 않기위한 필드. CHECK 찰나의 순간 대량으로 HP가 깎인경우 이 값이 의도대로 동작하지 않음
        // 2. 전조 해제 조건 set
        List<OmenCancelCond> omenCancelConds = omen.getOmenCancelConds();
        OmenCancelCond omenCancelCond = omenCancelConds.get((int) (Math.random() * omenCancelConds.size()));
        enemy.updateOmenCancelCondIndex(omenCancelConds.indexOf(omenCancelCond));
        // 2.1 전조 해제조건에 해당하는 값 설정
        Integer initialValue = omenCancelCond.getInitValue();
        enemy.updateOmenValue(initialValue);
        return standby;
    }

    /**
     * 현재 적의 체력 상태에서 트리거 되는 HP 트리거를 찾아 반환
     *
     * @param enemy
     * @return Omen HpTrigger, 없으면 null
     */
    protected Omen getValidHpTrigger(Enemy enemy) {
        double hpRate = enemy.getHpRate();
        double latestTriggeredHp = enemy.getLatestTriggeredHp();
        return enemy.getBaseActor().getMoves().values().stream()
                .filter(move -> move.getType().getParentType().equals(MoveType.STANDBY))
                .map(BaseMove::getOmen)
                .filter(omen -> omen.getOmenType() == OmenType.HP_TRIGGER) // HP_TRIGGER만
                // triggerHps 중 "아직 발동하지 않았고, 현재 HP 이하"인 값이 하나라도 있으면 통과
                .filter(omen ->
                        omen.getTriggerHps().stream()
                                .anyMatch(triggerHp -> hpRate <= triggerHp && triggerHp < latestTriggeredHp)
                )
                // 트리거 가능한 HP 중 가장 높은 값들 뽑은뒤, 그중 제일 작은값을 고름 
                // hpRate 가 39, latestTriggeredHP 가 60 일때 {50, 40}(=40) 과 {60, 45, 30}(=45) 중 {50, 40} 을 고름
                .min(Comparator.comparing(omen ->
                        omen.getTriggerHps().stream()
                                .filter(triggerHp -> triggerHp >= hpRate && triggerHp < latestTriggeredHp)
                                .min(Integer::compareTo)
                                .orElse(Integer.MAX_VALUE)
                )).orElse(null);
    }

    /**
     * 현재 적의 체력 상태에서 트리거되는 차지어택의 Omen 을 찾아 반환
     *
     * @param enemy
     * @return Omen ChargeAttack, 없으면 null
     */
    protected Omen getValidChargeAttack(Enemy enemy) {
        if (enemy.getChargeGauge() < enemy.getMaxChargeGauge()) return null;
        double hpRate = enemy.getHpRate();
        return enemy.getBaseActor().getMoves().values().stream()
                .filter(move -> move.getType().getParentType().equals(MoveType.STANDBY))
                .map(BaseMove::getOmen)
                .filter(omen -> omen.getOmenType() == OmenType.CHARGE_ATTACK)
                .filter(omen -> hpRate <= omen.getTriggerHps().getFirst()) // CT기는 트리거 1개
                .max(Comparator.comparing(omen -> omen.getTriggerHps().getFirst()))
                .orElse(null);
    }

    /**
     * ActorLogicResult 결과에 따라 적의 전조값을 갱신
     *
     * @param enemy
     * @param otherResult
     * @return 갱신된 전조값, 0인경우 해제요망
     */
    protected int updateOmenByOtherResult(Enemy enemy, ActorLogicResult otherResult) {
        BaseMove standbyMove = enemy.getBaseActor().getMoves().get(enemy.getCurrentStandbyType());
        Omen omen = standbyMove.getOmen();
        OmenCancelCond cancelCond = omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex());
        Integer omenValue = enemy.getOmenValue();
        switch (cancelCond.getType()) {
            case HIT_COUNT -> {
                Integer totalHitCount = otherResult.getTotalHitCount();
                enemy.updateOmenValue(Math.max(omenValue - totalHitCount, 0));
            }
            case DAMAGE -> {
                Integer damageSum = getDamageSum(otherResult.getDamages(), otherResult.getAdditionalDamages());
                enemy.updateOmenValue(Math.max(omenValue - damageSum, 0));
            }
            case DEBUFF_COUNT -> {
                int debuffCount = (int) otherResult.getSnapshots().get(enemy.getId()).getAddedStatusEffects().stream()
                        .filter(addedStatusEffect -> addedStatusEffect.getStatusEffectType() == StatusEffectType.DEBUFF)
                        .filter(addedStatusEffect -> !(addedStatusEffect.getName().equals("MISS") || addedStatusEffect.getName().equals("NO EFFECT")))
                        .count(); // int 로 변환해도 무리없음
                enemy.updateOmenValue(Math.max(omenValue - debuffCount, 0));
            }
            case IMPOSSIBLE -> {
                // 해제불가, 아무것도 하지 않음
            }
        }
        log.info("[updateOmenByOtherResult] type = {}, originValue = {}, modifiedValue = {}", cancelCond.getType(), omenValue, enemy.getOmenValue());
        return enemy.getOmenValue();
    }

    /**
     * 데미지 합 구함,
     * 지금 BattleLogService 쪽이랑 중복있으니 차후에 한군데로 통합
     *
     * @return
     */
    protected Integer getDamageSum(List<Integer> damages, List<List<Integer>> additionalDamages) {
        int damageSum = damages.stream().filter(value -> value >= 0).mapToInt(Integer::intValue).sum();
        int additionalDamageSum = additionalDamages.stream()
                .map(additionalDamage -> additionalDamage.stream()
                        .filter(value -> value >= 0)
                        .mapToInt(Integer::intValue)
                        .sum())
                .mapToInt(Integer::intValue)
                .sum();
        return damageSum + additionalDamageSum;
    }

}
