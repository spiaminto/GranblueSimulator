package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.Room;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.statuseffect.*;
import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.logic.actor.dto.StatusEffectDto;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.StatusEffectRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.*;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SetStatusLogic {

    private final StatusEffectRepository statusEffectRepository;
    private final ProcessStatusLogic processStatusLogic;

    /**
     * 단순히 battleActor 에 status 를 붙여줌. <br>
     * <b>사용 주의!, 동기화시에만 사용중</b>
     */
    public void addSyncedStatusEffect(Actor targetActor, StatusEffect statusEffect) {
        // 새로 추가되는 스테이터스
        StatusEffect addedStatusEffect = StatusEffect.builder()
                .actor(targetActor)
                .duration(statusEffect.getDuration())
                .baseStatusEffect(statusEffect.getBaseStatusEffect())
                .level(statusEffect.getLevel()) // 레벨 유지
                .iconSrc(statusEffect.getIconSrc())
                .build()
                .mapBattleActor(targetActor);
        statusEffectRepository.save(addedStatusEffect);
    }

    /**
     * Move 를 받아 해당 Move 의 Status 리스트를 타겟에 맞춰 BattleStatus 로 set
     * Move 에 '랜덤효과 N개 부여' 인 경우 적용하여 set
     *
     * @param mainActor    move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers mainActor 를 포함한 전체 파티원
     * @param move
     */
    public SetStatusResult setStatusEffect(Actor mainActor, Actor enemy, List<Actor> partyMembers, Move move) {
        List<BaseStatusEffect> baseStatusEffects = move.getStatusEffects();
        if (move.getRandomStatusCount() > 0) {
            // 랜덤효과 N 개 부여
            Collections.shuffle(baseStatusEffects);
            baseStatusEffects = baseStatusEffects.subList(0, move.getRandomStatusCount());
        }
        return this.applyStatusEffect(mainActor, enemy, partyMembers, baseStatusEffects, null);
    }

    /**
     * Status 리스트를 받아 타겟에 맞춰 BattleStatus 로 set
     *
     * @param mainActor         move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers      mainActor 를 포함한 전체 파티원
     * @param baseStatusEffects Move.statuses 또는 임의의 Status
     */
    public SetStatusResult setStatusEffect(Actor mainActor, Actor enemy, List<Actor> partyMembers, List<BaseStatusEffect> baseStatusEffects) {
        return this.applyStatusEffect(mainActor, enemy, partyMembers, baseStatusEffects, null);
    }

    /**
     * Status 리스트를 받아 타겟에 맞춰 BattleStatus 로 set 하며, StatusTargetType modifyingTargetType 을 추가로 받아 Status.targetType 대신 사용
     * 주로 SELF 스테이터스의 효과를 PARTY_MEMBERS 로 전체화 할때 사용한다.
     *
     * @param mainActor           move 사용자 (enemy 도 가능)
     * @param enemy
     * @param partyMembers        mainActor 를 포함한 전체 파티원
     * @param baseStatusEffects   Move.statuses 또는 임의의 Status
     * @param modifyingTargetType 기존 Status.targetType 대신 사용할 타겟 타입
     */
    public SetStatusResult setStatusEffect(Actor mainActor, Actor enemy, List<Actor> partyMembers, List<BaseStatusEffect> baseStatusEffects, StatusEffectTargetType modifyingTargetType) {
        return this.applyStatusEffect(mainActor, enemy, partyMembers, baseStatusEffects, modifyingTargetType);
    }

    /**
     * 스테이터스를 적용하는 메인로직
     * 여기서 타겟을 정한 뒤 타겟별로 StatusEffect 적용
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param baseStatusEffects
     * @param modifiedTargetType
     * @return SetStatusResult 스테이터스 처리 결과 DTO
     */
    protected SetStatusResult applyStatusEffect(Actor mainActor, Actor enemy, List<Actor> partyMembers, List<BaseStatusEffect> baseStatusEffects, StatusEffectTargetType modifiedTargetType) {
        List<List<StatusEffectDto>> addedBattleStatusesList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<StatusEffectDto>()).collect(Collectors.toList());
        List<List<StatusEffectDto>> removedBattleStatusesList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<StatusEffectDto>()).collect(Collectors.toList());
        List<Integer> healValues = new ArrayList<>(Collections.nCopies(5, null));
        List<Integer> damageValues = new ArrayList<>(Collections.nCopies(5, null));
        // 처리 순서에 맞게 정렬
        List<BaseStatusEffect> sortedBaseStatusEffects = baseStatusEffects.stream().sorted(Comparator.comparing(status -> status.getType().getProcessOrder())).toList();

        sortedBaseStatusEffects.forEach(appliedBaseStatusEffect -> {
            // 1. 타겟 타입 변경 여부 처리
            StatusEffectTargetType statusEffectTargetType = modifiedTargetType != null ? modifiedTargetType : appliedBaseStatusEffect.getTargetType();

            // 2. 타겟 확인
            List<Actor> targetActors = this.getTargets(mainActor, enemy, partyMembers, statusEffectTargetType);
            // 2.1 참전자 전체가 타겟인 경우 등록
            if (statusEffectTargetType == StatusEffectTargetType.ALL_PARTY_MEMBERS) {
                this.registerForAllStatus(mainActor, appliedBaseStatusEffect);
            }

            // 3. 타겟 액터 순회
            targetActors.forEach(targetActor -> {

                // 3.1 즉시 효과가 적용되는 스테이터스 처리 (디스펠, 힐, 오의게이지 상승 등)
                ImmediateProcessedStatusEffectResult immediateResult = processImmediateStatusEffect(targetActor, appliedBaseStatusEffect);
                if (immediateResult != null) {
                    // 추가 / 갱신된 스테이터스 (DB 저장 x)
                    if (immediateResult.getAddedStatusEffect() != null)
                        addedBattleStatusesList.get(targetActor.getCurrentOrder()).addAll(immediateResult.getAddedStatusEffect().stream().map(StatusEffectDto::of).toList());
                    // 지워진(디스펠, 클리어) 스테이터스
                    removedBattleStatusesList.get(targetActor.getCurrentOrder()).addAll(immediateResult.getRemovedStatusEffects().stream().map(StatusEffectDto::of).toList());
                    // 힐
                    if (immediateResult.getHealValue() != null) {
                        Integer currentHealValue = healValues.get(targetActor.getCurrentOrder());
                        Integer resultHealValue = currentHealValue == null ? immediateResult.getHealValue() : currentHealValue + immediateResult.getHealValue();
                        healValues.set(targetActor.getCurrentOrder(), resultHealValue);
                    }
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
                // 3.4 결과 리스트에 넣음
                addedBattleStatusesList.get(targetActor.getCurrentOrder()).add(StatusEffectDto.of(displayStatusEffect));
            });
        });

        // 스텟 재계산
        partyMembers.forEach(partyMember -> partyMember.getStatus().syncStatus());
        enemy.getStatus().syncStatus();

        // 로깅
//        partyMemberAddedBattleStatus.forEach(addedStatuses -> log.info("partyMemberIndex = {}, addedStatuses = {}", partyMemberAddedBattleStatus.indexOf(addedStatuses), addedStatuses));

        return SetStatusResult.builder()
                .addedStatusesList(addedBattleStatusesList)
                .removedStatuesList(removedBattleStatusesList)
                .healValues(healValues)
                .build();
    }

    protected List<Actor> getTargets(Actor mainActor, Actor enemy, List<Actor> partyMembers, StatusEffectTargetType targetType) {
        List<Actor> resultTargets = new ArrayList<>();
        switch (targetType) {
            case SELF -> resultTargets.add(mainActor);
            case SELF_AND_NEXT_CHARACTER -> {
                resultTargets.add(mainActor);
                partyMembers.stream().filter(battleActor -> battleActor.getCurrentOrder() == mainActor.getCurrentOrder() + 1).findFirst().ifPresent(resultTargets::add);
            }
            case SELF_AND_MAIN_CHARACTER -> {
                resultTargets.add(mainActor);
                partyMembers.stream().filter(battleActor -> battleActor.getBaseActor().isLeaderCharacter()).findFirst().ifPresent(resultTargets::add);
            }
            case MAIN_CHARACTER ->
                    partyMembers.stream().filter(battleActor -> battleActor.getBaseActor().isLeaderCharacter()).findFirst().ifPresent(resultTargets::add);
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
     * @return ImmediateProcessedStatusEffectResult, 없을시 null 로 다음단계 진행
     */
    protected ImmediateProcessedStatusEffectResult processImmediateStatusEffect(Actor targetActor, BaseStatusEffect appliedBaseStatusEffect) {
        if (appliedBaseStatusEffect.getDuration() > 0) return null;
        ProcessStatusLogic.ProcessStatusLogicResult processStatusLogicResult = processStatusLogic.process(targetActor, appliedBaseStatusEffect);
        if (processStatusLogicResult == null) return null;
        else {
            return ImmediateProcessedStatusEffectResult.builder()
                    .addedStatusEffect(processStatusLogicResult.getAddedStatusEffects())
                    .removedStatusEffects(processStatusLogicResult.getRemovedStatusEffects())
                    .healValue(processStatusLogicResult.getHealValue())
                    .build();
        }
    }

    @Builder
    @Data
    static class ImmediateProcessedStatusEffectResult {
        @Builder.Default
        private List<StatusEffect> addedStatusEffect = new ArrayList<>();
        @Builder.Default
        private List<StatusEffect> removedStatusEffects = new ArrayList<>(); // dispeled, cleared
        private Integer healValue;
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
        boolean isBasicStatusEffect = appliedModifiers.size() == 1; // 기본 상태효과: modifier 1개로 구성, 2개 이상인경우 고유 상태효과
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
                        .mapBattleActor(targetActor);
                this.addStatusEffectsLevel(targetActor, 1, addedStatusEffect); // 1 증가
                statusEffectRepository.save(addedStatusEffect);
                return addedStatusEffect;
            } else {
                // 1.2 기존 스테이터스 효과가 상한이 높음
                existStatusEffect.resetDuration(); // 효과시간 초기화
                addStatusEffectLevel(existStatusEffect.getActor(), 1, existStatusEffect); // 레벨 상승
                // 누적식은 원본 설계상 NO EFFECT 가 없고, 반드시 효과시간 초기화 및 효과 적용 (상한에 막힐시 실제론 미적용) 한다.
                return existStatusEffect;
            }
        } else {
            // 2. 레벨제 스테이터스가 아님
            double inputStatusEffectValue = appliedBaseStatusEffect.getFirstModifier().getValue();
            double currentStatusEffectValue = existStatusEffect.getBaseStatusEffect().getFirstModifier().getValue();
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
                .mapBattleActor(targetActor);
        statusEffectRepository.save(addedStatusEffect);
        return addedStatusEffect;
    }

    /**
     * battleActor 의 battleStatus 중 statusIds 에 해당하는 모든 스테이터스의 레벨을 level 만큼 증가
     * 사용자에게 결과 반환 없이 임의로 레벨 조작시 사용
     *
     * @param actor         증가시킬 대상
     * @param level         증가량 (목표 값이 아님)
     * @param statusEffects 증가시킬 battleStatus[]
     */
    public void addStatusEffectsLevel(Actor actor, int level, StatusEffect... statusEffects) {
        for (StatusEffect statusEffect : statusEffects)
            this.addStatusEffectLevel(actor, level, statusEffect);
    }

    /**
     * battleActor 의 battleStatus 중 statusId 에 해당하는 스테이터스의 레벨을 level 만큼 증가
     * 사용자에게 결과 반환 없이 임의로 레벨 조작시 사용
     *
     * @param actor
     * @param level        증가량 (목표 값이 아님)
     * @param statusEffect 증가할 battleStatus
     */
    public void addStatusEffectLevel(Actor actor, int level, StatusEffect statusEffect) {
        if (statusEffect == null) {
            log.warn("[addBattleStatusLevel] null BattleStatus, actor = {}", actor);
            return;
        }
        statusEffect.addLevel(level);

        actor.getStatus().syncStatus(); // 갱신
    }

    /**
     * battleActor 의 battleStatus 중 statusId 에 해당하는 스테이터스의 레벨을 level 만큼 감소
     * 레벨 감소의 경우 사용자에게 감소정보를 노출해야하기 때문에 SetStatusResult 를 반환
     *
     * @param actor
     * @param level        감소량 (목표 값이 아님)
     * @param statusEffect 레벨이 감소할 battleStatus
     * @return setStatusResult
     */
    public SetStatusResult subtractStatusEffectLevel(Actor actor, int level, StatusEffect statusEffect) {
        if (statusEffect == null) {
            log.warn("[subtractBattleStatusLevel] null BattleStatus, actor = {}", actor);
            return null;
        }
        List<List<StatusEffectDto>> addedBattleStatuses = IntStream.range(0, 5).mapToObj(i -> new ArrayList<StatusEffectDto>()).collect(Collectors.toList());
        List<List<StatusEffectDto>> removedBattleStatuses = IntStream.range(0, 5).mapToObj(i -> new ArrayList<StatusEffectDto>()).collect(Collectors.toList());

        statusEffect.subtractLevel(level);

        if (statusEffect.getLevel() <= 0) {
            statusEffectRepository.delete(statusEffect);
            actor.getStatusEffects().remove(statusEffect);
            removedBattleStatuses.get(actor.getCurrentOrder()).add(StatusEffectDto.of(statusEffect)); // 삭제되면 이쪽
        } else
            addedBattleStatuses.get(actor.getCurrentOrder()).add(StatusEffectDto.of(statusEffect)); // 레벨 내려가면 추가와 동일하게 표시

        actor.getStatus().syncStatus();

        return SetStatusResult.builder()
                .addedStatusesList(addedBattleStatuses)
                .removedStatuesList(removedBattleStatuses)
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
        }
        return statusEffect;
    }

    /**
     * 배틀 스테이터스를 삭제. 참전자 전체 적용 스테이터스 삭제하는 메서드로 수정예정
     * 일반 제거가 아닌 로직으로 인한 제거에 사용. 소거불가도 이걸로 제거.
     *
     * @param actor
     * @param statusEffects
     */
    public List<StatusEffect> removeStatusEffects(Actor actor, List<StatusEffect> statusEffects) {
        actor.getStatusEffects().removeAll(statusEffects);
        statusEffectRepository.deleteAll(statusEffects);
        return statusEffects;
    }

    /**
     * 배틀 스테이터스를 삭제. 참전자 전체 적용 스테이터스 삭제하는 메서드로 수정예정
     * 일반 제거가 아닌 로직으로 인한 제거에 사용. 소거불가도 이걸로 제거.
     *
     * @param actor
     * @param statusEffect
     */
    public StatusEffect removeStatusEffect(Actor actor, StatusEffect statusEffect) {
        if (statusEffect == null) return null;
        actor.getStatusEffects().remove(statusEffect);
        statusEffectRepository.delete(statusEffect);
        return statusEffect;
    }

    /**
     * 스테이터스 효과의 시간을 진행시킴 <br>
     * 남은 시간을 확인하고 남은 시간이 0 인 스테이터스 효과를 삭제 <br>
     * 현재 적만 시간제 스테이터스 효과가 구현되어있으므로 적만 기능 구현
     *
     * @param enemy
     * @param partyMembers
     */
    public void progressTimeBasedStatusEffects(Actor enemy, List<Actor> partyMembers) {
        List<StatusEffect> expiredAllTargetEnemyStatusEffects = getEffectsByTargetType(enemy, StatusEffectTargetType.ALL_ENEMIES).stream()
                .filter(StatusEffect::isExpired).toList();
        removeStatusEffects(enemy, expiredAllTargetEnemyStatusEffects);
    }

    /**
     * 배틀 스테이터스의 턴을 진행시킴. 남은시간이 감소하고 남은시간이 0턴인 배틀스테이터스는 삭제됨
     * 캐릭터의 턴종효과 보다 나중에 발동해야함 (강화효과 연장 등 적용 후 발동해야 함)
     *
     * @param enemy
     * @param partyMembers
     */
    public void progressStatusEffects(Actor enemy, List<Actor> partyMembers) {
        List<Actor> allActors = new ArrayList<>();
        allActors.add(enemy);
        allActors.addAll(partyMembers);

        // BattleStatus 남은시간 1턴 감소
        allActors.stream()
                .map(Actor::getStatusEffects)
                .flatMap(Collection::stream)
                .forEach(statusEffect -> {
                    if (statusEffect.getBaseStatusEffect().getDurationType().isTurnBased())
                        statusEffect.subtractDuration(1);
                });

        // 남은시간 0턴인 BattleStatus
        List<List<StatusEffect>> expiredBattleStatusesList = allActors.stream()
                .map(battleActor -> battleActor.getStatusEffects()
                        .stream().filter(battleStatus -> battleStatus.getDuration() == 0).toList())
                .toList();
        expiredBattleStatusesList.forEach(list -> list.forEach(battleStatus -> log.info("[progressBattleStatus] listIndex = {} statusName = {}, expiredBattleStatus = {}", expiredBattleStatusesList.indexOf(list), battleStatus.getBaseStatusEffect().getName(), battleStatus)));

        // 남은 시간 0턴인 BAttleStatus 삭제
        expiredBattleStatusesList.forEach(battleStatuses ->

        {
            if (!battleStatuses.isEmpty()) {
                battleStatuses.getFirst().getActor().getStatusEffects().removeAll(battleStatuses);
                statusEffectRepository.deleteAll(battleStatuses);
            }
        });

        // 스테이터스 갱신
        allActors.forEach(actor -> actor.getStatus().

                syncStatus());
    }

    /**
     * 참전자 효과 적용 <br>
     * <b>StatusTargetType.ALL_PLAYERS</b> 만 적용됨 <br>
     * 참전자전체 버프, 클리어올 에서 사용 상정
     *
     * @param mainActor        : 스테이터스 발생 액터
     * @param baseStatusEffect : 적용할 스테이터스
     */
    public void registerForAllStatus(Actor mainActor, BaseStatusEffect baseStatusEffect) {
        // StatusTargetType.All_Players 만을 적용대상으로 함
        if (baseStatusEffect.getTargetType() != StatusEffectTargetType.ALL_PARTY_MEMBERS)
            throw new IllegalArgumentException("참전자 버프, 참전자 클리어올 외에 다른 타입 " + baseStatusEffect.getType());

        Member refMember = mainActor.getMember();
        Room room = refMember.getRoom();
        List<Member> targetMembers = room.getMembers().stream()
                .filter(member -> !refMember.getId().equals(member.getId()))
                .toList();
        if (targetMembers.isEmpty()) return; // 동기화 대상 없음

        // 결과 반환을 위한 처리 -> 이펙트 결과를 반환하고 싶기 때문에 처리를 반환시점으로 지연시킴
        targetMembers.forEach(member -> {
            member.addForAllStatusId(baseStatusEffect.getId());
            // 저장해놓고, 플레이어 각각 필요한경우 사용 및 초기화
            // 사용시 mainActor 를 해당 파티의 leader 로 설정해서 임시로 테스트하고, 문제없으면 그대로 사용하자

        });
    }


}
