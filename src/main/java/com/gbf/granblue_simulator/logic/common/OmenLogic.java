package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelCond;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.BattleStatusDto;
import com.gbf.granblue_simulator.repository.actor.BattleEnemyRepository;
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
     * 결정된 전조를 set
     * @param enemy
     * @param standby
     * @return
     */
    public Move setStandbyMove(BattleEnemy enemy, Move standby) {
        return setSanddbyMove(enemy, standby, null);
    }

    /**
     * 결정된 전조를 set, 전조 해제 초기값 변경시 사용
     * @param enemy
     * @param standby
     * @param modifiedInitialValue : 변경할 전조 해제 초기값
     * @return
     */
    public Move setSanddbyMove(BattleEnemy enemy, Move standby, Integer modifiedInitialValue) {
        if(standby == null) return null;
        Omen omen = standby.getOmen();
        enemy.setCurrentStandbyType(standby.getType());
        if (omen.getOmenType() == OmenType.HP_TRIGGER) enemy.setLatestTriggeredHp(enemy.calcHpRate()); // 이전 발동한 HP 트리거를 재발동하지 않기위한 필드. CHECK 찰나의 순간 대량으로 HP가 깎인경우 이 값이 의도대로 동작하지 않음

        List<OmenCancelCond> omenCancelConds = omen.getOmenCancelConds();
        OmenCancelCond omenCancelCond = omenCancelConds.get((int) (Math.random() * omenCancelConds.size()));
        enemy.setOmenCancelCondIndex(omenCancelConds.indexOf(omenCancelCond));

        Integer initialValue = modifiedInitialValue == null ? omenCancelCond.getInitValue() : modifiedInitialValue;
        enemy.setOmenValue(initialValue);
        return standby;
    }

    /**
     * 다음 전조를 결정.
     *
     * @param enemy
     * @return Optional Move 전조.
     */
    public Optional<Move> determineStandbyMove(BattleEnemy enemy) {
        Move standby = null;
        // 우선순위대로 전조를 결정
        Omen triggeredOmen = null;
        // 영창기 : 로직 내부에서 설정된 다음 영창기 존재시 발동
        triggeredOmen = enemy.getNextIncantStandbyType() != null ? enemy.getActor().getMoves().get(enemy.getNextIncantStandbyType()).getOmen() : null;
        // HP 트리거 : 발동 가능한 HP 트리거 확인 후 발동
        triggeredOmen = triggeredOmen == null ? this.getValidHpTrigger(enemy).orElse(null) : triggeredOmen;
        // 차지어택 : 차지게이지, HP 확인 후 발동
        triggeredOmen = triggeredOmen == null ? this.getValidChargeAttack(enemy).orElse(null) : triggeredOmen;
        log.info("[determineStandbyMove] nextIncantStandbyType = {}, hpRate = {}, ct = {}, ctMax = {}, triggeredOmen = {}", enemy.getNextIncantStandbyType(), enemy.calcHpRate(), enemy.getChargeGauge(), enemy.getMaxChargeGauge(), triggeredOmen);

        standby = triggeredOmen != null ? triggeredOmen.getMove() : null;
        return Optional.ofNullable(standby);
    }

    /**
     * 현재 적의 체력 상태에서 트리거 되는 HP 트리거 또는 차지어택 의 Omen 을 찾아 반환 (HP 트리거 우선)
     *
     * @param enemy
     * @return Omen HpTrigger or ChargeAttack
     */
    protected Optional<Omen> getValidHpTrigger(BattleEnemy enemy) {
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
                ));
    }

    protected Optional<Omen> getValidChargeAttack(BattleEnemy enemy) {
        if (enemy.getChargeGauge() < enemy.getMaxChargeGauge()) return Optional.empty();
        double hpRate = enemy.calcHpRate();
        return enemy.getActor().getMoves().values().stream()
                .filter(move -> move.getType().getParentType().equals(MoveType.STANDBY))
                .map(Move::getOmen)
                .filter(omen -> omen.getOmenType() == OmenType.CHARGE_ATTACK)
                .filter(omen -> hpRate <= omen.getTriggerHps().getFirst()) // CT기는 트리거 1개
                .max(Comparator.comparing(omen -> omen.getTriggerHps().getFirst()));
    }

    // onOtherMove 에서 실행될 전조처리
    public Integer processOmen(BattleEnemy enemy, ActorLogicResult otherResult) {
        Move standbyMove = enemy.getActor().getMoves().get(enemy.getCurrentStandbyType());
        Omen omen = standbyMove.getOmen();
        OmenCancelCond omenCancelCond = omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex());
        int omenValue = enemy.getOmenValue();
        Integer processedOmenValue = this.process(enemy, omenCancelCond, otherResult);
        log.info("[processOmen] beforeOmenValue = {} , processedOmenValue = {}", omenValue, processedOmenValue);

        // REMIND standby 프론트로 넘겨주고 프론트에서 standby 재생중이면 값만 처리하도록
        return processedOmenValue;
    }

    public Integer process(BattleEnemy enemy, OmenCancelCond cancelCond, ActorLogicResult otherResult) {
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
        log.info("OmenLogic.process() type = {}, originValue = {}, modifiedValue = {}", cancelCond.getType(), omenValue, enemy.getOmenValue());
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
