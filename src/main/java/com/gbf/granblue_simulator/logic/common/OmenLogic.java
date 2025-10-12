package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelCond;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.BattleStatusDto;
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
    public Optional<Move> triggerOmen(BattleActor enemyActor) {
        BattleEnemy enemy = (BattleEnemy) enemyActor;

        // 1. 다음 전조를 결정
        Optional<Move> standbyOptional = Optional.ofNullable(determineStandbyMove(enemy));
        log.info("[triggerOmen] determinedStandby: standbyOptional = {}, nextIncantStandbyType = {}, hpRate = {}, ct / max = {} / {}", standbyOptional, enemy.getNextIncantStandbyType(), enemy.calcHpRate(), enemy.getChargeGauge(), enemy.getMaxChargeGauge());
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
    public int updateOmenValue(BattleEnemy enemy, int value) {
        if (value < 0) throw new IllegalArgumentException("[updateOmenValue] value < 0, value = " + value);
        int omenValue = enemy.getOmenValue();
        // 값 갱신
        enemy.setOmenValue(value);

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
    public int updateOmenValue(BattleEnemy enemy, ActorLogicResult otherResult) {
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
    protected Move determineStandbyMove(BattleEnemy enemy) {
        // 우선순위대로 전조를 결정
        // 1. 영창기 
        if (enemy.getNextIncantStandbyType() != null)
            return enemy.getMove(enemy.getNextIncantStandbyType());
        // 2. HP 트리거 
        Omen hpTriggerOmen = this.getValidHpTrigger(enemy);
        if (hpTriggerOmen != null) return hpTriggerOmen.getMove();
        // 3. 차지어택 
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
    protected Move setStandbyMove(BattleEnemy enemy, Move standby) {
        if (standby == null) return null;
        // 1. 전조 set
        Omen omen = standby.getOmen();
        enemy.setCurrentStandbyType(standby.getType());
        if (omen.getOmenType() == OmenType.HP_TRIGGER)
            enemy.setLatestTriggeredHp(enemy.calcHpRate()); // 이전 발동한 HP 트리거를 재발동하지 않기위한 필드. CHECK 찰나의 순간 대량으로 HP가 깎인경우 이 값이 의도대로 동작하지 않음
        // 2. 전조 해제 조건 set
        List<OmenCancelCond> omenCancelConds = omen.getOmenCancelConds();
        OmenCancelCond omenCancelCond = omenCancelConds.get((int) (Math.random() * omenCancelConds.size()));
        enemy.setOmenCancelCondIndex(omenCancelConds.indexOf(omenCancelCond));
        // 2.1 전조 해제조건에 해당하는 값 설정
        Integer initialValue = omenCancelCond.getInitValue();
        enemy.setOmenValue(initialValue);
        return standby;
    }

    /**
     * 현재 적의 체력 상태에서 트리거 되는 HP 트리거를 찾아 반환
     *
     * @param enemy
     * @return Omen HpTrigger, 없으면 null
     */
    protected Omen getValidHpTrigger(BattleEnemy enemy) {
        double hpRate = enemy.calcHpRate();
        double latestTriggeredHp = enemy.getLatestTriggeredHp();
        return enemy.getActor().getMoves().values().stream()
                .filter(move -> move.getType().getParentType().equals(MoveType.STANDBY))
                .map(Move::getOmen)
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
    protected Omen getValidChargeAttack(BattleEnemy enemy) {
        if (enemy.getChargeGauge() < enemy.getMaxChargeGauge()) return null;
        double hpRate = enemy.calcHpRate();
        return enemy.getActor().getMoves().values().stream()
                .filter(move -> move.getType().getParentType().equals(MoveType.STANDBY))
                .map(Move::getOmen)
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
    protected int updateOmenByOtherResult(BattleEnemy enemy, ActorLogicResult otherResult) {
        Move standbyMove = enemy.getActor().getMoves().get(enemy.getCurrentStandbyType());
        Omen omen = standbyMove.getOmen();
        OmenCancelCond cancelCond = omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex());
        Integer omenValue = enemy.getOmenValue();
        switch (cancelCond.getType()) {
            case HIT_COUNT -> {
                Integer totalHitCount = otherResult.getTotalHitCount();
                enemy.setOmenValue(Math.max(omenValue - totalHitCount, 0));
            }
            case DAMAGE -> {
                Integer damageSum = getDamageSum(otherResult.getDamages(), otherResult.getAdditionalDamages());
                enemy.setOmenValue(Math.max(omenValue - damageSum, 0));

            }
            case DEBUFF_COUNT -> {
                int debuffCount = otherResult.getAddedBattleStatusesList().stream()
                        .flatMap(battleStatuses -> battleStatuses.stream()
                                .filter(battleStatus -> !battleStatus.getName().equals("MISS"))
                                .map(BattleStatusDto::getStatusType))
                        .filter(type -> type == StatusType.DEBUFF || type == StatusType.DEBUFF_FOR_ALL)
                        .toList().size();
                enemy.setOmenValue(Math.max(omenValue - debuffCount, 0));
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
        int damageSum = damages.stream().mapToInt(Integer::intValue).sum();
        int additionalDamageSum = additionalDamages.stream()
                .map(additionalDamage -> additionalDamage.stream()
                        .mapToInt(Integer::intValue)
                        .sum())
                .mapToInt(Integer::intValue)
                .sum();
        return damageSum + additionalDamageSum;
    }

}
