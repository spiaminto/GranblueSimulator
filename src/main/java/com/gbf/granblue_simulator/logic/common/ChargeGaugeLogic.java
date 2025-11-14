package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.omen.OmenType;
import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusModifierType;
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
    public void afterAttack(Actor mainActor, List<Actor> partyMembers, MoveType moveType) {
        double attackChargeGaugeIncreaseRate = mainActor.getStatus().getStatusDetails().getCalcedAttackChargeGaugeIncreaseRate(); // 일반공격 오의데미지 증가율 (현재 0 or 1.0)
        switch (moveType) {
            case SINGLE_ATTACK -> addChargeGauge(mainActor, baseSingleAttackGaugePoint * attackChargeGaugeIncreaseRate);
            case DOUBLE_ATTACK -> addChargeGauge(mainActor, baseDoubleAttackGaugePoint * attackChargeGaugeIncreaseRate);
            case TRIPLE_ATTACK -> addChargeGauge(mainActor, baseTripleAttackGaugePoint * attackChargeGaugeIncreaseRate);
            case CHARGE_ATTACK_DEFAULT -> {
                setChargeGauge(mainActor, 0); // 사용자는 초기화
                partyMembers.stream() // 페이탈 체인 게이지 증가
                        .filter(battleActor -> battleActor.getBaseActor().isLeaderCharacter()).findFirst()
                        .ifPresent(mainCharacter -> addFatalChainGauge(mainCharacter, baseFatalChainGaugePoint));
                partyMembers.stream() // 뒷자리 파티 멤버는 오의 게이지 증가
                        .filter(battleActor -> battleActor.getCurrentOrder() > mainActor.getCurrentOrder())
                        .forEach(battleActor -> addChargeGauge(battleActor, baseGaugePointWhenOtherActorChargeAttack));
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
    public void afterEnemyAttack(Actor enemy, List<Actor> targets, List<Integer> damages, MoveType moveType, OmenType omenType) {
        if (moveType.getParentType() != MoveType.CHARGE_ATTACK) {
            addChargeGauge(enemy, baseEnemyAttackGaugePoint);
        } else if (omenType == OmenType.CHARGE_ATTACK) {
            setChargeGauge(enemy, 0);
        } // omenType 나머지의 경우 게이지 변화없음

        // 적의 공격에 따른 아군의 오의게이지 변화
        for (int i = 0; i < targets.size(); i++) {
            Actor target = targets.get(i);
            int damage = damages.get(i);
            int addValue = (int) Math.ceil(100 * ((double) damage / target.getMaxHp()) * 0.5);
            addChargeGauge(target, addValue);
        }
    }

    /**
     * SetStatus 에서 오의게이지 업 스테이터스 후처리용 (어빌리티, 오의 등에서 스테이터스 형식으로 오의게이지가 증가하는 경우)
     *
     * @param actor
     * @param chargeGaugeUpBaseStatusEffect
     */
    public void processChargeGaugeFromStatus(Actor actor, BaseStatusEffect chargeGaugeUpBaseStatusEffect) {
        addChargeGauge(actor, (int) chargeGaugeUpBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_CHARGE_GAUGE_UP).getValue());
    }

    /**
     * SetStatus 에서 페이탈체인 게이지 업 스테이터스 후처리용 (어빌리티, 오의 등에서 스테이터스 형식으로 페이탈 체인 게이지가 증가하는 경우)
     *
     * @param actor
     * @param fatalChainGaugeUpBaseStatusEffect
     */
    public void processFatalChainGaugeFromStatus(Actor actor, BaseStatusEffect fatalChainGaugeUpBaseStatusEffect) {
        actor.getMember().getActors().stream()
                .filter(battleActor -> battleActor.getBaseActor().isLeaderCharacter()).findFirst()
                .ifPresent(mainCharacter ->
                        addFatalChainGauge(mainCharacter, fatalChainGaugeUpBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_FATAL_CHAIN_GAUGE_UP).getValue()));
    }

    /**
     * 실제로 오의게이지를 변경하는 메서드
     *
     * @param actor
     * @param setValue set 할 값
     */
    public int setChargeGauge(Actor actor, double setValue) {
        if (setValue < 0) throw new IllegalArgumentException("setValue 가 음수");
        int maxGauge = actor.getMaxChargeGauge();
        int setChargeGauge = (int) Math.min(setValue, maxGauge);
        actor.updateChargeGauge(setChargeGauge);
        return setChargeGauge;
    }

    /**
     * 실제로 오의게이지를 변경하는(더하는) 메서드
     *
     * @param actor
     * @param addValue 더할 값
     * @return
     */
    public int addChargeGauge(Actor actor, double addValue) {
        Integer maxGauge = actor.getMaxChargeGauge();
        double gaugeIncreaseRate = actor.getStatus().getChargeGaugeIncreaseRate(); // -1 ~ 1
        int increasedChargeGauge = Math.min(
                (int) Math.ceil(actor.getChargeGauge() + addValue * (1 + gaugeIncreaseRate)),
                maxGauge
        );
        actor.updateChargeGauge(increasedChargeGauge);
        return increasedChargeGauge;
    }

    /**
     * 실제로 페이탈 체인 게이지를 변경하는 메서드
     *
     * @param mainCharacter 주인공 만
     * @param setValue
     * @return
     */
    public int setFatalChainGauge(Actor mainCharacter, double setValue) {
        if (mainCharacter.getFatalChainMoveId() == null || setValue < 0)
            throw new IllegalArgumentException("[setFatalChainGauge] mainCharacter.fatalChainMoveId = " + mainCharacter.getFatalChainMoveId() + " mainCharacter.fatalChainGauge = " + mainCharacter.getFatalChainGauge() + " value = " + setValue);
        int setChargeGauge = Math.min((int) setValue, 100);
        mainCharacter.updateFatalChainGauge(setChargeGauge);
        return setChargeGauge;
    }

    /**
     * 실제로 페이탈 체인 게이지를 변경하는(더하는) 메서드
     *
     * @param mainCharacter 주인공 만
     * @param addValue
     * @return
     */
    public int addFatalChainGauge(Actor mainCharacter, double addValue) {
        if (mainCharacter.getFatalChainMoveId() == null || addValue < 0)
            throw new IllegalArgumentException("[addFatalChainGauge] mainCharacter.fatalChainMoveId = " + mainCharacter.getFatalChainMoveId() + " mainCharacter.fatalChainGauge = " + mainCharacter.getFatalChainGauge() + " value = " + addValue);
        int increasedFatalChainGauge = Math.min(mainCharacter.getFatalChainGauge() + (int) addValue, 100);
        mainCharacter.updateFatalChainGauge(increasedFatalChainGauge);
        return increasedFatalChainGauge;
    }

}
