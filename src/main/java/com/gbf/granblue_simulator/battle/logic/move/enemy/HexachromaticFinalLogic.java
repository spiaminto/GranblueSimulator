package com.gbf.granblue_simulator.battle.logic.move.enemy;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

@Slf4j
@Transactional
@Component
public class HexachromaticFinalLogic extends DefaultEnemyMoveLogic {

    private final String gid = "7300843";

    protected HexachromaticFinalLogic(EnemyMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid, "a"), this::chargeAttackA);
        moveLogicRegistry.register(chargeAttackKey(gid, "b"), this::chargeAttackB);
        moveLogicRegistry.register(chargeAttackKey(gid, "c"), this::chargeAttackC);
        moveLogicRegistry.register(chargeAttackKey(gid, "d"), this::chargeAttackD);
        moveLogicRegistry.register(chargeAttackKey(gid, "e"), this::chargeAttackE);
        moveLogicRegistry.register(chargeAttackKey(gid, "f"), this::chargeAttackF);
        moveLogicRegistry.register(chargeAttackKey(gid, "g"), this::chargeAttackG);
        moveLogicRegistry.register(chargeAttackKey(gid, "h"), this::chargeAttackH);
        // moveLogicRegistry.register(chargeAttackKey(gid, "i"), this::chargeAttackI);
        // moveLogicRegistry.register(chargeAttackKey(gid, "j"), this::chargeAttackJ);
        // moveLogicRegistry.register(chargeAttackKey(gid, "k"), this::chargeAttackK);
        moveLogicRegistry.register(chargeAttackKey(gid, "l"), this::chargeAttackL);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 3), this::thirdSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 4), this::fourthSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 5), this::fifthSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 6), this::sixthSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 7), this::seventhSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 8), this::eighthSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 9), this::ninthSupportAbility);
        moveLogicRegistry.register("stb_" + gid, this::triggerOmen);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        if (checkCondition.hasEffect(normalAttack.getActor(), "공룡의 시련").isPresent()) return resultMapper.emptyResult();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 전조 발생 [TURN_END_OMEN]
    public MoveLogicResult triggerOmen(MoveLogicRequest request) {
        Enemy self = (Enemy) battleContext.getEnemy();

        Optional<StatusEffect> trialEffectOptional = checkCondition.hasEffect(self, "공룡의 시련");
        if (trialEffectOptional.isPresent() && trialEffectOptional.get().getDuration() >= 2) {
            // 공룡의 시련 효과시간이 2턴 이상 남았을때 공룡의 시련 영창기 등록
            self.updateNextIncantStandbyType(MoveType.STANDBY_B);
        }

        // 라지오 엑제티움의 경우, 기원의 광륜 효과있을때 해제불가, 아닌경우 해제가능하도록 지정하여 발생
        if (self.getNextIncantStandbyType() == MoveType.STANDBY_L) {
            BaseOmen standbyLOmen = self.getBaseOmen(MoveType.STANDBY_L);
            Optional<Move> standbyLOptional = checkCondition.hasEffect(self, "기원의 광륜")
                    .map(effect -> omenLogic.triggerOmen(self, standbyLOmen, List.of(1))) // 해제불가
                    .orElseGet(() -> omenLogic.triggerOmen(self, standbyLOmen, List.of(0)));
            if (standbyLOptional.isPresent()) {
                return resultMapper.toResult(ResultMapperRequest.from(standbyLOptional.get()));
            }
            log.warn("[triggerOmen] nextIncantStandbyType STANDBY_L, 전조발생 x enemy = {}", self);
        }

        // 전조발생
        MoveType lastStandbyType = self.getLastStandbyType(); // 트리거 전에 미리 구해놓음(CT)
        return omenLogic.triggerOmen(self)
                .map(standby -> {
                    List<MoveType> chargeAttackStandbyTypes = new ArrayList<>(List.of(MoveType.STANDBY_C, MoveType.STANDBY_D, MoveType.STANDBY_E));
                    if (chargeAttackStandbyTypes.contains(standby.getType())) {
                        // CT 전조는 랜덤전조로 교체
                        omenLogic.removeCurrentOmen(self);
                        if (self.getLastStandbyType() != null) {
                            chargeAttackStandbyTypes.remove(lastStandbyType); // 이전에 발생한 전조 연속발생 하지 않음
                        }
                        MoveType standbyType = chargeAttackStandbyTypes.get(ThreadLocalRandom.current().nextInt(chargeAttackStandbyTypes.size()));
                        Move ctStandby = omenLogic.triggerOmen(self, self.getBaseOmen(standbyType)).orElseThrow(() -> new MoveProcessingException("CT 랜덤 전조 발생 오류, 타입: " + standbyType.name() + " 전조: " + self.getBaseOmen(standbyType)));
                        return resultMapper.toResult(ResultMapperRequest.from(ctStandby));
                    }

                    return resultMapper.toResult(ResultMapperRequest.from(standby));
                })
                .orElseGet(resultMapper::emptyResult);
    }


    //40% 진입 ==========================================================================================================

    // 페이즈 변화: 기원의 그릇 합산, 쐐기 제거, 무적효과
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        if (!request.getOtherResult().isEnemyFormChange()) return resultMapper.emptyResult();
        Move ability = request.getMove();
        Actor self = ability.getActor();

        // 각 효과 합산
        StatusEffect hexaEffect = getEffectByName(self, "기원의 그릇").orElseThrow(() -> new MoveProcessingException("기원의 그릇 효과 없음, actor.id = " + self.getId()));
        List<StatusEffect> dragonLevelEffects = getEffectsByNames(self, "화룡의 기운", "수룡의 기운", "토룡의 기운", "풍룡의 기운", "광룡의 기운", "암룡의 기운");
        dragonLevelEffects.forEach(dragonEffect -> setStatusLogic.addStatusEffectsLevel(self, dragonEffect.getLevel(), hexaEffect));
        setStatusLogic.removeStatusEffects(self, dragonLevelEffects);

        // 쐐기 효과 제거
        List<StatusEffect> wedgeEffects = getEffectsByNames(self, "붉은 쐐기", "푸른 쐐기", "황금 쐐기");
        setStatusLogic.removeStatusEffects(self, wedgeEffects); // 제거 효과보여주지 않음

        DefaultMoveLogicResult defaultResult = defaultAbility(ability); // 무적효과
        defaultResult.getSetStatusEffectResult().getResults().get(self.getId()).getAddedStatusEffects().add(StatusEffectDto.of(hexaEffect)); // 효과 보여주기
        return resultMapper.fromDefaultResult(defaultResult);
    }

    // 비콜로르 리베라티오 60배 * 10 데미지 [이후 서포어비 2 발동]
    protected MoveLogicResult chargeAttackA(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // [REACT_SELF] 비콜로르 리베라티오 이후 기원의 그릇 * 3000 무속뎀, 공룡의 시련, 6속 진옥 부여,
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        if (!checkCondition.isMoveType(request.getOtherResult(), MoveType.CHARGE_ATTACK_A))
            return resultMapper.emptyResult();

        List<BaseStatusEffect> toApplyEffects = new ArrayList<>();
        Map<Integer, List<BaseStatusEffect>> groupedEffects = ability.getBaseMove().getEffectsGroupByApplyOrder(); // 0: 공룡의시련 + 진옥 / 1: 방어력 상승 (도중참여)

        // 무속성 데미지
        int plainDamage = getEffectByName(self, "기원의 그릇").map(hexaEffect -> hexaEffect.getLevel() * 3000).orElse(0);
        BaseStatusEffect damageEffect = BaseStatusEffect.builder()
                .uniqueFrame(false)
                .durationType(StatusDurationType.TURN)
                .duration(0)
                .name("기원의 그릇 무속성 데미지")
                .targetType(StatusEffectTargetType.PARTY_MEMBERS)
                .statusModifiers(Map.of(
                        StatusModifierType.ACT_DAMAGE,
                        StatusModifier.builder()
                                .type(StatusModifierType.ACT_DAMAGE)
                                .value(plainDamage)
                                .build()))
                .build();
        toApplyEffects.add(damageEffect);

        // 공룡의 시련
        boolean isPlayingSolo = battleContext.getMember().getRoom().getEnterUserCount() <= 1;
        Optional<StatusEffect> chainEffectOptional = checkCondition.hasEffect(self, "공룡의 족쇄");
        Optional<StatusEffect> finalTrialOptional = checkCondition.hasEffect(self, "최종 시련");
        SetStatusEffectResult enteredAfterPhaseStartedEffectResult = null;
        DefaultMoveLogicResult defaultResult;
        if (chainEffectOptional.isPresent() || finalTrialOptional.isPresent()) {
            // 40% 미만 도중참여 || 최종시련 이후 비콜로르 발생
            // 기원의 그릇 레벨 비례 공격성능 강화 + 무적
            StatusEffect hexaEffect = getEffectByName(self, "기원의 그릇").orElseThrow(() -> new MoveProcessingException("기원의 그릇 효과 없음")); // 레벨 12 고정
            int attackOrderGroup = 3; // fourthSupportAbility 참고
            Map<Integer, List<BaseStatusEffect>> groupedFourthSupportAbilityEffects = baseMoveService.findByLogicId(supportAbilityKey(gid, 4)).getEffectsGroupByApplyOrder();
            for (int i = 0; i <= attackOrderGroup; i++) {
                toApplyEffects.addAll(groupedFourthSupportAbilityEffects.getOrDefault(i, List.of()));
            }
            // 방어력 상승 추가
            toApplyEffects.addAll(groupedEffects.get(2));
            // 최종 시련 없을때, 15% 무적 추가
            if (finalTrialOptional.isEmpty()) toApplyEffects.addAll(groupedFourthSupportAbilityEffects.get(10)); // 15% 무적

            // 기원의 그릇, 공룡의 족쇄, 무적 효과 해제
            List<StatusEffect> toRemoveEffects = new ArrayList<>();
            toRemoveEffects.add(hexaEffect);
            chainEffectOptional.ifPresent(toRemoveEffects::add);
            // 30% 무적 해제
            getEffectsByName(self, "무적").forEach(effect -> {
                if (effect.getModifierValue(StatusModifierType.TAKEN_DAMAGE_INEFFECTIVE_FROM) >= 30) {
                    toRemoveEffects.add(effect);
                }
            });
            enteredAfterPhaseStartedEffectResult = setStatusLogic.removeStatusEffectsWithResult(self, toRemoveEffects);
            // 최종 시련 1턴 연장
            finalTrialOptional.ifPresent(statusEffect -> setStatusLogic.extendStatusEffectDuration(statusEffect, 1));

            defaultResult = defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects));

        } else {
            // 일반 시련 진입
            toApplyEffects.addAll(groupedEffects.get(0)); // 공룡의 시련, 여섯빛깔 장막

            List<BaseStatusEffect> sixMarbles = groupedEffects.get(1); // 진옥
            // 진옥은 1인도전중 3개까지만 부여
            toApplyEffects.addAll(isPlayingSolo ? sixMarbles.subList(0, 3) : sixMarbles);

            defaultResult = defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects));

            // 여섯빛깔 장막 레벨 증가 (일반 시련이 아닌경우 장막 효과를 건드리지 않아야함)
            int bailEffectToLevel = isPlayingSolo ? 2 : 5; // 3, 6
            getEffectByName(self, "여섯빛깔 장막").ifPresent(effect -> setStatusLogic.addStatusEffectsLevel(self, bailEffectToLevel, effect));
        }

        // 40% 미만 입장 효과 추가
        if (enteredAfterPhaseStartedEffectResult != null) {
            defaultResult.getSetStatusEffectResult().merge(enteredAfterPhaseStartedEffectResult);
        }

        return resultMapper.fromDefaultResult(defaultResult);
    }

    //40% 시련 ==========================================================================================================

    // 공룡의 시련 : 적 전체에 20000 무속성 고정데미지
    // 해제시 진옥 1개 해제
    protected MoveLogicResult chargeAttackB(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // [REACT_CHARACTER] 공룡의 시련 해제시 진옥 1개 해제
    protected MoveLogicResult thirdSupportAbility(MoveLogicRequest request) {
        Actor self = battleContext.getEnemy();
        if (!checkCondition.isEnemyBreak(request.getOtherResult(), MoveType.STANDBY_B))
            return resultMapper.emptyResult(); // 공룡의 시련 해제 확인

        // 진옥 1개 해제
        List<StatusEffect> circleEffects = getEffectsByNameContains(self, "룡의 진옥");
        if (circleEffects.isEmpty()) return resultMapper.emptyResult(); // 3명이서 해제시 없을수도있음. 없으면 그냥 스킵
        SetStatusEffectResult removeEffectResult = setStatusLogic.removeStatusEffectsWithResult(self, circleEffects.getFirst());

        // 장막 레벨 감소
        getEffectByName(self, "여섯빛깔 장막").ifPresent(effect -> setStatusLogic.subtractStatusEffectLevel(self, 1, effect));

        return resultMapper.toResult(ResultMapperRequest.of(request.getMove(), removeEffectResult));
    }

    // [TURN_END]<STATUS_POST> 공룡의 시련 해제시 자신의 기원의 그릇에 비례해 공격성능 강화
    // TURN_FINISH 트리거는 전조 발생 후에 발생하므로 너무 늦음
    protected MoveLogicResult fourthSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        Optional<StatusEffect> trialEffectOptional = checkCondition.hasEffect(self, "공룡의 시련");
        if (trialEffectOptional.isEmpty() || trialEffectOptional.get().getDuration() > 1)
            return resultMapper.emptyResult();

        StatusEffect hexaEffect = getEffectByName(self, "기원의 그릇").orElseThrow(() -> new MoveProcessingException("기원의 그릇 효과 없음"));
        int hexaLevel = hexaEffect.getLevel();
        int attackOrderGroup = (hexaLevel - 1) / 4; // 1 ~ 4 (0): 추격 + 무적 / 5 ~ 8 (1): 천명안 / 9 ~ 12 (2): 일반공격 히트수 증가 / 13 ~ 16 (3): 재행동

        List<BaseStatusEffect> toApplyEffects = new ArrayList<>();
        Map<Integer, List<BaseStatusEffect>> groupedEffects = ability.getBaseMove().getEffectsGroupByApplyOrder();
        for (int i = 0; i <= attackOrderGroup; i++) {
            toApplyEffects.addAll(groupedEffects.getOrDefault(i, List.of()));
        }
        toApplyEffects.addAll(groupedEffects.get(10)); // 15% 무적

        DefaultMoveLogicResult defaultResult = defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects));

        // 30% 무적 해제
        getEffectsByName(self, "무적").forEach(effect -> {
            if (effect.getModifierValue(StatusModifierType.TAKEN_DAMAGE_INEFFECTIVE_FROM) >= 30) {
                SetStatusEffectResult removeResult = setStatusLogic.removeStatusEffectsWithResult(self, effect);
                defaultResult.getSetStatusEffectResult().merge(removeResult);
            }
        });

        SetStatusEffectResult removeStatusEffectsWithResult = setStatusLogic.removeStatusEffectsWithResult(self, hexaEffect);
        defaultResult.getSetStatusEffectResult().merge(removeStatusEffectsWithResult);

        return resultMapper.fromDefaultResult(defaultResult);
    }

    // [BATTLE_START] 도중입장시, 비콜로르 리베라티오 발동
    protected MoveLogicResult fifthSupportAbility(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();
        BaseOmen hpTriggerOmen = omenLogic.getValidHpTrigger(self);
        return omenLogic.triggerOmen(self, hpTriggerOmen)
                .map(standby -> resultMapper.toResult(ResultMapperRequest.from(standby)))
                .orElseGet(resultMapper::emptyResult);
    }

    //시련이후 ~ 15% ====================================================================================================

    // [REACT_CHARACTER] 자신이 기원의 그릇 효과 없을때, 어빌리티 사용시 여섯빛깔 쐐기 부여
    protected MoveLogicResult sixthSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        if (checkCondition.hasEffect(self, "기원의 그릇").isPresent()) return resultMapper.emptyResult(); // 기원의 그릇 확인

        if (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.ABILITY)
                || !request.getOtherResult().getMove().getId().equals(battleContext.getCommandAbilityId()))
            return resultMapper.emptyResult(); // 커맨드 어빌리티 확인

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_CHARACTER] 자신이 여섯빛깔 쐐기 6레벨일때, 효과 해제후 적 전체 어빌리티 봉인, 10000 무속뎀
    protected MoveLogicResult seventhSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();

        return checkCondition.hasEffectLevel(self, "여섯빛깔 쐐기", 6).map(wedgeEffect -> {
            DefaultMoveLogicResult defaultResult = defaultAbility(ability); // 10000 무속데미지, 어빌리티 봉인
            SetStatusEffectResult removedEffectResult = setStatusLogic.removeStatusEffectsWithResult(self, wedgeEffect);// 쐐기 제거
            defaultResult.getSetStatusEffectResult().merge(removedEffectResult);
            return resultMapper.fromDefaultResult(defaultResult);
        }).orElse(resultMapper.emptyResult());
    }

    // 엑세이저 라셀라티오: 5 * 8회 수암 데미지, 독효과, 페이탈체인 -30%
    protected MoveLogicResult chargeAttackC(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 클레디스 루체아: 전체 20배 화/광 데미지, 디스펠, 페이탈체인 -30%
    protected MoveLogicResult chargeAttackD(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 아트로피아 임페토스: 10 * 2 풍토 데미지, 강압, 페이탈체인 -30%
    protected MoveLogicResult chargeAttackE(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    //15% 진입 ==========================================================================================================

    // [TURN_END] 최종시련: 최종시련 기원의 광륜(lv2) 를 부여
    protected MoveLogicResult eighthSupportAbility(MoveLogicRequest request) {
        Actor self = request.getMove().getActor();
        if (self.getHpRateInt() > 15 || checkCondition.hasEffect(self, "최종 시련").isPresent())
            return resultMapper.emptyResult();
        if (checkCondition.hasEffect(self, "기원의 그릇").isPresent()) {
            return resultMapper.emptyResult(); // 기원의 그릇이 남아있음 -> 공룡의 시련 도중 또는 아직 비콜로르 보기 전 : 최종시련 시작하지 않음
        }
        // 공룡의 시련이 남아있다면, 시련을 강제진행하며 30% 무적이 풀리지 않음.

        DefaultMoveLogicResult defaultResult = defaultAbility(DefaultMoveRequest.builder().move(request.getMove()).plusLevel(3).build());

        // checkCondition.hasEffect(self, "여섯빛깔 쐐기").ifPresent(effect -> defaultResult.getSetStatusEffectResult().merge(setStatusLogic.removeStatusEffectsWithResult(self, effect)));
        checkCondition.hasEffect(self, "무적").ifPresent(effect -> defaultResult.getSetStatusEffectResult().merge(setStatusLogic.removeStatusEffectsWithResult(self, effect)));

        return resultMapper.fromDefaultResult(defaultResult);
    }

    // 루프레스 알도레 / 트렌스: 전체 화속성 10배 x 2 데미지, 솬석쿨 2턴 연장
    protected MoveLogicResult chargeAttackF(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();
        self.updateNextIncantStandbyType(MoveType.STANDBY_G);
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 루프레스 그라운드 / 템페스타: 전체 수속성 10배 x 2 데미지, 독 (해제불가, 영속)
    protected MoveLogicResult chargeAttackG(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();
        self.updateNextIncantStandbyType(MoveType.STANDBY_H);
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 루프레스 스플렌더 / 테네브리스 : 전체 광 암 10배 x 2  데미지, 오의게이지 상승률 감소 50%
    protected MoveLogicResult chargeAttackH(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();
        self.updateNextIncantStandbyType(MoveType.STANDBY_L);
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 사용중지
    protected MoveLogicResult chargeAttackI(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();
        self.updateNextIncantStandbyType(MoveType.STANDBY_J);
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 사용중지
    protected MoveLogicResult chargeAttackJ(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();
        self.updateNextIncantStandbyType(MoveType.STANDBY_K);
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 사용중지
    protected MoveLogicResult chargeAttackK(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();
        self.updateNextIncantStandbyType(MoveType.STANDBY_L);
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 라지오 엑제티움: 캐릭터 전원 강제전멸
    protected MoveLogicResult chargeAttackL(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();

        DefaultMoveLogicResult defaultResult = defaultChargeAttack(request.getMove());

        battleContext.getFrontCharacters().forEach(character -> character.updateHp(0)); // 보험 1
        self.updateNextIncantStandbyType(MoveType.STANDBY_L); // 보험 2

        return resultMapper.fromDefaultResult(defaultResult);
    }

    // [REACT_CHARACTER] 각 최종시련 전조 해제시, 다음 최종시련 영창기 등록 / 라지오 엑제티움 해제시 자신에게 무속성 9999999999
    protected MoveLogicResult ninthSupportAbility(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();
        if (self.getHpRateInt() > 15 || checkCondition.hasEffect(self, "최종 시련").isEmpty())
            return resultMapper.emptyResult();

        OmenResult omenResult = request.getOtherResult().getOmenResult();
        if (omenResult == null || !omenResult.isOmenBreak()) return resultMapper.emptyResult();

        boolean processLastAbility = false;
        switch (omenResult.getStandbyMoveType()) {
            case STANDBY_F:
                self.updateNextIncantStandbyType(MoveType.STANDBY_G);
                break;
            case STANDBY_G:
                self.updateNextIncantStandbyType(MoveType.STANDBY_H);
                break;
            case STANDBY_H:
                self.updateNextIncantStandbyType(MoveType.STANDBY_L);
                break;
//            case STANDBY_I:
//                self.updateNextIncantStandbyType(MoveType.STANDBY_J);
//                break;
//            case STANDBY_J:
//                self.updateNextIncantStandbyType(MoveType.STANDBY_K);
//                break;
//            case STANDBY_K:
//                self.updateNextIncantStandbyType(MoveType.STANDBY_L);
//                break;
            case STANDBY_L:
                processLastAbility = true;
                break;
        }

        if (!processLastAbility) {
            // 라지오 엑제티움 아닐때, 기원의 광륜 있다면 레벨감소 후 결과반환
            return checkCondition.hasEffect(self, "기원의 광륜")
                    .map(haloEffect -> resultMapper.toResult(ResultMapperRequest.of(request.getMove(), setStatusLogic.subtractStatusEffectLevel(self, 1, haloEffect))))
                    .orElseGet(resultMapper::emptyResult);
        }

        return resultMapper.fromDefaultResult(defaultAbility(request.getMove()));
    }


}

