package com.gbf.granblue_simulator.battle.logic.move.enemy;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.util.StatusUtil;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

@Slf4j
@Transactional
@Component
public class HexachromaticWaterEarthLogic extends DefaultEnemyMoveLogic {


    private final String gid = "7300823";

    protected HexachromaticWaterEarthLogic(EnemyMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid, "a"), this::chargeAttackA);
        moveLogicRegistry.register(chargeAttackKey(gid, "b"), this::chargeAttackB);
        moveLogicRegistry.register(chargeAttackKey(gid, "c"), this::chargeAttackC);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 3), this::thirdSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 4), this::fourthSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 5), this::fifthSupportAbility);
        moveLogicRegistry.register("stb_" + gid, this::triggerOmen);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(normalAttack));
    }

    // 전조 발생 [TURN_END_OMEN]
    public MoveLogicResult triggerOmen(MoveLogicRequest request) {
        Enemy self = (Enemy) battleContext.getEnemy();

        BaseOmen triggerOmen = null;

        // HP 트리거
        triggerOmen = omenLogic.getValidHpTrigger(self);

        // CT기
        if (triggerOmen == null && self.getChargeGauge() >= self.getMaxChargeGauge()) {
            // 토룡: B 수룡: C 
            MoveType standbyMoveType = StatusUtil.getEffectByName(self, "토룡의 기운")
                    .map(earthEffect -> StatusUtil.getEffectByName(self, "수룡의 기운")
                            .map(waterEffect -> {
                                if (Objects.equals(earthEffect.getLevel(), waterEffect.getLevel())) {
                                    // 이전에 레벨이 높았던 효과의 전조 발생
                                    return earthEffect.getUpdatedAt().isBefore(waterEffect.getUpdatedAt()) ? MoveType.STANDBY_B : MoveType.STANDBY_C;
                                } else {
                                    return earthEffect.getLevel() > waterEffect.getLevel() ? MoveType.STANDBY_B : MoveType.STANDBY_C;
                                }
                            })
                            .orElse(MoveType.STANDBY_B)
                    ).orElse(MoveType.STANDBY_C); // 반드시 하나이상의 효과가 부여되어있음.
            triggerOmen = self.getBaseOmen(standbyMoveType);
        }

        // 전조발생
        if (triggerOmen == null) return resultMapper.emptyResult(); // 천원은 전조를 직접 지정했을때만 발생

        return omenLogic.triggerOmen(self, triggerOmen)
                .map(standby -> {
                    if (standby.getType() == MoveType.STANDBY_A) {
                        // HP 트리거 인경우 값도 수정해야함
                        omenLogic.manualUpdateOmenValue(self, 6 + 3 * (self.getCurrentForm() - 1),0); // 2페때 9, 3페때 12
                    }
                    return resultMapper.toResult(ResultMapperRequest.from(standby));
                })
                .orElseGet(resultMapper::emptyResult);
    }

    // 전체 대상 [적데미지]10.0배 X 2회 화,풍속성[/적데미지]데미지 / 화,풍속성 열세효과
    // 해제시 2 감소, 실패시 2 상승
    protected MoveLogicResult chargeAttackA(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();

        List<BaseStatusEffect> toApplyEffects = new ArrayList<>(chargeAttack.getBaseMove().getBaseStatusEffects());
        toApplyEffects.addAll(baseMoveService.findByLogicId(supportAbilityKey(gid, 2)).getEffectsGroupByApplyOrder().get(0));

        return resultMapper.fromDefaultResult(defaultChargeAttack(DefaultMoveRequest.builder()
                .move(chargeAttack)
                .selectedBaseEffects(toApplyEffects)
                .plusLevel(2)
                .build()));
    }

    // 인텐시스 플레임: 전체 대상 [적데미지]15.0배 화속성[/적데미지]데미지 / 공격력 감소(누적) 효과
    // 해제시 용의기운 레벨 4 감소
    protected MoveLogicResult chargeAttackB(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 마하3: 랜덤 대상 [적데미지]3.0배 X 12회 풍속성[/적데미지]데미지 / 공격력 감소(누적) 효과
    // 해제시 용의기운 레벨 4 감소
    protected MoveLogicResult chargeAttackC(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // [REACT_SELF] 폼 체인지 후 자신에게 푸른 쐐기 효과
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        if (!request.getOtherResult().isEnemyFormChange()) return resultMapper.emptyResult();
        Move ability = request.getMove();
        Actor self = ability.getActor();

        // 각 효과 합산
        StatusEffect hexaEffect = StatusUtil.getEffectByName(self, "기원의 그릇").orElseThrow(() -> new MoveProcessingException("기원의 그릇 효과 없음, actor.id = " + self.getId()));
        StatusUtil.getEffectByName(self, "화룡의 기운").ifPresent(fireEffect -> {
            setStatusLogic.addStatusEffectsLevel(self, fireEffect.getLevel(), hexaEffect);
            setStatusLogic.removeStatusEffect(self, fireEffect);
        });
        StatusUtil.getEffectByName(self, "풍룡의 기운").ifPresent(windEffect -> {
            setStatusLogic.addStatusEffectsLevel(self, windEffect.getLevel(), hexaEffect);
            setStatusLogic.removeStatusEffect(self, windEffect);
        });
        StatusUtil.getEffectByName(self, "암룡의 기운").ifPresent(darkEffect -> {
            setStatusLogic.addStatusEffectsLevel(self, darkEffect.getLevel(), hexaEffect);
            setStatusLogic.removeStatusEffect(self, darkEffect);
        });
        StatusUtil.getEffectByName(self, "광룡의 기운").ifPresent(lightEffect -> {
            setStatusLogic.addStatusEffectsLevel(self, lightEffect.getLevel(), hexaEffect);
            setStatusLogic.removeStatusEffect(self, lightEffect);
        });

        // 쐐기 효과 제거
        StatusUtil.getEffectByName(self, "붉은 쐐기").ifPresent(redWedge -> {
            setStatusLogic.removeStatusEffect(self, redWedge); // 효과 보여주진 않음 (다른 쐐기 걸리는 효과로 갈음)
        });
        StatusUtil.getEffectByName(self, "황금 쐐기").ifPresent(goldWedge -> {
            setStatusLogic.removeStatusEffect(self, goldWedge); // 효과 보여주진 않음 (다른 쐐기 걸리는 효과로 갈음)
        });

        List<BaseStatusEffect> toApplyEffects = new ArrayList<>(ability.getBaseMove().getBaseStatusEffects()); // 푸른 쐐기 효과
        if (!self.getMember().isBattleStarted()) {
            // 전투 시작 처리중, 턴종효과 발생하지 않으므로 용의기운 레벨 상승 효과 추가
            List<BaseStatusEffect> dragonLevelEffects = baseMoveService.findByLogicId(supportAbilityKey(gid, 2)).getEffectsGroupByApplyOrder().get(0);
            BaseStatusEffect selectedDragonLevelEffect = dragonLevelEffects.get(ThreadLocalRandom.current().nextInt(dragonLevelEffects.size()));
            toApplyEffects.add(selectedDragonLevelEffect);
        }

        DefaultMoveLogicResult defaultResult = defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)); // 푸른 쐐기 + (용의 기운) 효과
        defaultResult.getSetStatusEffectResult().getResults().get(self.getId()).getAddedStatusEffects().add(StatusEffectDto.of(hexaEffect)); // 효과 보여주기
        return resultMapper.fromDefaultResult(defaultResult);
    }

    // [TURN_END] 자신에게 데미지 반사 효과 / 랜덤 용의 기운 레벨 1 상승
    // 용의 기운 레벨은 이쪽에서 가져다 쓰면 됨
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Map<Integer, List<BaseStatusEffect>> effectsGroup = ability.getBaseMove().getEffectsGroupByApplyOrder();

        List<BaseStatusEffect> dragonLevelEffects = effectsGroup.get(0);
        BaseStatusEffect selectedDragonLevelEffect = dragonLevelEffects.get(ThreadLocalRandom.current().nextInt(dragonLevelEffects.size()));

        List<BaseStatusEffect> toApplyEffects = new ArrayList<>(effectsGroup.get(1));
        toApplyEffects.add(selectedDragonLevelEffect);

        // 반사 트리거 무브 저장
        saveTriggeredMove(List.of(ability.getActor()), "enemy_reflect");

        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

    // [REACT_CHARACTER] 전조 해제시 용의 기운 레벨 감소 (통합)
    protected MoveLogicResult thirdSupportAbility(MoveLogicRequest request) {
        Actor self = battleContext.getEnemy();
        if (checkCondition.isEnemyBreak(request.getOtherResult(), MoveType.STANDBY_A)) {
            // HP 트리거 해제
            List<StatusEffect> currentEffects = new ArrayList<>();
            StatusUtil.getEffectByName(self, "수룡의 기운").ifPresent(currentEffects::add);
            StatusUtil.getEffectByName(self, "토룡의 기운").ifPresent(currentEffects::add);
            if (currentEffects.isEmpty()) return resultMapper.emptyResult();

            SetStatusEffectResult setStatusEffectResult = setStatusLogic.subtractStatusEffectLevel(self, 1, currentEffects.toArray(new StatusEffect[0]));
            return resultMapper.toResult(ResultMapperRequest.of(request.getMove(), setStatusEffectResult));

        } else if (checkCondition.isEnemyBreak(request.getOtherResult(), MoveType.STANDBY_C)) {
            // 와무 CT 해제
            return StatusUtil.getEffectByName(self, "수룡의 기운").map(statusEffect ->
                            resultMapper.toResult(ResultMapperRequest.of(
                                    request.getMove(), setStatusLogic.subtractStatusEffectLevel(self, 3, statusEffect))
                            ))
                    .orElse(resultMapper.emptyResult());
        } else if (checkCondition.isEnemyBreak(request.getOtherResult(), MoveType.STANDBY_B)) {
            // 가레용 CT 해제
            return StatusUtil.getEffectByName(self, "토룡의 기운").map(statusEffect ->
                            resultMapper.toResult(ResultMapperRequest.of(
                                    request.getMove(), setStatusLogic.subtractStatusEffectLevel(self, 3, statusEffect))
                            ))
                    .orElse(resultMapper.emptyResult());
        }
        return resultMapper.emptyResult();
    }

    // [TURN_END] 체력 80% 이하 턴 종료시 페이즈 전환
    protected MoveLogicResult fourthSupportAbility(MoveLogicRequest request) {
        Enemy self = (Enemy) battleContext.getEnemy();
        if (self.getOmen() != null) return resultMapper.emptyResult();

        int nextHpRate = 100 - self.getCurrentForm() * 20; // 60 || 40
        if (self.getHpRateInt() > nextHpRate) return resultMapper.emptyResult();

        BaseEnemy currentBaseEnemy = (BaseEnemy) self.getBaseActor();

        // 폼 체인지
        int formOrderByHp = self.getHpRateInt() > 80 ? 1
                : self.getHpRateInt() > 60 ? 2
                  : self.getHpRateInt() > 40 ? 3
                    : 4;
        BaseEnemy nextBaseEnemy = getNextFormBaseEnemy(formOrderByHp);
        // 폼관련 필드처리
        self.updateCurrentForm(formOrderByHp);
        self.updateLatestTriggeredHp(100 - 20 * (formOrderByHp - 1) + 1); // formOrderByHp = 3, latestTriggeredHp = 61

        // 자신의 Move 교체
        List<Move> currentBaseEnemyMoves = self.getMoves().stream()
                .filter(move -> currentBaseEnemy.getDefaultMoveIds().contains(move.getBaseMove().getId()))
                .toList();
        moveService.deleteAll(currentBaseEnemyMoves);
        self.removeMoves(currentBaseEnemyMoves);

        List<BaseMove> nextDefaultBaseMoves = baseMoveService.findAllByIds(nextBaseEnemy.getDefaultMoveIds());
        Map<Long, MoveType> moveTypeById = nextBaseEnemy.getMappedMove().getMoveTypeById();
        List<Move> nextDefaultMoves = nextDefaultBaseMoves.stream().map(nextEnemyBaseMove ->
                Move.fromBaseMove(nextEnemyBaseMove)
                        .mapActor(self)
                        .mapType(moveTypeById.get(nextEnemyBaseMove.getId()))
        ).toList();
        moveService.saveAll(nextDefaultMoves);
        // 엔티티추가는 mapActor 에서 처리

        // 자신의 BaseActor 교체
        self.updateBaseActor(nextBaseEnemy);

        // 자신의 Visual 교체
        ActorVisual nextActorVisual = nextBaseEnemy.getDefaultVisual();
        self.updateActorVisual(nextActorVisual);

        return resultMapper.toResult(ResultMapperRequest.builder()
                .move(request.getMove())
                .executeOptions(ResultMapperRequest.ExecuteOptions.enemyFormChange())
                .build());
    }

    /**
     * 천원에서, 다음 폼의 메타데이터를 가져옴 <br>
     *
     * @param formOrderByHp 현재 HP 에 대응하는 formOrder
     * @return BaseEnemy
     */
    public BaseEnemy getNextFormBaseEnemy(int formOrderByHp) {
        Member currentMember = battleContext.getMember();
        Enemy latestFormEnemy = (Enemy) currentMember.getRoom().getMembers().stream()
                .filter(member -> !member.getId().equals(currentMember.getId()))
                .flatMap(member -> member.getActors().stream())
                .filter(Actor::isEnemy)
                .max(Comparator.comparing(actor -> ((Enemy) actor).getCurrentForm())) // 현재 가장 최신의 form 인 적
                .orElse(null);

        Long nextFormBaseId;

        if (latestFormEnemy != null && latestFormEnemy.getCurrentForm() >= formOrderByHp) {
            // 현재 체력에 맞게 폼체인지 한 적이 있음 -> 해당 폼을 따라감
            nextFormBaseId = latestFormEnemy.getBaseEnemy().getId();
        } else {
            // 다른 멤버의 적이 없거나, 아직 현재 체력에 맞게 폼체인지 한 적이 없음 -> 내가 먼저 폼 결정
            final List<Long> nextFormBaseIds = List.of(10400L, 10500L); // 2, 3페이즈 id
            nextFormBaseId = switch (formOrderByHp) {
                case 2 -> nextFormBaseIds.get(ThreadLocalRandom.current().nextInt(nextFormBaseIds.size()));
                case 3 -> {
                    List<Long> candidates = new ArrayList<>(nextFormBaseIds);
                    Long latestBaseId = latestFormEnemy != null
                            ? latestFormEnemy.getBaseEnemy().getId()
                            : battleContext.getEnemy().getBaseActor().getId();
                    if (candidates.contains(latestBaseId)) {
                        candidates.remove(latestBaseId); // 이전 폼 제외
                    } else {
                        Collections.shuffle(candidates); // 한번에 20% 이상 깎일경우 이전폼 제외가 안될때 보험코드
                    }
                    yield candidates.getFirst();
                }
                case 4 -> 10600L;
                default -> throw new MoveProcessingException("페이즈 진행 에러, 페이즈: " + formOrderByHp);
            };
        }

        return baseEnemyService.findById(nextFormBaseId).orElseThrow(() -> new MoveProcessingException("다음 폼의 메타데이터가 없음."));
    }


    // [BATTLE_START] 중도참여, 로직자체는 firstSupportAbility 와 같음
    protected MoveLogicResult fifthSupportAbility(MoveLogicRequest request) {
//        Move firstSupportAbility = Move.fromBaseMove(baseMoveService.findByLogicId(supportAbilityKey(gid, 1)))
//                .mapActor(request.getMove().getActor())
//                .mapType(MoveType.FIRST_SUPPORT_ABILITY);
//        return firstSupportAbility(MoveLogicRequest.builder().move(firstSupportAbility).otherResult(MoveLogicResult.builder().isEnemyFormChange(true).build()).build());
        return resultMapper.emptyResult();
    }

}
