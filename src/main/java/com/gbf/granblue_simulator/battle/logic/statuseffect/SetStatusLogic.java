package com.gbf.granblue_simulator.battle.logic.statuseffect;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.*;
import com.gbf.granblue_simulator.metadata.repository.StatusEffectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SetStatusLogic {

    private final StatusEffectRepository statusEffectRepository;
    private final ProcessStatusLogic processStatusLogic;
    private final BattleContext battleContext;

    /**
     * 단순히 battleActor 에 status 를 붙여줌. 효과 표시 없음 <br>
     * <b>사용 주의!, 동기화시 적에게 동기화된 상태효과 붙일때 에만 사용중</b>
     */
    public void addSyncedStatusEffect(Actor targetActor, StatusEffect statusEffect) {
        StatusEffect addedStatusEffect = StatusEffect.builder()
                .actor(targetActor)
                .duration(statusEffect.getDuration())
                .baseStatusEffect(statusEffect.getBaseStatusEffect())
                .level(statusEffect.getLevel()) // 레벨 유지
                .iconSrc(statusEffect.getIconSrc())
                .build()
                .mapActor(targetActor);
        statusEffectRepository.save(addedStatusEffect);
    }

    /**
     * Move 를 받아 해당 Move 의 BaseStatusEffect 리스트를 타겟에 맞춰 부여
     * '랜덤효과 N개 부여' 인 경우 적용하여 set <br>
     *
     * @param move
     */
    public SetStatusEffectResult setStatusEffect(BaseMove move) {
        List<BaseStatusEffect> baseStatusEffects = move.getBaseStatusEffects();
        if (move.getRandomStatusCount() > 0) {
            // 랜덤효과 N 개 부여
            Collections.shuffle(baseStatusEffects);
            baseStatusEffects = baseStatusEffects.subList(0, move.getRandomStatusCount());
        }
        return this.applyStatusEffect(battleContext.getMainActor(), baseStatusEffects, null, null);
    }

    /**
     * Move 를 받아 해당 Move 의 BaseStatusEffect 리스트를 지정된 타겟에 부여 <br>
     * 주로 적의 랜덤 타겟 공격에 사용 <br>
     * 현재 구현상, 부여되는 BaseStatusEffect.targetType 이 PARTY_MEMBERS 인 경우에 한해, selectedTargets 가 적용됨 (getTargets 참고) <br>
     * '랜덤효과 N개 부여' 인 경우 적용하여 set <br>
     *
     * @param move
     * @param selectedTargets
     */
    public SetStatusEffectResult setStatusEffect(BaseMove move, List<Actor> selectedTargets) {
        List<BaseStatusEffect> baseStatusEffects = move.getBaseStatusEffects();
        if (move.getRandomStatusCount() > 0) {
            // 랜덤효과 N 개 부여
            Collections.shuffle(baseStatusEffects);
            baseStatusEffects = baseStatusEffects.subList(0, move.getRandomStatusCount());
        }
        return this.applyStatusEffect(battleContext.getMainActor(), baseStatusEffects, null, selectedTargets);
    }

    /**
     * BaseStatusEffect list 를 각 타겟에 맞춰 StatusEffect 로 부여
     *
     * @param baseStatusEffects
     */
    public SetStatusEffectResult setStatusEffect(List<BaseStatusEffect> baseStatusEffects) {
        return this.applyStatusEffect(battleContext.getMainActor(), baseStatusEffects, null, null);
    }

    /**
     * BaseStatusEffect list 를 부여, 타겟은 파라미터로 지정된 타겟타입을 사용 (기존 BaseStatusEffect.targetType 은 무시) <br>
     * 주로 SELF 스테이터스의 효과를 PARTY_MEMBERS 로 전체화 할때 사용한다.
     *
     * @param baseStatusEffects
     * @param modifyingTargetType 대체 타겟 타입
     */
    public SetStatusEffectResult setStatusEffect(List<BaseStatusEffect> baseStatusEffects, StatusEffectTargetType modifyingTargetType) {
        return this.applyStatusEffect(battleContext.getMainActor(), baseStatusEffects, modifyingTargetType, null);
    }

    /**
     * BaseStatusEffect list 를 부여, 타겟은 파라미터로 지정된 타겟타입을 사용 (기존 BaseStatusEffect.targetType 은 무시) <br>
     * battleContext.getMainActor() 를 사용할 수 없는 일부 상황에서 사용, 현재 syncLogic 에서 등록된 참전자 버프 적용시 사용중
     *
     * @param baseStatusEffects
     * @param modifyingTargetType 대체 타겟 타입
     */
    public SetStatusEffectResult setStatusEffect(Actor mainActor, List<BaseStatusEffect> baseStatusEffects, StatusEffectTargetType modifyingTargetType) {
        return this.applyStatusEffect(mainActor, baseStatusEffects, modifyingTargetType, null);
    }

    /**
     * 스테이터스를 적용하는 메인로직
     * 여기서 타겟을 정한 뒤 타겟별로 StatusEffect 적용
     *
     * @param baseStatusEffects
     * @param modifiedTargetType
     * @return SetStatusResult 스테이터스 처리 결과 DTO
     */
    protected SetStatusEffectResult applyStatusEffect(Actor mainActor, List<BaseStatusEffect> baseStatusEffects, StatusEffectTargetType modifiedTargetType, List<Actor> selectedTargets) {
        log.info("[applyStatusEffect] mainActor = {}, baseStatusEffects = \n{}, \n modifiedTargetType = {}, selectedTargets = {}", mainActor.getName(), String.join("\n ", baseStatusEffects.stream().map(BaseStatusEffect::toString).toList()), modifiedTargetType, selectedTargets);
        // 처리 순서에 맞게 정렬
        List<BaseStatusEffect> sortedBaseStatusEffects = baseStatusEffects.stream().sorted(Comparator.comparing(BaseStatusEffect::getProcessOrder)).toList();

        Map<Long, SetStatusEffectResult.Result> resultMap = new HashMap<>();

        sortedBaseStatusEffects.forEach(appliedBaseStatusEffect -> {
            // 1. 타겟 타입 변경 여부 처리
            StatusEffectTargetType statusEffectTargetType = modifiedTargetType != null ? modifiedTargetType : appliedBaseStatusEffect.getTargetType();

            // 2. 타겟 확인
            List<Actor> targetActors = this.getTargets(statusEffectTargetType, selectedTargets);

            // 3. 타겟 액터 순회
            targetActors.forEach(targetActor -> {
                SetStatusEffectResult.Result actorResult = resultMap.get(targetActor.getId());
                if (actorResult == null) {
                    actorResult = SetStatusEffectResult.Result.builder().actorId(targetActor.getId()).build();
                    resultMap.put(targetActor.getId(), actorResult);
                }

                // 3.1 즉시 효과가 적용되는 스테이터스 처리 (디스펠, 힐, 오의게이지 상승 등)
                ProcessStatusLogic.ProcessStatusLogicResult processedResult = processImmediateStatusEffect(targetActor, appliedBaseStatusEffect);
                if (processedResult != null) {
                    // 추가 / 갱신된 스테이터스 (DB 저장 x)
                    actorResult.getAddedStatusEffects().addAll(processedResult.getAddedStatusEffects());
                    // 지워진(디스펠, 클리어) 스테이터스
                    actorResult.getRemovedStatusEffects().addAll(processedResult.getRemovedStatusEffects());
                    // 힐
                    if (processedResult.getHealValue() != null) {
                        Integer currentHealValue = actorResult.getHealValue();
                        Integer resultHealValue = currentHealValue == null ? processedResult.getHealValue() : currentHealValue + processedResult.getHealValue();
                        actorResult.setHealValue(resultHealValue);
                    }
                    // 데미지
                    if (processedResult.getDamageValue() != null) {
                        Integer currentDamageValue = actorResult.getDamageValue();
                        Integer resultDamageValue = currentDamageValue == null ? processedResult.getDamageValue() : currentDamageValue + processedResult.getDamageValue();
                        actorResult.setDamageValue(resultDamageValue);
                    }
                    targetActor.getStatus().syncStatus();
                    return;
                }

                StatusEffect displayStatusEffect = null; // 실적용 여부와 상관 없이, 결과로 표시할 스테이터스 (MISS, RESIST, 갱신된 효과, 새로 적용할 효과 모두 포함)

                // 3.2 디버프 명중연산
                displayStatusEffect = applyDebuffResistance(mainActor, targetActor, appliedBaseStatusEffect);
                // 3.2 효과 우열연산
                if (displayStatusEffect == null) {
                    displayStatusEffect = processSuperiority(targetActor, appliedBaseStatusEffect);
                }
                // 3.3 효과 actor 에 실적용 (새로 적용할 효과 저장)
                if (displayStatusEffect == null) {
                    displayStatusEffect = processApplyToActor(targetActor, appliedBaseStatusEffect);
                }

                log.info("[applyStatusEffect] displayStatusEffect = {}", displayStatusEffect);
                targetActor.getStatus().syncStatus();
                // 3.4 결과 리스트에 넣음
                actorResult.getAddedStatusEffects().add(ResultStatusEffectDto.of(displayStatusEffect));
            });
        });

        return SetStatusEffectResult.builder()
                .results(resultMap)
                .build();
    }

    protected List<Actor> getTargets(StatusEffectTargetType targetType, List<Actor> selectedTargets) {
        if (selectedTargets != null && !selectedTargets.isEmpty()
                && targetType == StatusEffectTargetType.PARTY_MEMBERS)
            // CHECK 적의 행동으로 targetType이 ENEMY 와 PARTY_MEMBERS 인 상태효과가 동시에 발생했을때 ENEMY 인경우는 기존 target 설정을 유지(enemy), PARTY_MEMBERS 의 경우 지정된 타겟(실제 피격된 타겟) 사용
            // CHECK 현재 적의 랜덤타겟 공격시 발생하는 상태효과만 해당 기능을 사용하므로 일단 방어적으로 작성. 확장해야 할경우 SELF / ENEMY 두개만 제외하면 될듯
            return selectedTargets;

        Actor mainActor = battleContext.getMainActor();
        Actor leaderActor = battleContext.getLeaderCharacter();
        Actor enemy = battleContext.getEnemy();
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        List<Actor> resultTargets = new ArrayList<>();
        switch (targetType) {
            case SELF -> {
                // 적은 SELF 에서 허용하지 않음
                if (mainActor.isEnemy())
                    throw new IllegalArgumentException("getTargets() Invalid target type from SELF, mainActor:" + mainActor.getName());
                resultTargets.add(mainActor);
            }
            case SELF_AND_NEXT_CHARACTER -> {
                resultTargets.add(mainActor);
                partyMembers.stream().filter(battleActor -> battleActor.getCurrentOrder() == mainActor.getCurrentOrder() + 1).findFirst().ifPresent(resultTargets::add);
            }
            case SELF_AND_LEADER_CHARACTER -> {
                resultTargets.add(mainActor);
                if (leaderActor != null) resultTargets.add(leaderActor);
            }
            case LEADER_CHARACTER -> {
                if (leaderActor != null) resultTargets.add(leaderActor);
            }
            case ENEMY, ALL_ENEMIES -> resultTargets.add(enemy);
            case PARTY_MEMBERS, ALL_PARTY_MEMBERS -> resultTargets.addAll(partyMembers);
            case PARTY_MEMBERS_NOT_SELF ->
                    resultTargets.addAll(partyMembers.stream().filter(battleActor -> !battleActor.getId().equals(mainActor.getId())).toList());
            default -> throw new IllegalArgumentException("getTargets() Invalid target type: " + targetType);
        }
        return resultTargets;
    }

    /**
     * 즉시 처리해야 하는 상태효과 처리 (디스펠, 클리어, 오의게이지 상승/감소, 페이탈체인 게이지 상승/감소, 데미지, 힐) <br>
     * duration == 0 전제
     *
     * @param targetActor
     * @param appliedBaseStatusEffect
     * @return ProcessStatusLogic.ProcessStatusLogicResult, 없을시 null 로 다음단계 진행
     */
    protected ProcessStatusLogic.ProcessStatusLogicResult processImmediateStatusEffect(Actor targetActor, BaseStatusEffect appliedBaseStatusEffect) {
        if (appliedBaseStatusEffect.getDuration() > 0) return null; // 이 조건에서 대부분 걸리므로 쪼개서 리턴
        if (appliedBaseStatusEffect.getStatusModifiers().keySet().stream().noneMatch(StatusModifierType::needPostProcess))
            return null;
        // 임시로 StatusEffect 만들어서 처리
        StatusEffect addedImmediateEffect = StatusEffect.builder()
                .actor(targetActor)
                .duration(appliedBaseStatusEffect.getDuration())
                .baseStatusEffect(appliedBaseStatusEffect)
                .level(appliedBaseStatusEffect.getMaxLevel() > 0 ? 1 : 0) // maxLevel 이 존재하는 레벨제의 경우 시작레벨 1
                .iconSrc(appliedBaseStatusEffect.getIconSrcs().isEmpty() ? "" : appliedBaseStatusEffect.getIconSrcs().getFirst())
                .build(); // mapActor() 하지 말것
        return processStatusLogic.process(targetActor, addedImmediateEffect); // nullable
    }

    /**
     * 디버프 명중 처리
     *
     * @param mainActor   디버프 명중률을 사용할 메인액터
     * @param targetActor 디버프 내성을 사용할 타겟
     * @return 디버프 명중에 실패할 경우 MISS StatusEffect 반환, 명중하면 null 로 다음단계 진행
     */
    protected StatusEffect applyDebuffResistance(Actor mainActor, Actor targetActor, BaseStatusEffect baseStatusEffect) {
        if (baseStatusEffect.getType() != StatusEffectType.DEBUFF || !baseStatusEffect.isResistible())
            return null; // 필중 : 마운트와 약체명중을 관통한다
        // 마운트 처리 - RESIST
        StatusEffect mountResultStatusEffect = getEffectByModifierType(targetActor, StatusModifierType.MOUNT)
                .map(mountBattleStatus -> {
                    mountBattleStatus.expireAtTurnEnd(); // 마운트는 발동시 해당 턴 종료시 삭제됨
                    return StatusEffect.getTransientStatusEffect(baseStatusEffect.getType(), "RESIST", targetActor);
                }).orElse(null);
        if (mountResultStatusEffect != null)
            return mountResultStatusEffect;

        // 약체 명중 처리 - MISS
        double deBuffSuccessRate = mainActor.getStatus().getDebuffSuccessRate();
        double deBuffResistRate = targetActor.getStatus().getDebuffResistRate();
        double debuffAccuracy = deBuffSuccessRate * (1 - deBuffResistRate);
        if (deBuffSuccessRate < 100 && debuffAccuracy < Math.random()) // 디버프 성공률 100.0 이상은 '필중'상태. 등록은 999이상으로 할것임
            // 디버프 명중에 실패할 경우 MISS 반환. 이 MISS 는 DB 에 저장되지 않음. (표시전용)
            return StatusEffect.getTransientStatusEffect(baseStatusEffect.getType(), "MISS", targetActor);
        return null; // 마운트, 명중처리에서 결과가 없다면 명중 -> 새로 추가하기 위해 null 반환
    }

    /**
     * 타겟이 가진 기존 StatusEffect 들과 우열 연산
     *
     * @param targetActor
     * @param appliedBaseStatusEffect
     * @return 기존 효과를 갱신했다면 해당 StatusEffect 반환, 기존 효과와 상호작용이 없거나 우월한 경우 null 로 다음단계 진행
     */
    protected StatusEffect processSuperiority(Actor targetActor, BaseStatusEffect appliedBaseStatusEffect) {
        Map<StatusModifierType, StatusModifier> appliedModifiers = appliedBaseStatusEffect.getStatusModifiers();
        boolean isBasicStatusEffect = appliedModifiers.size() == 1 && appliedModifiers.get(StatusModifierType.UNIQUE) == null; // 기본 상태효과: UNIQUE 아닌 modifier 1개로 구성, 2개 이상인경우 고유 상태효과
        StatusEffect displayStatusEffect = isBasicStatusEffect  // NO EFFECT 또는 갱신된 기존효과
                ? getBasicEffectByModifierTypeAndTargetType(targetActor, appliedBaseStatusEffect.getFirstModifier().getType(), appliedBaseStatusEffect.getTargetType()).map(
                existingStatusEffect -> processSuperiorityByModifier(appliedBaseStatusEffect, existingStatusEffect)).orElse(null)
                : getEffectByBaseId(targetActor, appliedBaseStatusEffect.getId()).map(
                existingStatusEffect -> this.processSuperiorityById(appliedBaseStatusEffect, existingStatusEffect)).orElseGet(() -> null);
        return displayStatusEffect;
    }

    /**
     * 발생한 BaseStatusEffect 가 기존 StatusEffect.BaseStatusEffect 와 id 가 같은경우 기존 StatusEffect 를 갱신 한 후 반환 <br>
     * 완전히 동일한 기본 상태효과 또는 고유 상태효과 연산시 사용
     *
     * @param baseStatusEffect 발생한 BaseStatusEffect
     * @param statusEffect     발생한 BaseStatusEffect 와 Status.id 가 같은 기존 StatusEffect
     * @return 갱신된 기존 StatusEffect
     */
    private StatusEffect processSuperiorityById(BaseStatusEffect baseStatusEffect, StatusEffect statusEffect) {
        statusEffect.resetDuration(); // 일단 효과시간 초기화

        if (baseStatusEffect.getMaxLevel() > 0 && statusEffect.getLevel() < baseStatusEffect.getMaxLevel()) {
            // 레벨제 스테이터스 && 최고레벨 아닌경우 레벨증가
            statusEffect.addLevel(1);
        }
        return statusEffect;
    }

    /**
     * 발생한 BaseStatusEffect 가 기존 StatusEffect 와 동일한 Modifier 의 기본 상태효과 일 시, 기존 StatusEffect 와의 우열 연산
     *
     * @param appliedBaseStatusEffect 발생한 스테이터스 효과 (기본 상태효과)
     * @param existStatusEffect       기존의 스테이터스 효과 (기본 상태효과, 발생한 스테이터스 효과와 modifierType 과 targetType 이 같아야 함)
     * @return 갱신된 기존 StatusEffect (새로운 상한의 누적제 포함) 또는 NO EFFECT 또는 null 을 통해 다음진행
     */
    private StatusEffect processSuperiorityByModifier(BaseStatusEffect appliedBaseStatusEffect, StatusEffect existStatusEffect) {
        log.info("[applyDuplicatedBasicStatusEffect] \n addedStatusEffect = {}, \n existStatusEffect = {}", appliedBaseStatusEffect, existStatusEffect);
        if (appliedBaseStatusEffect.getMaxLevel() > 0) {
            // 1. 기본 상태효과 (누적), 레벨제
            if (appliedBaseStatusEffect.getMaxLevel() > existStatusEffect.getBaseStatusEffect().getMaxLevel()) {
                // 1.1 발생한 스테이터스 효과가 기존보다 상한이 높음
                // 기존 스테이터스 삭제
                this.removeStatusEffect(existStatusEffect.getActor(), existStatusEffect);
                // CHECK 누적식 상한 갱신에 한해, 여기서 직접 저장하고 반환 - 레벨을 이어갈 필요가 있음.
                Actor targetActor = existStatusEffect.getActor();
                StatusEffect addedStatusEffect = StatusEffect.builder()
                        .actor(targetActor)
                        .duration(appliedBaseStatusEffect.getDuration())
                        .baseStatusEffect(appliedBaseStatusEffect)
                        .level(existStatusEffect.getLevel()) // 기존 레벨 이어서 가져감
                        .iconSrc(appliedBaseStatusEffect.getIconSrcs().isEmpty() ? "" : appliedBaseStatusEffect.getIconSrcs().getFirst())
                        .build()
                        .mapActor(targetActor);
                this.addStatusEffectsLevel(targetActor, 1, addedStatusEffect); // 1 증가
                statusEffectRepository.save(addedStatusEffect);
                return addedStatusEffect;
            } else {
                // 1.2 기존 스테이터스 효과가 상한이 높음
                existStatusEffect.resetDuration(); // 효과시간 초기화
                addStatusEffectsLevel(existStatusEffect.getActor(), 1, existStatusEffect); // 레벨 상승
                // 누적식은 원본 설계상 NO EFFECT 가 없고, 반드시 효과시간 초기화 및 효과 적용 (상한에 막힐시 실제론 미적용) 한다.
                return existStatusEffect;
            }
        } else {
            // 2. 레벨제 스테이터스가 아님
            double inputStatusEffectValue = appliedBaseStatusEffect.getFirstModifier().getInitValue();
            double currentStatusEffectValue = existStatusEffect.getBaseStatusEffect().getFirstModifier().getInitValue();
            if (inputStatusEffectValue >= currentStatusEffectValue) {
                // 2.1 입력 스테이터스 효과값이 같거나 크면 기존 삭제, 갱신준비
                this.removeStatusEffect(existStatusEffect.getActor(), existStatusEffect);
                return null;
            }
            // 2.2 입력 스테이터스 효과 값이 작으면 NO EFFECT
            return StatusEffect.getTransientStatusEffect(appliedBaseStatusEffect.getType(), "NO EFFECT", existStatusEffect.getActor()); // 발생효과가 덮어 씌워졌으면 NO EFFECT 반환 (DB 저장x, 표시용)
        }
    }

    /**
     * 새로 발생한 효과가 조건을 모두 통과한경우, StatusEffect 로 생성하여 저장
     *
     * @param targetActor
     * @param appliedBaseStatusEffect
     * @return 저장된 StatusEffect
     */
    protected StatusEffect processApplyToActor(Actor targetActor, BaseStatusEffect appliedBaseStatusEffect) {
        // 새로 추가되는 스테이터스
        StatusEffect addedStatusEffect = StatusEffect.builder()
                .actor(targetActor)
                .duration(appliedBaseStatusEffect.getDuration())
                .baseStatusEffect(appliedBaseStatusEffect)
                .level(appliedBaseStatusEffect.getMaxLevel() > 0 ? 1 : 0) // maxLevel 이 존재하는 레벨제의 경우 시작레벨 1
                .iconSrc(appliedBaseStatusEffect.getIconSrcs().isEmpty() ? "" : appliedBaseStatusEffect.getIconSrcs().getFirst())
                .build()
                .mapActor(targetActor);
        statusEffectRepository.save(addedStatusEffect);
        return addedStatusEffect;
    }

    /**
     * battleActor 의 battleStatus 중 statusIds 에 해당하는 모든 스테이터스의 레벨을 level 만큼 증가
     * 사용자에게 결과 반환 없이 임의로 레벨 조작시 사용 <br>
     * 최고 레벨 이상 증가하지 않음
     *
     * @param actor         증가시킬 대상
     * @param level         증가량 (목표 값이 아님)
     * @param statusEffects 증가시킬 battleStatus[], null 인경우 경고 후 스킵
     */
    public void addStatusEffectsLevel(Actor actor, int level, StatusEffect... statusEffects) {
        if (statusEffects == null) {
            log.warn("[addStatusEffectsLevel] null statusEffects, actor = {}", actor);
            return;
        }
        for (StatusEffect statusEffect : statusEffects) {
            statusEffect.addLevel(level);
        }

        actor.getStatus().syncStatus(); // 갱신
    }

    /**
     * Actor 의 StatusEffects 중 statusId 에 해당하는 StatusEffect의 레벨을 level 만큼 감소
     * 레벨 감소의 경우 사용자에게 감소정보를 노출해야하기 때문에 SetStatusResult 를 반환 <br>
     * 레벨 감소시 0 이하면 해당 효과 제거 <br>
     *
     * @param actor
     * @param level         감소량 (목표 값이 아님)
     * @param statusEffects 레벨이 감소할 statusEffects
     * @return setStatusResult
     */
    public SetStatusEffectResult subtractStatusEffectLevel(Actor actor, int level, StatusEffect... statusEffects) {
        if (statusEffects == null) {
            log.warn("[subtractStatusEffectLevel] null StatusEffect, actor = {}", actor);
            return null;
        }
        List<ResultStatusEffectDto> levelDownedStatusEffects = new ArrayList<>(); // 레벨이 내려감
        List<ResultStatusEffectDto> removedStatusEffects = new ArrayList<>(); // 레벨이 내려가서 삭제됨
        Map<Long, SetStatusEffectResult.Result> resultMap = new HashMap<>();

        for (StatusEffect statusEffect : statusEffects) {
            statusEffect.subtractLevel(level);

            if (statusEffect.getLevel() <= 0) {
                this.removeStatusEffect(actor, statusEffect);
                removedStatusEffects.add(ResultStatusEffectDto.of(statusEffect));
            } else {
                levelDownedStatusEffects.add(ResultStatusEffectDto.of(statusEffect));
            }
        }
        SetStatusEffectResult.Result result = SetStatusEffectResult.Result.builder()
                .actorId(actor.getId())
                .levelDownedStatusEffects(levelDownedStatusEffects)
                .removedStatusEffects(removedStatusEffects)
                .build();
        resultMap.put(actor.getId(), result);

        actor.getStatus().syncStatus();

        return SetStatusEffectResult.builder()
                .results(resultMap)
                .build();

    }

    /**
     * 배틀 스테이터스들의 효과를 모두 같은 duration 만큼 연장
     *
     * @param statusEffects
     * @param duration
     */
    public void extendStatusEffectsDuration(List<StatusEffect> statusEffects, Integer duration) {
        statusEffects.forEach(battleStatus -> this.extendStatusEffectDuration(battleStatus, duration));
    }

    /**
     * 배틀 스테이터스의 효과를 duration 만큼 연장
     *
     * @param statusEffect
     * @param duration
     */
    public void extendStatusEffectDuration(StatusEffect statusEffect, Integer duration) {
        if (statusEffect.getDuration() > 0)
            statusEffect.addDuration(duration); // 남은 시간이 1턴 이상이여야 연장가능 (0턴은 의사적으로 감소시킨 수치)
    }

    /**
     * 배틀 스테이터스들의 효과를 모두 같은 duration 만큼 단축
     *
     * @param statusEffects
     * @param duration
     */
    public List<StatusEffect> shortenStatusEffectsDuration(List<StatusEffect> statusEffects, Integer duration) {
        statusEffects.forEach(battleStatus -> this.shortenStatusEffectDuration(battleStatus, duration));
        return statusEffects;
    }

    /**
     * 배틀 스테이터스의 효과를 duration 만큼 단축, duration 이 0 이되면 삭제
     *
     * @param statusEffect
     * @param duration
     */
    public StatusEffect shortenStatusEffectDuration(StatusEffect statusEffect, Integer duration) {
        statusEffect.subtractDuration(duration);
        if (statusEffect.getDuration() <= 0) {
            statusEffectRepository.delete(statusEffect);
            statusEffect.getActor().getStatusEffects().remove(statusEffect);
            statusEffect.getActor().getStatus().syncStatus();
        }
        return statusEffect;
    }

    /**
     * 배틀 스테이터스를 삭제. 참전자 전체 적용 스테이터스 삭제하는 메서드로 수정예정
     * 일반 제거가 아닌 로직으로 인한 제거에 사용. 소거불가도 이걸로 제거.
     *
     * @param actor
     * @param statusEffects
     * @return 제거된 효과
     */
    public List<StatusEffect> removeStatusEffects(Actor actor, List<StatusEffect> statusEffects) {
        actor.getStatusEffects().removeAll(statusEffects);
        statusEffectRepository.deleteAll(statusEffects);
        actor.getStatus().syncStatus();
        return statusEffects;
    }

    /**
     * 배틀 스테이터스를 삭제. 참전자 전체 적용 스테이터스 삭제하는 메서드로 수정예정
     * 일반 제거가 아닌 로직으로 인한 제거에 사용. 소거불가도 이걸로 제거.
     *
     * @param actor
     * @param statusEffect
     * @return 제거된 효과
     */
    public StatusEffect removeStatusEffect(Actor actor, StatusEffect statusEffect) {
        if (statusEffect == null) return null;
        actor.getStatusEffects().remove(statusEffect);
        statusEffectRepository.delete(statusEffect);
        actor.getStatus().syncStatus();
        return statusEffect;
    }

    /**
     * 상태효과 삭제하고 사용자에게 노출하기 위한 결과까지 반환
     *
     * @param actor
     * @param statusEffects
     * @return setStatusEffectResult
     */
    public SetStatusEffectResult removeStatusEffectsWithResult(Actor actor, StatusEffect... statusEffects) {
        return removeStatusEffectsWithResult(actor, Arrays.asList(statusEffects));
    }

    /**
     * 상태효과 삭제하고 사용자에게 노출하기 위한 결과까지 반환
     *
     * @param actor
     * @param statusEffects
     * @return setStatusEffectResult
     */
    public SetStatusEffectResult removeStatusEffectsWithResult(Actor actor, List<StatusEffect> statusEffects) {
        List<ResultStatusEffectDto> removedStatusEffects = new ArrayList<>(); // 삭제됨
        Map<Long, SetStatusEffectResult.Result> resultMap = new HashMap<>();

        for (StatusEffect statusEffect : statusEffects) {
            actor.getStatusEffects().remove(statusEffect);
            statusEffectRepository.delete(statusEffect);
            removedStatusEffects.add(ResultStatusEffectDto.of(statusEffect));
        }

        SetStatusEffectResult.Result result = SetStatusEffectResult.Result.builder()
                .actorId(actor.getId())
                .removedStatusEffects(removedStatusEffects)
                .build();
        resultMap.put(actor.getId(), result);

        actor.getStatus().syncStatus();

        return SetStatusEffectResult.builder()
                .results(resultMap)
                .build();
    }

    /**
     * 시간제 스테이터스 효과중 효과 시간이 종료된 효과를 삭제 <br>
     * 현재 적만 시간제 스테이터스 효과가 구현되어있으므로 적만 기능 구현
     *
     * @param enemy
     * @param partyMembers
     */
    public void removeExpiredTimeBasedStatusEffects(Actor enemy, List<Actor> partyMembers) {
        List<StatusEffect> expiredAllTargetEnemyStatusEffects = getEffectsByTargetType(enemy, StatusEffectTargetType.ALL_ENEMIES).stream()
                .filter(StatusEffect::isExpired).toList();
        this.removeStatusEffects(enemy, expiredAllTargetEnemyStatusEffects);
    }


}
