package com.gbf.granblue_simulator.battle.logic.statuseffect;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.ReactionLogic;
import com.gbf.granblue_simulator.battle.logic.move.mapper.CharacterLogicResultMapper;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.move.mapper.EnemyLogicResultMapper;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusDurationType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.metadata.repository.StatusEffectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class TurnEndStatusLogic {

    private final BattleContext battleContext; // 특히, 아군의 턴데미지 적의 턴데미지 때 mainActor 설정이 필요하여 사용

    private final ProcessStatusLogic processStatusLogic;
    private final SetStatusLogic setStatusLogic;
    private final ReactionLogic reactionLogic;

    private final CharacterLogicResultMapper characterLogicResultMapper;
    private final EnemyLogicResultMapper enemyLogicResultMapper;

    /**
     * 턴 종료 시 상태효과 처리
     * 되도록 최후에 처리
     *
     * @param turnEndProcessStartTime 턴 종료 처리 시작시간 (턴 종료시 생성/갱신된 상태효과의 경우 duration 진행 안함)
     * @return
     */
    public MoveLogicResult progressStatusEffect(LocalDateTime turnEndProcessStartTime) {
        List<Actor> allActors = battleContext.getCurrentFieldActors();

        SetStatusEffectResult statusEffectResult = SetStatusEffectResult.emptyResult();
        for (Actor actor : allActors) {
            List<StatusEffect> expiredStatusEffects = new ArrayList<>();
            for (StatusEffect statusEffect : actor.getStatusEffects()) {
                // 상태효과의 남은 효과시간을 1턴 감소
                if (statusEffect.getBaseStatusEffect().getDurationType().isTurnBased() // 턴제
                        && !(statusEffect.getBaseStatusEffect().getDurationType() == StatusDurationType.TURN_INFINITE) // 영속아님
                        && statusEffect.getUpdatedAt().isBefore(turnEndProcessStartTime)) { // 턴종료 처리 이전 갱신된 상태효과
                    statusEffect.subtractDuration(1);
                }
                // 효과시간이 0턴이면 제거 목록에 삽입 (마운트 등 무한지속 + 효과 소모후 expire 하는경우가 있어 턴 감소와 분리)
                if (statusEffect.getDuration() <= 0) {
                    expiredStatusEffects.add(statusEffect);
                }
            }

            // 효과시간 0턴 이하가 된 효과 제거 및 결과 병합
            SetStatusEffectResult removedResult = setStatusLogic.removeStatusEffectsWithResult(actor, expiredStatusEffects);
            statusEffectResult.merge(removedResult);

            // 스텟 재계산
            actor.getStatus().syncStatus();
        }

        return enemyLogicResultMapper.toResult(ResultMapperRequest.of(Move.getTransientMove(battleContext.getEnemy(), MoveType.TURN_FINISH), statusEffectResult));
    }

    /**
     * 턴 종료시 스테이터스 효과 및 그에따른 반응 처리
     *
     * @param enemy
     * @param partyMembers
     * @return MoveType.NONE 결과를 리턴하지 않음
     */
    public List<MoveLogicResult> processTurnEnd(Actor enemy, List<Actor> partyMembers) {
        // 아군 전원 사망시 처리 스킵
        if (partyMembers.isEmpty()) return List.of();

        List<MoveLogicResult> results = new ArrayList<>();
        Actor partyMainActor = partyMembers.getFirst();

        // 아군 턴종 힐 처리
        battleContext.setCurrentMainActor(partyMainActor);
        results.addAll(process(partyMembers, StatusModifierType.ACT_HEAL, MoveType.TURN_END_HEAL));
        results.addAll(process(partyMembers, StatusModifierType.ACT_RATE_HEAL, MoveType.TURN_END_HEAL));

        // 적 턴종 힐 처리
        battleContext.setCurrentMainActor(enemy);
        results.addAll(process(List.of(enemy), StatusModifierType.ACT_HEAL, MoveType.TURN_END_HEAL));
        results.addAll(process(List.of(enemy), StatusModifierType.ACT_RATE_HEAL, MoveType.TURN_END_HEAL));

        // 아군 오의 게이지 상승
        battleContext.setCurrentMainActor(partyMainActor);
        results.addAll(process(partyMembers, StatusModifierType.ACT_CHARGE_GAUGE_UP, MoveType.TURN_END_CHARGE_GAUGE));

        // 아군 오의게이지 감소
        battleContext.setCurrentMainActor(partyMainActor);
        results.addAll(process(List.of(enemy), StatusModifierType.ACT_CHARGE_GAUGE_DOWN, MoveType.TURN_END_CHARGE_GAUGE));

        // 적 오의게이지 상승 은 고양효과로 갈음
        // 적 오의게이지 턴 종료시 하락은 미구현

        // 아군에 대한 턴종 데미지 처리
        battleContext.setCurrentMainActor(enemy);
        results.addAll(process(partyMembers, StatusModifierType.ACT_DAMAGE, MoveType.TURN_END_DAMAGE));
        results.addAll(process(partyMembers, StatusModifierType.ACT_RATE_DAMAGE, MoveType.TURN_END_DAMAGE));

        // 적에 대한 턴종 데미지 처리
        battleContext.setCurrentMainActor(partyMainActor);
        results.addAll(process(List.of(enemy), StatusModifierType.ACT_DAMAGE, MoveType.TURN_END_DAMAGE));
        results.addAll(process(partyMembers, StatusModifierType.ACT_RATE_DAMAGE, MoveType.TURN_END_DAMAGE));

        return results;
    }

    /**
     * 턴 종료시 처리할 modifier 당 해당하는 결과를 만들어 반환
     *
     * @param targetActors      타겟, 적의경우 List.of(enemy) 사용
     * @param modifierType      처리할 modifierType, ACT_XXX 계열의 후처리 필요 modifier 만 가능
     * @param transientMoveType ActorLogic.moveType, TransientMove 만 가능
     * @return
     */
    protected List<MoveLogicResult> process(List<Actor> targetActors, StatusModifierType modifierType, MoveType transientMoveType) {
        List<MoveLogicResult> results = new ArrayList<>(); // 같은 상태효과 별로 묶임 + 각각에 대한 반응까지 순서대로
        Move move = Move.getTransientMove(battleContext.getMainActor(), transientMoveType); // 결과용 transient move
        Map<BaseStatusEffect, SetStatusEffectResult> statusEffectResultMap = new HashMap<>(); // 하나의 상태효과와 그에대한 효과 부여 결과 맵

        Map<StatusEffect, Actor> statusTargetMap = getStatusMapByModifier(targetActors, modifierType); // modifierType 을 가진 모든 StatusEffect 를 key 로 하는 타겟맵 (StatusEffect 기준이므로 Actor 는 1:1 대응)
        if (statusTargetMap.isEmpty()) return results;

        List<Map.Entry<StatusEffect, Actor>> orderedStatusTargetEntries = statusTargetMap.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getCreatedAt()))
                .toList(); // 같은 효과 끼리 순서대로 처리하기 위해 정렬 (부여 시간 우선, 필요시 baseEffect.id 정렬 한번 더)

        for (int i = 0; i < orderedStatusTargetEntries.size(); i++) {
            Map.Entry<StatusEffect, Actor> currentEntry = orderedStatusTargetEntries.get(i); // 하나의 효과 + 효과타겟 엔트리
            StatusEffect statusEffect = currentEntry.getKey();
            Actor target = currentEntry.getValue();

            // 상태효과에 따른 처리 수행
            ProcessStatusLogic.ProcessStatusLogicResult processResult = processStatusLogic.process(target, target, statusEffect, modifierType);

            // Actor 마다 Actor 에 부여된 StatusEffect 에 따른 각각의 결과를 전부 만듦 (같은 효과라도 레벨 등이 달라 효과량이 다를수 있음)
            Map<Long, SetStatusEffectResult.Result> currentResult = new HashMap<>();
            currentResult.put(target.getId(), SetStatusEffectResult.Result.builder()
                    .actorId(target.getId())
                    .addedStatusEffects(processResult.getAddedStatusEffects())
                    .removedStatusEffects(processResult.getRemovedStatusEffects())
                    .healValue(processResult.getHealValue())
                    .damageValue(processResult.getDamageValue())
                    .build());

            // 현재까지의 결과에 merge
            statusEffectResultMap.merge(
                    statusEffect.getBaseStatusEffect(),
                    SetStatusEffectResult.builder().results(currentResult).build()
                    , (existing, current) -> {
                        existing.merge(current);
                        return existing;
                    });

            // 다음 baseEffect 가 없거나, 기존 처리하던 baseEffect 와 다를 경우 -> 현재까지의 결과를 ActorLogicResult 로 변환 및 반응처리
            BaseStatusEffect nextBaseEffect = i < orderedStatusTargetEntries.size() - 1
                    ? orderedStatusTargetEntries.get(i + 1).getKey().getBaseStatusEffect()
                    : null;
            if (nextBaseEffect == null || !nextBaseEffect.equals(statusEffect.getBaseStatusEffect())) {
                MoveLogicResult result = characterLogicResultMapper.toResult(ResultMapperRequest.of(move, null, statusEffectResultMap.get(statusEffect.getBaseStatusEffect())));
                List<MoveLogicResult> reactionResults = reactionLogic.processReaction(result);
                results.addAll(reactionResults);
            }
        }

        return results; // 같은 modifierType 계열에서는 정렬하지 않음
    }


    /**
     * 파라미터로 받은 modifier 를 포함하는 StatusEffect 를 가진 Actor를 StatusEffect 를 key 로 하여 반환<br>
     * 스테이터스와 부여된 Actor 쌍으로 처리를 위해 사용 (StatusEffect 역시 엔티티이므로 Actor 와 1:1 대응)
     *
     * @param targets
     * @param modifierType
     * @return StatusEffect 를 key 로하는 맵. StatusEffect.BaseStatusEffect 가 같아도 별도로 취급되어 반환됨
     */
    protected Map<StatusEffect, Actor> getStatusMapByModifier(List<Actor> targets, StatusModifierType modifierType) {
        Map<StatusEffect, Actor> resultMap = new LinkedHashMap<>();

        for (Actor target : targets) {
            List<StatusEffect> effects = getEffectsByModifierType(target, modifierType);
            if (effects.isEmpty()) continue;
            for (StatusEffect effect : effects) {
                resultMap.put(effect, target);
            }
        }

        resultMap.forEach((key, value) -> {
            log.info("[getStatusMapByModifier] key = {}, value = {}", key.getBaseStatusEffect().getName(), value.getName());
        });
        return resultMap;
    }


    /*
    (참고) 원본 처리순서

    피데미지시 효과
    스택소모 버프/디버프, 웨폰버스트, 피타겟 효과
    클리어
    아군 재생
    적 재생
    아군 오의게이지 상승
    아군 오의게이지 감소
    아군 턴데미지
    적 턴데미지

    출처 https://x.com/zekasyuz/status/799531711285592064
     */

}
