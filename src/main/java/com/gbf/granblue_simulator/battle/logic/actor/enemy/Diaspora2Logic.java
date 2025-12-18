package com.gbf.granblue_simulator.battle.logic.actor.enemy;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusResult;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.system.OmenLogic;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.repository.BaseActorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByName;
import static com.gbf.granblue_simulator.metadata.domain.move.MoveType.*;

@Component
@Slf4j
public class Diaspora2Logic extends EnemyLogic {

    public Diaspora2Logic(BattleContext battleContext, EnemyLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, OmenLogic omenLogic, BattleLogService battleLogService, BaseActorRepository baseActorRepository) {
        super(battleContext, resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, omenLogic, battleLogService, baseActorRepository);
    }

    @Override
    public List<ActorLogicResult> processBattleStart() {
        return Collections.emptyList();
    }

    @Override
    public ActorLogicResult attack() {
        return resultMapper.fromDefaultResult(defaultAttack());
    }

    @Override
    public ActorLogicResult chargeAttack() {
        Move chargeAttack = selfMove(self().getCurrentStandbyType().getChargeAttackType());
        double damageRate =
                chargeAttack.getType() == CHARGE_ATTACK_B ? getChargeAttackBDamageRate() : // 허수몽핵
                        chargeAttack.getType() == CHARGE_ATTACK_D ? getChargeAttackDDamageRate() : // 인자방출
                                chargeAttack.getDamageRate(); // 기본배율
        return resultMapper.fromDefaultResult(defaultChargeAttack(damageRate));
    }

    @Override
    public List<ActorLogicResult> postProcessToPartyMove(ActorLogicResult otherResult) {
        List<ActorLogicResult> results = new ArrayList<>();

        // 전조처리
        DefaultActorLogicResult omenResult = this.defaultOmen(otherResult);
        if (omenResult != null) {
            results.add(resultMapper.fromDefaultResult(omenResult));

            if (omenResult.getResultMove().getType() == BREAK_C) { // 이성임계 해제시
                results.add(secondSupportAbility());
            }

        }

        return results;
    }

    @Override
    public List<ActorLogicResult> postProcessToEnemyMove(ActorLogicResult enemyResult) {
        if (enemyResult.getMove().getType() == CHARGE_ATTACK_D) {
            afterChargeAttackD();
        }
        return Collections.emptyList();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd() {
        return List.of(firstSupportAbility());
    }

    @Override
    public List<ActorLogicResult> activateOmen() {
        List<ActorLogicResult> results = new ArrayList<>();

        // 5턴마다 영창기 이성임계 발동
        if ((battleContext.getCurrentTurn() + 1) % 5 == 0)
            setStandbyCEveryFiveTurn();

        // 전조발생
        omenLogic.triggerOmen(self()).ifPresent(standby -> {
            if (standby.getType() == STANDBY_B) {
                omenLogic.updateOmenValue(self(), getStandbyBOmenInitValue()); // 초기값 수정필요한 경우 수정
            }
            results.add(resultMapper.toResultWithOmen(standby, OmenResult.from(self())));
        });

        return results;
    }

    /**
     * [턴 종료시] 자신의 모드에 따라 받은 데미지의 누적값이 일정 수치에 도달할경우 턴 종료시 (자신의 임계상태 레벨 상승 및) 공격력 증가, 재공격 효과
     *
     * @return
     */
    @Override
    protected ActorLogicResult firstSupportAbility() {
        Actor self = self();
        return getEffectByName(self, "모드 『")
                .flatMap(matchedStatusEffect ->
                        getEffectByName(self, "임계 상태").map(onLimitStatusEffect -> {
                            // 모드에 따른 데미지 합 유형 확인
                            MoveType getDamageSumMoveType = switch (matchedStatusEffect.getBaseStatusEffect().getName()) {
                                case "모드 『알파』" -> ATTACK;
                                case "모드 『베타』" -> ABILITY;
                                case "모드 『감마』" -> CHARGE_ATTACK;
                                default -> throw new IllegalArgumentException("맞는 모드 상태효과가 없음");
                            };

                            // 받은 데미지 누적값 확인 및 상승 레벨 계산
                            int takenDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(self, getDamageSumMoveType);
                            int addLevel = takenDamageSum / 100000 + 1 - onLimitStatusEffect.getLevel(); // 모드레벨 1부터 시작하므로 +1 TODO 나중에 수치 바꿀것
                            log.info("[firstSupportAbility] addLevel = {}, currentLevel = {}, takenDamageSum = {}", addLevel, onLimitStatusEffect.getLevel(), takenDamageSum);
                            if (addLevel <= 0) return resultMapper.emptyResult();

                            // 임계상태 레벨 상승 (내부연산, 결과 반환 x) - CHECK 불가능 하진 않지만 한 행동이 레벨을 2회 올릴수도 있음 그럴때는 다음 행동시 연속으로 적용됨, 모드 레벨은 StatusModifier 가 달려있으므로 임계상태 레벨을 올리도록 설정
                            setStatusLogic.addStatusEffectsLevel(self, addLevel, onLimitStatusEffect);

                            // 가하는 데미지 상승 및 재공격 적용 (결과 반환 ㅇ)
                            return resultMapper.fromDefaultResult(defaultAbility(selfMove(FIRST_SUPPORT_ABILITY)));
                        })
                ).orElseGet(resultMapper::emptyResult);
    }

    /**
     * [자신의 이성임계 해제시, BREAK_C] 자신의 임계도달 레벨 감소
     *
     * @return
     */
    @Override
    protected ActorLogicResult secondSupportAbility() {
        SetStatusResult setStatusResult = getEffectByName(self(), "임계 도달")
                .map(battleStatus -> setStatusLogic.subtractStatusEffectLevel(self(), 1, battleStatus))
                .orElse(null);
        return resultMapper.toResult(selfMove(SECOND_SUPPORT_ABILITY), setStatusResult);
    }

    // 기타 표시되지 않는 개인 로직 ======================================================================

    /**
     * 턴 종료시 5의 배수턴마다 이성임계가 발동 (스테이터스로 표시)
     */
    protected void setStandbyCEveryFiveTurn() {
        if (self().getNextIncantStandbyType() == null) // STANDBY_D 가 더 우선
            self().setNextIncantStandbyType(STANDBY_C);
    }

    /**
     * 허수몽핵 (STANDBY_B) 의 경우 임계 도달 레벨에 비례해 해제조건 강화
     *
     * @return
     */
    protected int getStandbyBOmenInitValue() {
        return getEffectByName(self(), "임계 도달").map(
                statusEffect -> 2500000 * (1 + statusEffect.getLevel())
        ).orElse(2500000);
    }

    /**
     * 허수몽핵 (CHARGE_ATTACK_B) 의 경우 임계 도달 레벨에 비례해 데미지 배율 강화
     *
     * @return
     */
    protected double getChargeAttackBDamageRate() {
        return getEffectByName(self(), "임계 도달").map(
                statusEffect -> 10.0 * (1 + statusEffect.getLevel())
        ).orElse(10.0);
    }

    /**
     * 인자방출 (CHARGE_ATTACK_D) 의 경우 자괴인자 레벨에 비례해 데미지 배율 강화
     *
     * @return
     */
    protected double getChargeAttackDDamageRate() {
        return getEffectByName(self(), "자괴인자").map(
                statusEffect -> 5.0 * (1 + statusEffect.getLevel())
        ).orElse(5.0);
    }

    /**
     * [자신이 인자방출 발동시] 인자방출 후 자신의 자괴인자를 삭제, 자괴인자 레벨에 비례해 파티멤버의 강압 효과 연장
     *
     * @return
     */
    protected void afterChargeAttackD() {
        getEffectByName(self(), "자괴인자").ifPresent(
                statusEffect -> {
                    battleContext.getFrontCharacters().forEach(
                            partyMember -> getEffectByName(partyMember, "강압").ifPresent(
                                    effect -> setStatusLogic.extendStatusEffectDuration(effect, statusEffect.getLevel())
                            )
                    );
                    // 자괴 인자 삭제
                    setStatusLogic.removeStatusEffect(self(), statusEffect);
                }
        );


    }
}

