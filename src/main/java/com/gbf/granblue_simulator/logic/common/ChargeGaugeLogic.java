package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ChargeGaugeLogic {

    private final Integer baseSingleAttackGaugePoint = 10; // 통상공격 첫타 오의게이지
    private final Integer baseDoubleAttackGaugePoint = baseSingleAttackGaugePoint + 12; // 2타 오의게이지
    private final Integer baseTripleAttackGaugePoint = baseSingleAttackGaugePoint + baseDoubleAttackGaugePoint + 15; // 3타 오의게이지
    private final Integer baseEnemyAttackGaugePoint = 1;

    private final Integer baseFatalChainGaugePoint = 15; // 통상 오의시 페이탈 체인 게이지 + 15

    private final Integer baseGaugePointWhenOtherActorChargeAttack = 10; // 다른 캐릭터가 오의쓰면 후열의 캐릭터 오의게이지증가치

    /**
     * 캐릭터의 통상공격, 오의사용 후 오의게이지 설정
     *
     * @param mainActor
     * @param moveType
     */
    public void afterAttack(BattleActor mainActor, List<BattleActor> partyMembers, MoveType moveType) {
        switch (moveType) {
            case SINGLE_ATTACK -> increaseChargeGauge(mainActor, baseSingleAttackGaugePoint);
            case DOUBLE_ATTACK -> increaseChargeGauge(mainActor, baseDoubleAttackGaugePoint);
            case TRIPLE_ATTACK -> increaseChargeGauge(mainActor, baseTripleAttackGaugePoint);
            case CHARGE_ATTACK_DEFAULT -> {
                mainActor.setChargeGauge(0); // 사용자는 초기화
                partyMembers.stream() // 페이탈 체인 게이지 증가
                        .filter(battleActor -> battleActor.getActor().isMainCharacter()).findFirst()
                        .ifPresent(mainCharacter -> increaseFatalChainGauge(mainCharacter, baseFatalChainGaugePoint));
                partyMembers.stream() // 뒷자리 파티 멤버는 오의 게이지 증가
                        .filter(battleActor -> battleActor.getCurrentOrder() > mainActor.getCurrentOrder())
                        .forEach(battleActor -> increaseChargeGauge(battleActor, baseGaugePointWhenOtherActorChargeAttack));
            }
            default -> {
            }
        }
    }


    /**
     * 적의 통상공격, 오의 후 차지턴 설정
     * 적 역시 chargeGauge 필드를 사용. 1, 2, 3... 정수형으로 +1 씩증가. increaseRate 역시 100%, 200% 단위로 설정
     * 적의 공격은 아군의 오의게이지역시 변화시킴
     *
     * @param enemy    주체
     * @param targets  적의 공격타겟
     * @param damages  피해량에 따른 아군 오의게이지 상승을 계산하기 위한 데미지
     * @param moveType 적의 공격타입
     * @param omenType 적의 특수기 타입 (nullable)
     */
    public void afterEnemyAttack(BattleActor enemy, List<BattleActor> targets, List<Integer> damages, MoveType moveType, OmenType omenType) {
        if (moveType.getParentType() != MoveType.CHARGE_ATTACK) {
            increaseChargeGauge(enemy, baseEnemyAttackGaugePoint);
        } else if (omenType == OmenType.CHARGE_ATTACK) {
            enemy.setChargeGauge(0);
        } // omenType 나머지의 경우 게이지 변화없음

        // 적의 공격에 따른 아군의 오의게이지 변화
        for (int i = 0; i < targets.size(); i++) {
            BattleActor target = targets.get(i);
            int damage = damages.get(i);
            double addValue = Math.ceil(100 * ((double) damage / target.getMaxHp()) * 0.5);
            increaseChargeGauge(target, addValue);
        }
    }

    /**
     * SetStatus 에서 오의게이지 업 스테이터스 후처리용 (어빌리티, 오의 등에서 스테이터스 형식으로 오의게이지가 증가하는 경우)
     *
     * @param actor
     * @param chargeGaugeUpEffect
     */
    public void processChargeGaugeFromSetStatus(BattleActor actor, StatusEffect chargeGaugeUpEffect) {
        if (chargeGaugeUpEffect.getType() == StatusEffectType.ACT_CHARGE_GAUGE_UP) { // 검사 안해도 되는데 혹시몰라 함
            increaseChargeGauge(actor, chargeGaugeUpEffect.getValue());
        } else {
            log.warn("[processChargeGaugeFromSetStatus] chargeGaugeUpEffect.type = {}", chargeGaugeUpEffect.getType());
        }
    }

    /**
     * SetStatus 에서 페이탈체인 게이지 업 스테이터스 후처리용 (어빌리티, 오의 등에서 스테이터스 형식으로 페이탈 체인 게이지가 증가하는 경우)
     *
     * @param actor
     * @param fatalChainGaugeUpEffect
     */
    public void processFatalChainGaugeFromSetStatus(BattleActor actor, StatusEffect fatalChainGaugeUpEffect) {
        if (fatalChainGaugeUpEffect.getType() == StatusEffectType.ACT_FATAL_CHAIN_GAUGE_UP) { // 검사 안해도 되는데 혹시몰라 함
            actor.getMember().getBattleActors().stream()
                    .filter(battleActor -> battleActor.getActor().isMainCharacter()).findFirst()
                    .ifPresent(mainCharacter ->
                            increaseFatalChainGauge(mainCharacter, fatalChainGaugeUpEffect.getValue()));
        } else {
            log.warn("[processFatalChainGaugeFromSetStatus] chargeGaugeUpEffect.type = {}", fatalChainGaugeUpEffect.getType());
        }
    }

    /**
     * 실제로 오의게이지를 증가시키는 메서드
     *
     * @param actor
     * @param value 올릴 양
     * @throws IllegalArgumentException value 가 음수면 던짐
     */
    protected int increaseChargeGauge(BattleActor actor, double value) {
        if (value < 0) throw new IllegalArgumentException("[increaseChargeGauge] value 가 음수임. value = " + value);
        Integer maxGauge = actor.getMaxChargeGauge();
        double gaugeIncreaseRate = actor.getChargeGaugeIncreaseRate(); // -1 ~ 1
        int increasedChargeGauge = Math.min(
                (int) Math.ceil(actor.getChargeGauge() + value * (1 + gaugeIncreaseRate)),
                maxGauge
        );
        actor.setChargeGauge(increasedChargeGauge);
        return increasedChargeGauge;
    }

    /**
     * 다른 스테이터스(공포, 오의게이지다운) 등의 영향 없이 오의게이지를 직접 조작, 캐릭터 로직에서 직접 호출해야함.
     * 자신이 오의 즉시 사용가능, 특정 상황에서만 오의게이지 증가하는 캐릭터에서 사용
     * 스테이터스는 사용자에게 표시를 위해 그대로 set 하되 수치는 해당 메서드로 적용
     *
     * @param actor
     * @param value
     */
    public int modifyChargeGaugeManual(BattleActor actor, double value) {
        if (value < 0) throw new IllegalArgumentException("[increaseChargeGauge] value 가 음수임. value = " + value);
        Integer maxGauge = actor.getMaxChargeGauge();
        int increasedChargeGauge = Math.min(
                (int) Math.ceil(actor.getChargeGauge() + value),
                maxGauge
        );
        actor.setChargeGauge(increasedChargeGauge);
        return increasedChargeGauge;
    }

    /**
     * 실제로 페이탈 체인 게이지를 증가시키는 메서드
     *
     * @param mainCharacter 주인공 만
     * @param value
     * @return
     */
    protected int increaseFatalChainGauge(BattleActor mainCharacter, double value) {
        if (mainCharacter.getFatalChainMoveId() == null || mainCharacter.getFatalChainGauge() == null || value < 0)
            throw new IllegalArgumentException("[increaseFatalChainGauge] mainCharacter.fatalChainMoveId = " + mainCharacter.getFatalChainMoveId() + " mainCharacter.fatalChainGauge = " + mainCharacter.getFatalChainGauge() + " value = " + value);
        int increasedFatalChainGauge = Math.min(mainCharacter.getFatalChainGauge() + (int) value, 100);
        mainCharacter.setFatalChainGauge(increasedFatalChainGauge);
        return increasedFatalChainGauge;
    }

}
