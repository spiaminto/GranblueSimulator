package com.gbf.granblue_simulator.battle.logic.statuseffect;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.move.dto.SetEffectRequest;
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
     * BaseStatusEffect list 를 BaseStatusEffect.getTargetType 에 맞춰 부여
     *
     * @param baseStatusEffects
     */
    public SetStatusEffectResult setStatusEffect(List<BaseStatusEffect> baseStatusEffects) {
        return this.applyStatusEffect(SetEffectRequest.ofList(baseStatusEffects));
    }

    /**
     * 요청 정보에 맞게 상태효과를 부여
     */
    public SetStatusEffectResult setStatusEffect(SetEffectRequest request) {
        return this.applyStatusEffect(request);
    }

    /**
     * 상태효과를 적용하는 메인로직 <br>
     * 상태효과 부여 대상은 BaseStatusEffect.targetType 을 기본으로 사용하되, request.selectedTargets 가 존재할시 targetType 을 무시하고 해당 타겟을 사용 <br>
     *
     * @return SetStatusResult 스테이터스 처리 결과 DTO
     */
    protected SetStatusEffectResult applyStatusEffect(SetEffectRequest request) {
        Actor mainActor = battleContext.getMainActor();
        List<BaseStatusEffect> baseStatusEffects = request.getBaseStatusEffects();
        List<Actor> selectedTargets = request.getSelectedTargets();
        List<Actor> enemyAttackTargets = request.getEnemyAttackTargets();
        int toLevel = request.getToLevel();

        log.info("[applyStatusEffect] mainActor = {}, baseStatusNames = {}", mainActor.getName(), String.join("\n ", baseStatusEffects.stream().map(BaseStatusEffect::toString).toList()));
        Map<Long, SetStatusEffectResult.Result> resultMap = new HashMap<>();

        // 1. 처리 순서에 맞게 정렬
        List<BaseStatusEffect> sortedBaseStatusEffects = baseStatusEffects.stream().sorted(Comparator.comparing(BaseStatusEffect::getProcessOrder)).toList();

        sortedBaseStatusEffects.forEach(appliedBaseEffect -> {
            // 2. 타겟 설정
            List<Actor> targets = selectedTargets != null ? selectedTargets : this.getDefaultTargets(appliedBaseEffect.getTargetType(), enemyAttackTargets);

            // 3. 타겟 액터 순회
            targets.forEach(targetActor -> {
                SetStatusEffectResult.Result actorResult = resultMap.get(targetActor.getId());
                if (actorResult == null) {
                    actorResult = SetStatusEffectResult.Result.builder().actorId(targetActor.getId()).build();
                    resultMap.put(targetActor.getId(), actorResult);
                }

                // 3.1 즉시 효과가 적용되는 스테이터스 처리 (디스펠, 힐, 오의게이지 상승 등)
                if (appliedBaseEffect.getDuration() <= 0 && appliedBaseEffect.getModifiers().keySet().stream().anyMatch(StatusModifierType::needPostProcess)) {
                    SetStatusEffectResult.Result immediateResult = applyImmediateStatusEffect(targetActor, appliedBaseEffect);
                    actorResult.merge(immediateResult);
                    return;
                }

                // 3.2 디버프 명중연산
                StatusEffect registEffect = applyDebuffResistance(mainActor, targetActor, appliedBaseEffect);
                if (registEffect != null) {
                    actorResult.getAddedStatusEffects().add(StatusEffectDto.of(registEffect));
                    return;
                }

                // 3.3 효과 우열연산 및 효과 생성
                StatusEffect resultEffect = processSuperiority(targetActor, appliedBaseEffect);
                log.info("[applyStatusEffect] resultEffect = {}", resultEffect);

                // 3.4.1 결과 리스트에 넣음
                if (!resultEffect.isTransient()) {
                    resultEffect.mapActor(targetActor);
                    if (appliedBaseEffect.isUniqueFrame() && appliedBaseEffect.getMaxLevel() > 0) {
                        // 고유항 && 레벨제 인 경우
                        if (toLevel > 0) {
                            resultEffect.addLevel(toLevel); // 요청레벨 반영
                        }
                        if (appliedBaseEffect.isConditionalModifier()) {
                            resultEffect.updateActiveModifierCount(resultEffect.getLevel()); // 레벨에 비례해 효과 반영
                        }
                    }
                    statusEffectRepository.save(resultEffect);
                    targetActor.getStatus().syncStatus();
                }
                actorResult.getAddedStatusEffects().add(StatusEffectDto.of(resultEffect)); // transient no_effect 등도 결과에는 삽입됨.

            });
        });

        return SetStatusEffectResult.builder()
                .results(resultMap)
                .build();
    }

    /**
     * BaseStatusEffect.targetType 으로 기본 타겟 설정
     *
     * @param targetType
     * @return 부여 대상
     */
    protected List<Actor> getDefaultTargets(StatusEffectTargetType targetType, List<Actor> enemyAttackTargets) {
        Actor mainActor = battleContext.getMainActor();
        Actor leaderActor = battleContext.getLeaderCharacter();
        Actor enemy = battleContext.getEnemy();
        List<Actor> partyMembers = enemyAttackTargets != null ? enemyAttackTargets : battleContext.getFrontCharacters();
        List<Actor> resultTargets = new ArrayList<>();

        // 적은 SELF 에서 허용하지 않음
        if (targetType == StatusEffectTargetType.SELF && mainActor.isEnemy())
            throw new IllegalArgumentException("getTargets() Invalid target type from SELF, mainActor:" + mainActor.getName());

        switch (targetType) {
            case SELF -> resultTargets.add(mainActor);
            case SELF_AND_NEXT_CHARACTER -> {
                partyMembers.stream().filter(battleActor -> battleActor.getCurrentOrder() == mainActor.getCurrentOrder() + 1).findAny().ifPresent(resultTargets::add);
                resultTargets.add(mainActor);
            }
            case SELF_AND_LEADER_CHARACTER -> {
                if (leaderActor != null) resultTargets.add(leaderActor);
                resultTargets.add(mainActor);
            }
            case SELF_AND_LOWEST_HP_CHARACTER -> {
                resultTargets.add(mainActor);
                partyMembers.stream().filter(actor -> !actor.getId().equals(mainActor.getId())).min(Comparator.comparing(Actor::getHpRateInt)).ifPresent(resultTargets::add);
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
     * @return SetStatusEffectResult.Result, nullable
     */
    protected SetStatusEffectResult.Result applyImmediateStatusEffect(Actor targetActor, BaseStatusEffect appliedBaseEffect) {
        ProcessStatusLogic.ProcessStatusLogicResult processedResult = processStatusLogic.process(battleContext.getMainActor(), targetActor, StatusEffect.fromBaseEffect(appliedBaseEffect, targetActor)); // 변환해서 넘길것
        SetStatusEffectResult.Result result = SetStatusEffectResult.Result.emptyResult();
        if (processedResult != null) {
            result.setActorId(targetActor.getId());
            // 추가 / 갱신된 스테이터스 (DB 저장 x)
            result.getAddedStatusEffects().addAll(processedResult.getAddedStatusEffects());
            // 지워진(디스펠, 클리어) 스테이터스
            result.getRemovedStatusEffects().addAll(processedResult.getRemovedStatusEffects());
            // 힐
            if (processedResult.getHealValue() != null) {
                Integer currentHealValue = result.getHealValue();
                Integer resultHealValue = currentHealValue == null ? processedResult.getHealValue() : currentHealValue + processedResult.getHealValue();
                result.setHealValue(resultHealValue);
            }
            // 데미지
            if (processedResult.getDamageValue() != null) {
                Integer currentDamageValue = result.getDamageValue();
                Integer resultDamageValue = currentDamageValue == null ? processedResult.getDamageValue() : currentDamageValue + processedResult.getDamageValue();
                result.setDamageValue(resultDamageValue);
            }
            targetActor.getStatus().syncStatus();
        }
        return result;
    }

    /**
     * 디버프 명중 처리
     *
     * @param mainActor   디버프 명중률을 사용할 메인액터
     * @param targetActor 디버프 내성을 사용할 타겟
     * @return 디버프 명중에 실패할 경우 MISS StatusEffect 반환, 명중하면 null 로 다음단계 진행
     */
    protected StatusEffect applyDebuffResistance(Actor mainActor, Actor targetActor, BaseStatusEffect baseStatusEffect) {
        // 1. 필중 : 약화 효과 내성과 마운트 효과를 모두 관통
        if (baseStatusEffect.getType() != StatusEffectType.DEBUFF || !baseStatusEffect.isResistible())
            return null;

        // 2. 마운트 : 약화 명중/내성 과 관계없이 부여한 약화효과를 반드시 무효화한다 (필중 효과 제외)
        StatusEffect mountResultStatusEffect = getEffectByModifierType(targetActor, StatusModifierType.MOUNT)
                .map(mountBattleStatus -> {
                    mountBattleStatus.expireAtTurnEnd(); // 마운트는 발동시 해당 턴 종료시 삭제됨
                    return StatusEffect.getTransientStatusEffect(baseStatusEffect.getType(), "RESIST", targetActor);
                }).orElse(null);
        if (mountResultStatusEffect != null)
            return mountResultStatusEffect;

        // 3. 약화 명중 / 내성
        double debuffSuccessRate = mainActor.getStatus().getDebuffSuccessRate(); // 성공률 0 ~ 1.0
        double debuffResistRate = targetActor.getStatus().getDebuffResistRate(); // 내성 0 ~ 2.0
        double finalAccuracy = Math.clamp(1.0 + debuffSuccessRate - debuffResistRate, 0, 1);
        // 성공률과 내성이 같은값이면 100% 부여
        // 성공률 관련 효과 없는 상태에서 내성이 100% 상승시 무효화
        // 내성이 200% 상승시 성공률에 관계없이 무효화

        if (Math.random() > finalAccuracy) {
            return StatusEffect.getTransientStatusEffect(baseStatusEffect.getType(), "MISS", targetActor);
        }
        return null; // 명중시 다음 처리 진행
    }

    /**
     * 타겟이 가진 기존 StatusEffect 들과 우열 연산
     *
     * @return 기존 효과를 갱신했다면 해당 StatusEffect 반환, not null
     */
    protected StatusEffect processSuperiority(Actor targetActor, BaseStatusEffect appliedBaseEffect) {
        StatusEffect resultEffect = null; // 실적용 여부와 상관 없이, 결과로 표시할 스테이터스 (MISS, RESIST, 갱신된 효과, 새로 적용할 효과 모두 포함)

        // 겹치는 효과 확인
        StatusEffect existingEffect = null;
        boolean isBasicStatusEffect = !appliedBaseEffect.isUniqueFrame(); // 고유항 효과
        boolean isStackable = appliedBaseEffect.getMaxLevel() > 0; // 누적식 (또는 레벨상승)
        if (isBasicStatusEffect) {
            if (isStackable) {
                // CHECK 빙결, 화상 등 레벨상승식 기본 상태효과를 위해 name 매칭. 두고보아야 할듯
                existingEffect = getSameStackableBasicEffectsByName(targetActor, appliedBaseEffect).orElse(null);
            } else {
                existingEffect = getBasicEffectByModifierTypeAndTargetType(targetActor, appliedBaseEffect.getFirstModifier().getType(), appliedBaseEffect.getTargetType()).orElse(null);
            }
        } else {
            existingEffect = getEffectByBaseId(targetActor, appliedBaseEffect.getId()).orElse(null);
        }

        // 우열 연산
        boolean isRefillable = appliedBaseEffect.getDurationType() == StatusDurationType.LEVEL_INFINITE;
        if (existingEffect != null) {
            resultEffect = isRefillable ? getSuperiorReFillable(appliedBaseEffect, existingEffect)
                    : isStackable ? getSuperiorStackable(appliedBaseEffect, existingEffect)
                    : getSuperiorNormal(appliedBaseEffect, existingEffect);
        } else {
            // 새로부여
            resultEffect = StatusEffect.fromBaseEffect(appliedBaseEffect, targetActor);
            if (isRefillable) resultEffect.addLevel(appliedBaseEffect.getMaxLevel()); // 리필식은 최고레벨 부여
        }
        return resultEffect;
    }

    protected StatusEffect getSuperiorReFillable(BaseStatusEffect appliedBaseStatusEffect, StatusEffect existStatusEffect) {
        Actor targetActor = existStatusEffect.getActor();
        if (existStatusEffect.getLevel() > appliedBaseStatusEffect.getMaxLevel()) {
            // 기존 효과의 남은 레벨이 발생한 효과의 최대 레벨보다 높음
            return StatusEffect.getTransientStatusEffect(appliedBaseStatusEffect.getType(), "NO EFFECT", targetActor);
        }
        this.removeStatusEffect(targetActor, existStatusEffect);
        StatusEffect appliedEffect = StatusEffect.fromBaseEffect(appliedBaseStatusEffect, targetActor);
        appliedEffect.addDuration(appliedEffect.getDuration() - existStatusEffect.getDuration());
        this.addStatusEffectsLevel(targetActor, appliedBaseStatusEffect.getMaxLevel(), appliedEffect); // 리필
        return appliedEffect;
    }

    protected StatusEffect getSuperiorStackable(BaseStatusEffect appliedBaseStatusEffect, StatusEffect existStatusEffect) {
        Actor targetActor = existStatusEffect.getActor();
        if (existStatusEffect.getBaseStatusEffect().getMaxLevel() > appliedBaseStatusEffect.getMaxLevel()) {
            // 기존 효과가 상한이 높음
            existStatusEffect.resetDuration(); // 효과시간 초기화
            addStatusEffectsLevel(targetActor, 1, existStatusEffect); // 레벨 상승
            return existStatusEffect; // 누적식은 NO_EFFECT 없음 (디버프 횟수 전조용으로 추정됨)
        }

        // 발생한 효과가 기존보다 상한이 높음
        this.removeStatusEffect(targetActor, existStatusEffect);
        StatusEffect appliedEffect = StatusEffect.fromBaseEffect(appliedBaseStatusEffect, targetActor);
        this.addStatusEffectsLevel(targetActor, existStatusEffect.getLevel(), appliedEffect); // 기존 레벨 이어서 가져감
        return appliedEffect;
    }

    /**
     * 리필, 누적식(레벨식) 이 아닌 효과의 효과량으로 우열연산
     */
    protected StatusEffect getSuperiorNormal(BaseStatusEffect appliedBaseStatusEffect, StatusEffect existStatusEffect) {
        Actor targetActor = existStatusEffect.getActor();
        double inputStatusEffectValue = appliedBaseStatusEffect.getFirstModifier().getInitValue();
        double currentStatusEffectValue = existStatusEffect.getBaseStatusEffect().getFirstModifier().getInitValue();
        boolean isApplied = false;
        if (inputStatusEffectValue >= currentStatusEffectValue) {
            // 입력 스테이터스 효과값이 같거나 크면 기존 삭제, 갱신준비 (효과시간이 긴쪽보다 효과량이 높은쪽을 우선)
            isApplied = true;
        } else if (appliedBaseStatusEffect.getFirstModifier().getType() == StatusModifierType.BARRIER) {
            // 베리어 효과에 한해, 현재 베리어 잔여량과 추가 비교 후 적용
            int currentBarrier = targetActor.getStatus().getBarrier();
            if (currentBarrier < (int) inputStatusEffectValue) {
                isApplied = true;
            }
        }

        if (isApplied) {
            this.removeStatusEffect(targetActor, existStatusEffect);
            return StatusEffect.fromBaseEffect(appliedBaseStatusEffect, targetActor);
        } else {
            return StatusEffect.getTransientStatusEffect(appliedBaseStatusEffect.getType(), "NO EFFECT", targetActor); // 발생효과가 덮어 씌워졌으면 NO EFFECT 반환 (DB 저장x, 표시용)
        }

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
        boolean levelAdded = false;
        for (StatusEffect statusEffect : statusEffects) {
            if (!statusEffect.isMaxLevel()) {
                statusEffect.addLevel(level);
                levelAdded = true;
            }
        }
        if (levelAdded) {
            actor.getStatus().syncStatus(); // 갱신
        }
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
        List<StatusEffectDto> levelDownedStatusEffects = new ArrayList<>(); // 레벨이 내려감
        List<StatusEffectDto> removedStatusEffects = new ArrayList<>(); // 레벨이 내려가서 삭제됨
        Map<Long, SetStatusEffectResult.Result> resultMap = new HashMap<>();

        for (StatusEffect statusEffect : statusEffects) {
            statusEffect.subtractLevel(level);

            if (statusEffect.getLevel() <= 0) {
                this.removeStatusEffect(actor, statusEffect);
                removedStatusEffects.add(StatusEffectDto.of(statusEffect));
            } else {
                levelDownedStatusEffects.add(StatusEffectDto.of(statusEffect));
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
     * StatusEffect.activeModifierCount 변경 및 적용
     */
    public void updateActiveModifierCount(int count, StatusEffect... statusEffect) {
        List<Actor> targetActors = new ArrayList<>();
        for (StatusEffect effect : statusEffect) {
            effect.updateActiveModifierCount(count);
            targetActors.add(effect.getActor());
        }

        targetActors.forEach(actor -> actor.getStatus().syncStatus());

    }

    /**
     * 배틀 스테이터스들의 효과를 모두 같은 duration 만큼 연장
     */
    public void extendStatusEffectsDuration(List<StatusEffect> statusEffects, Integer duration) {
        statusEffects.forEach(battleStatus -> this.extendStatusEffectDuration(battleStatus, duration));
    }

    /**
     * 배틀 스테이터스의 효과를 duration 만큼 연장
     */
    public void extendStatusEffectDuration(StatusEffect statusEffect, Integer duration) {
        if (statusEffect.getDuration() > 0)
            statusEffect.addDuration(duration); // 남은 시간이 1턴 이상이여야 연장가능 (0턴은 의사적으로 감소시킨 수치)
    }

    /**
     * 배틀 스테이터스들의 효과를 모두 같은 duration 만큼 단축
     */
    public List<StatusEffect> shortenStatusEffectsDuration(List<StatusEffect> statusEffects, Integer duration) {
        statusEffects.forEach(battleStatus -> this.shortenStatusEffectDuration(battleStatus, duration));
        return statusEffects;
    }

    /**
     * 배틀 스테이터스의 효과를 duration 만큼 단축, duration 이 0 이되면 삭제
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
     * @return 제거된 효과
     */
    public StatusEffect removeStatusEffect(Actor actor, StatusEffect statusEffect) {
        if (statusEffect == null) return null;

        // 베리어 초기화
        if (statusEffect.getBaseStatusEffect().getFirstModifier().getType() == StatusModifierType.BARRIER) {
            actor.getStatus().clearBarrier();
        }

        statusEffectRepository.delete(statusEffect);
        actor.getStatusEffects().remove(statusEffect);
        statusEffect.setActor(null);
        actor.getStatus().syncStatus();

        return statusEffect;
    }

    /**
     * 상태효과 삭제하고 사용자에게 노출하기 위한 결과까지 반환
     *
     * @return setStatusEffectResult
     */
    public SetStatusEffectResult removeStatusEffectsWithResult(Actor actor, StatusEffect... statusEffects) {
        return removeStatusEffectsWithResult(actor, Arrays.asList(statusEffects));
    }

    /**
     * 상태효과 삭제하고 사용자에게 노출하기 위한 결과까지 반환
     *
     * @return setStatusEffectResult
     */
    public SetStatusEffectResult removeStatusEffectsWithResult(Actor actor, List<StatusEffect> statusEffects) {
        List<StatusEffectDto> removedStatusEffects = new ArrayList<>(); // 삭제됨
        Map<Long, SetStatusEffectResult.Result> resultMap = new HashMap<>();

        for (StatusEffect statusEffect : statusEffects) {
            actor.getStatusEffects().remove(statusEffect);
            statusEffectRepository.delete(statusEffect);
            removedStatusEffects.add(StatusEffectDto.of(statusEffect));
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
     */
    public void removeExpiredTimeBasedStatusEffects(Actor enemy, List<Actor> partyMembers) {
        List<StatusEffect> expiredAllTargetEnemyStatusEffects = getEffectsByTargetType(enemy, StatusEffectTargetType.ALL_ENEMIES).stream()
                .filter(StatusEffect::isExpired).toList();
        this.removeStatusEffects(enemy, expiredAllTargetEnemyStatusEffects);
    }


}
