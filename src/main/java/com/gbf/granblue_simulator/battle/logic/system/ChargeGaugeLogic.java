package com.gbf.granblue_simulator.battle.logic.system;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifier;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
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
    private final BattleContext battleContext;

    /**
     * 캐릭터의 통상공격, 오의사용 후 오의게이지 설정
     *
     * @param moveType
     */
    public void afterAttack(MoveType moveType) {
        Actor mainActor = battleContext.getMainActor();
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        double attackChargeGaugeIncreaseRate = mainActor.getStatus().getStatusDetails().getCalcedAttackChargeGaugeIncreaseRate(); // 일반공격 오의데미지 증가율 (현재 0 or 1.0)
        switch (moveType) {
            case SINGLE_ATTACK -> addChargeGauge(mainActor, baseSingleAttackGaugePoint * attackChargeGaugeIncreaseRate);
            case DOUBLE_ATTACK -> addChargeGauge(mainActor, baseDoubleAttackGaugePoint * attackChargeGaugeIncreaseRate);
            case TRIPLE_ATTACK -> addChargeGauge(mainActor, baseTripleAttackGaugePoint * attackChargeGaugeIncreaseRate);
            case CHARGE_ATTACK_DEFAULT -> {
                mainActor.updateChargeGauge(0); // 사용자는 초기화
                addFatalChainGauge(baseFatalChainGaugePoint); // 페이탈 체인 게이지 증가
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
     * @param targets  적의 공격타겟
     * @param damages  피해량에 따른 아군 오의게이지 상승을 계산하기 위한 데미지
     * @param moveType 적의 공격타입
     */
    public void afterEnemyAttack(List<Actor> targets, List<Integer> damages, MoveType moveType) {
        Enemy enemy = (Enemy) battleContext.getEnemy();
        
        if (enemy.getMove(enemy.getCurrentStandbyType()).getOmen().getOmenType() == OmenType.CHARGE_ATTACK) {
            setChargeGauge(enemy, 0); // 적의 CT 특수기 -> 0으로 초기화
        } else {
            addChargeGauge(enemy, baseEnemyAttackGaugePoint); // 적의 나머지 특수기, 일반공격 -> add
        }

        // 적의 공격에 따른 아군의 오의게이지 변화
        for (int i = 0; i < targets.size(); i++) {
            Actor target = targets.get(i);
            int damage = damages.get(i);
            int addValue = (int) Math.ceil(100 * ((double) damage / target.getMaxHp()) * 0.5);
            addChargeGauge(target, addValue);
        }
    }

    /**
     * SetStatus 에서 오의게이지 업 상태효과 후처리용 (어빌리티, 오의 등에서 상태효과 부여로 오의게이지가 증가하는 경우)
     *
     * @param actor
     * @param chargeGaugeUpEffect
     */
    public void processChargeGaugeUpFromStatus(Actor actor, StatusEffect chargeGaugeUpEffect) {
        Double chargeGaugeUpValue = chargeGaugeUpEffect.getModifierValue(StatusModifierType.ACT_CHARGE_GAUGE_UP);
        double setGaugeValue = actor.getChargeGauge() + chargeGaugeUpValue;
        this.setChargeGauge(actor, setGaugeValue);
    }

    /**
     * SetStatus 에서 오의게이지 다운 상태효과 후처리용
     * 하나의 상태에 오의 게이지 업 & 다운 모두 있는경우 독립적인 처리를 위해 UP 과 별도로 작성 (게이지 흡수 효과 등)
     *
     * @param actor
     * @param chargeGaugeDownEffect
     */
    public void processChargeGaugeDownFromStatus(Actor actor, StatusEffect chargeGaugeDownEffect) {
        Double chargeGaugeUpValue = chargeGaugeDownEffect.getModifierValue(StatusModifierType.ACT_CHARGE_GAUGE_DOWN);
        double setGaugeValue = actor.getChargeGauge() - chargeGaugeUpValue;
        this.setChargeGauge(actor, setGaugeValue);
    }


    /**
     * SetStatus 에서 페이탈체인 게이지 업 상태효과 후처리용 (어빌리티, 오의 등에서 상태효과 부여로 페이탈 체인 게이지가 증가하는 경우)
     *
     * @param actor
     * @param fatalChainGaugeUpEffect
     */
    public void processFatalChainGaugeUpFromStatus(Actor actor, StatusEffect fatalChainGaugeUpEffect) {
        Double fatalChainGaugeDownValue = fatalChainGaugeUpEffect.getModifierValue(StatusModifierType.ACT_FATAL_CHAIN_GAUGE_DOWN);
        double setGaugeValue = actor.getMember().getFatalChainGauge() - fatalChainGaugeDownValue;
        this.setFatalChainGauge(setGaugeValue);
    }

    /**
     * SetStatus 에서 페이탈체인 게이지 다운 상태효과 후처리용 <br>
     * 하나의 상태에 페이탈 체인 게이지 업 & 다운 모두 있는경우 독립적인 처리를 위해 UP 과 별도로 작성 (사실 이건 거의 없긴 함)
     *
     * @param actor
     * @param fatalChainGaugeDownEffect
     */
    public void processFatalChainGaugeDownFromStatus(Actor actor, StatusEffect fatalChainGaugeDownEffect) {
        Double fatalChainGaugeDownValue = fatalChainGaugeDownEffect.getModifierValue(StatusModifierType.ACT_FATAL_CHAIN_GAUGE_DOWN);
        double setGaugeValue = actor.getMember().getFatalChainGauge() - fatalChainGaugeDownValue;
        this.setFatalChainGauge(setGaugeValue);
    }

    /**
     * 실제로 오의게이지를 변경하는 메서드<br>
     * 오의게이지 증가량 상태가 적용되지 않음!
     *
     * @param actor
     * @param setValue set 할 값
     */
    public int setChargeGauge(Actor actor, double setValue) {
        int maxGauge = actor.getMaxChargeGauge();
        int setChargeGauge = Math.clamp((int) setValue, 0, maxGauge);
        actor.updateChargeGauge(setChargeGauge);
        return setChargeGauge;
    }

    /**
     * 실제로 오의게이지를 변경하는(더하는) 메서드<br>
     * 오의게이지 증가량 상태 적용
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
     *
     *
     * @param value
     * @return
     */
    public int setFatalChainGauge(double value) {
        int setValue = Math.clamp((int) value, 0, 100);
        battleContext.getMember().updateFatalChainGauge(setValue);
        return setValue;
    }

    /**
     * 실제로 페이탈 체인 게이지를 변경하는(더하는) 메서드
     *
     * @param addValue
     * @return
     */
    public int addFatalChainGauge(double addValue) {
        Member member = battleContext.getMember();
        int increasedFatalChainGauge = Math.min(member.getFatalChainGauge() + (int) addValue, 100);
        member.updateFatalChainGauge(increasedFatalChainGauge);
        return increasedFatalChainGauge;
    }

}
