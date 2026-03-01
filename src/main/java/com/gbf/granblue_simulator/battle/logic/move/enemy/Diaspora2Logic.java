package com.gbf.granblue_simulator.battle.logic.move.enemy;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;
import static com.gbf.granblue_simulator.metadata.domain.move.MoveType.*;

@Component
@Slf4j
@Transactional
public class Diaspora2Logic extends DefaultEnemyMoveLogic {
    private final String gid = "4300913";

    protected Diaspora2Logic(EnemyMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid, "a"), this::chargeAttackA);
        moveLogicRegistry.register(chargeAttackKey(gid, "b"), this::chargeAttackB);
        moveLogicRegistry.register(chargeAttackKey(gid, "c"), this::chargeAttackC);
        moveLogicRegistry.register(chargeAttackKey(gid, "d"), this::chargeAttackD);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register("stb_" + gid, this::triggerOmen);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    public MoveLogicResult triggerOmen(MoveLogicRequest request) {
        Enemy self = (Enemy) battleContext.getEnemy();

        // 5턴마다 영창기 이성임계 발동
        if ((battleContext.getCurrentTurn() + 1) % 5 == 0) {
            self.updateNextIncantStandbyType(STANDBY_C);
        }

        MoveLogicResult result = omenLogic.triggerOmen(self).map(standby -> {
            if (standby.getType() == STANDBY_B) {
                // 허수몽핵 전조 발생시 자신의 '임계 도달' 레벨에 비례해 해제조건 강화
                checkCondition.hasEffect(self, "임계 도달")
                        .map(statusEffect -> {
                            Integer initValue = self.getBaseOmen(STANDBY_B).getOmenCancelConds().getFirst().getInitValue();
                            return initValue + ((initValue / 2) * statusEffect.getLevel());
                        }).ifPresent(updateValue -> omenLogic.manualUpdateOmenValue(self, updateValue, 0));
            }
            return resultMapper.toResult(ResultMapperRequest.from(standby));
        }).orElseGet(resultMapper::emptyResult);
        return result;
    }

    // 경성방사
    protected MoveLogicResult chargeAttackA(MoveLogicRequest request) {
        List<BaseStatusEffect> baseStatusEffects = new ArrayList<>(request.getMove().getBaseMove().getBaseStatusEffects());
        Collections.shuffle(baseStatusEffects);
        List<BaseStatusEffect> selectedStatusEffect = baseStatusEffects.subList(0, 2);
        return resultMapper.fromDefaultResult(defaultChargeAttack(DefaultMoveRequest.withSelectedBaseStatusEffects(request.getMove(), selectedStatusEffect)));
    }

    // 허수몽핵
    protected MoveLogicResult chargeAttackB(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        double baseDamageRate = chargeAttack.getBaseMove().getDamageRate();
        Double damageRate = checkCondition.hasEffect(chargeAttack.getActor(), "임계 도달")
                .map(statusEffect -> baseDamageRate + (5.0 * statusEffect.getLevel()))
                .orElseGet(() -> baseDamageRate); // 자신의 '임계 도달' 레벨에 비례해 데미지 배율 강화
        return resultMapper.fromDefaultResult(defaultChargeAttack(DefaultMoveRequest.withDamageRate(chargeAttack, damageRate)));
    }

    // 이성임계
    protected MoveLogicResult chargeAttackC(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 인자방출
    protected MoveLogicResult chargeAttackD(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        Actor self = chargeAttack.getActor();
        double baseDamageRate = chargeAttack.getBaseMove().getDamageRate();
        Optional<StatusEffect> factorEffectOptional = checkCondition.hasEffect(self, "자괴인자");
        factorEffectOptional
                .map(statusEffect -> baseDamageRate + (5.0 * statusEffect.getLevel()))
                .orElseGet(() -> baseDamageRate);

        DefaultMoveLogicResult defaultResult = defaultChargeAttack(chargeAttack);

        factorEffectOptional.ifPresent(statusEffect -> {
            battleContext.getFrontCharacters().forEach(
                    partyMember -> getEffectByName(partyMember, "강압").ifPresent(
                            effect -> setStatusLogic.extendStatusEffectDuration(effect, statusEffect.getLevel())
                    )
            );
            // 자괴 인자 삭제
            SetStatusEffectResult removedResult = setStatusLogic.removeStatusEffectsWithResult(self, statusEffect);
            defaultResult.getSetStatusEffectResult().merge(removedResult);
        });

        return resultMapper.fromDefaultResult(defaultResult);
    }

    // 자신의 모드에 따라 받은 데미지의 누적값이 일정 수치에 도달할경우 턴 종료시 (자신의 임계상태 레벨 상승 및) 공격력 증가, 재공격 효과 [TURN_END]
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        return getEffectByNameContains(self, "모드『")
                .flatMap(matchedModeEffect -> checkCondition.hasEffect(self, "임계 상태 레벨")
                        .map(onLimitStatusEffect -> {
                            // 모드에 따른 데미지 합 유형 확인
                            MoveType getDamageSumMoveType = switch (matchedModeEffect.getBaseStatusEffect().getName()) {
                                case "모드『알파』" -> ATTACK;
                                case "모드『베타』" -> ABILITY;
                                case "모드『감마』" -> CHARGE_ATTACK;
                                default -> throw new IllegalArgumentException("맞는 모드 상태효과가 없음");
                            };

                            // 받은 데미지 누적값 확인 및 상승 레벨 계산 (개인)
                            int takenDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(getDamageSumMoveType, false);
                            int addLevel = takenDamageSum / 1500000 + 1 - onLimitStatusEffect.getLevel(); // 모드레벨 1부터 시작하므로 +1
                            log.info("[firstSupportAbility] addLevel = {}, currentLevel = {}, takenDamageSum = {}", addLevel, onLimitStatusEffect.getLevel(), takenDamageSum);
                            if (addLevel <= 0) return resultMapper.emptyResult();

                            // 임계상태 레벨 상승 (내부연산, 결과 반환 x) - CHECK 불가능 하진 않지만 한 행동이 레벨을 2회 올릴수도 있음 그럴때는 다음 행동시 연속으로 적용됨, 모드 레벨은 StatusModifier 가 달려있으므로 임계상태 레벨을 올리도록 설정
                            setStatusLogic.addStatusEffectsLevel(self, addLevel, onLimitStatusEffect);

                            // 가하는 데미지 상승 및 재공격 적용 (결과 반환 ㅇ)
                            return resultMapper.fromDefaultResult(defaultAbility(ability));
                        })).orElseGet(resultMapper::emptyResult);
    }

    // 자신의 이성임계 해제시, 자신의 임계도달 레벨 감소 [REACT_ENEMY]
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        if (!checkCondition.isEnemyBreak(request.getOtherResult(), STANDBY_C)) return resultMapper.emptyResult();
        Move ability = request.getMove();
        Actor self = ability.getActor();
        return checkCondition.hasEffect(self, "임계 도달")
                .map(statusEffect -> resultMapper.toResult(ResultMapperRequest.of(ability, setStatusLogic.subtractStatusEffectLevel(self, 1, statusEffect))))
                .orElseGet(resultMapper::emptyResult);
    }


}
