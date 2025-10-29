package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.MotionType;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.BattleStatusDto;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Slf4j
public class CharacterLogicResultMapper {

    /**
     * 캐릭터 로직에서 아무것도 발생하지 않았을때 리턴 (null X)
     *
     * @return
     */
    public ActorLogicResult emptyResult() {
        return ActorLogicResult.builder().moveType(MoveType.NONE).motionType(MotionType.NONE).build();
    }

    /**
     * 데미지만 발생하는 기본공격 결과맵핑
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param move
     * @return
     */
    public ActorLogicResult attackToResult(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult) {
        return map(mainActor, enemy, partyMembers, move, damageLogicResult, null, false, null);
    }

    /**
     * 오의 결과 맵핑
     * 오의는 재발동 여부를 체크함
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param move
     * @param damageLogicResult
     * @param statusResult
     * @param executeChargeAttack 오의 재발동 여부
     * @return
     */
    public ActorLogicResult chargeAttackToResult(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, SetStatusResult statusResult, boolean executeChargeAttack) {
        return map(mainActor, enemy, partyMembers, move, damageLogicResult, statusResult, executeChargeAttack, null);
    }

    /**
     * 데미지와 스테이터스 모두 발생하는 일반 결과 맵핑
     * 어빌리티, 차지어택, 서포트 어빌리티 <br>
     * 데미지 또는 스테이터스 각 안발생했을 경우 null 입력
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param move
     * @param statusResult
     * @return
     */
    public ActorLogicResult toResult(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, SetStatusResult statusResult) {
        return map(mainActor, enemy, partyMembers, move, damageLogicResult, statusResult, false, null);
    }

    /**
     * 턴 진행 없이 공격 포함하는 결과
     *
     * @param mainActor
     * @param enemy
     * @param partyMembers
     * @param move
     * @param damageLogicResult
     * @param statusResult
     * @return
     */
    public ActorLogicResult toResultWithExecuteAttack(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, SetStatusResult statusResult, StatusTargetType executeAttackTargetType) {
        return map(mainActor, enemy, partyMembers, move, damageLogicResult, statusResult, false, executeAttackTargetType);
    }

    protected ActorLogicResult map(BattleActor mainActor, BattleActor enemy, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, SetStatusResult setStatusResult, boolean executeChargeAttack, StatusTargetType executeAttackTargetType) {
        if (setStatusResult == null) setStatusResult = SetStatusResult.emptyResult(); // 스테이터스 효과가 발생하지 않은 경우 빈객체
        if (damageLogicResult == null)
            damageLogicResult = DamageLogicResult.builder().build(); // 데미지가 발생하지 않은경우 빈 객체 생성
        int hitCount = damageLogicResult.getDamages().stream().filter(damage -> damage > 0).toList().size();
        int totalHitCount = hitCount + damageLogicResult.getAdditionalDamages().stream()
                .map(additionalDamages -> additionalDamages.stream()
                        .filter(damage -> damage > 0)
                        .toList())
                .mapToInt(List::size)
                .sum();

        // 체력
        List<Integer> hps = IntStream.range(0, 5).mapToObj(i -> 0).collect(Collectors.toList());
        List<Integer> hpRates = IntStream.range(0, 5).mapToObj(i -> 0).collect(Collectors.toList());
        hps.set(0, enemy.getHp());
        hpRates.set(0, enemy.getHpRate());
        partyMembers.forEach(battleActor -> {
            hps.set(battleActor.getCurrentOrder(), battleActor.getHp());
            hpRates.set(battleActor.getCurrentOrder(), battleActor.getHpRate());
        });

        // 오의게이지
        List<Integer> chargeGauges = IntStream.range(0, 5).mapToObj(i -> 0).collect(Collectors.toList());
        chargeGauges.set(0, enemy.getChargeGauge());
        partyMembers.forEach(battleActor -> chargeGauges.set(battleActor.getCurrentOrder(), battleActor.getChargeGauge()));

        // 페이탈 체인 게이지
        int fatalChainGauge = partyMembers.stream()
                .filter(battleActor -> battleActor.getActor().isLeaderCharacter()).findFirst()
                .map(BattleActor::getFatalChainGauge).orElseGet(() -> 0);

        // 추가된 스테이터스
        List<List<BattleStatusDto>> addedStatusList = setStatusResult.getAddedStatusesList();
        // 삭제된 스테이터스
        List<List<BattleStatusDto>> removedStatusList = setStatusResult.getRemovedStatuesList();
        // 결과 스테이터스
        List<List<BattleStatus>> currentBattleStatusesList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList());
        currentBattleStatusesList.set(0, enemy.getBattleStatuses());
        partyMembers.forEach(partyMember -> currentBattleStatusesList.set(partyMember.getCurrentOrder(), partyMember.getBattleStatuses()));
        List<List<BattleStatusDto>> currentBattleStatusesDtoList = currentBattleStatusesList.stream()
                .map(currentBattleStatuses -> currentBattleStatuses.stream()
                        .map(BattleStatusDto::of)
                        .sorted(Comparator.comparing(BattleStatusDto::getCreatedAt))
                        .toList())
                .toList();

        // 힐
        List<Integer> healValues = new ArrayList<>(setStatusResult.getHealValues());

        log.info("[map] addedStatusList = {}", addedStatusList);
        log.info("[map] removedStatusList = {}", removedStatusList);

        // 쿨다운
        List<List<Integer>> cooldownList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<Integer>()).collect(Collectors.toList());
        List<List<Integer>> useCountList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<Integer>()).collect(Collectors.toList());
        List<List<Boolean>> abilityUsablesList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<Boolean>()).collect(Collectors.toList());
        partyMembers.forEach(actor -> {
//            cooldownList.set(actor.getCurrentOrder(), List.of(actor.getFirstAbilityCoolDown(), actor.getSecondAbilityCoolDown(), actor.getThirdAbilityCoolDown()));
            cooldownList.set(actor.getCurrentOrder(), actor.getAbilityCooldowns());
            useCountList.set(actor.getCurrentOrder(), actor.getAbilityUseCounts());
            abilityUsablesList.set(actor.getCurrentOrder(), actor.getAbilityUsables());
        });

        return ActorLogicResult.builder()
                .mainBattleActorId(mainActor.getId())
                .mainActorId(mainActor.getActor().getId())
                .mainBattleActorOrder(mainActor.getCurrentOrder())
                .moveType(move.getType())
                .motionType(move.getMotionType())

                .mainActorName(mainActor.getActor().getNameEn())
                .moveName(move.getName())

                .currentTurn(mainActor.getMember().getCurrentTurn())

                .hps(hps)
                .hpRates(hpRates)
                .chargeGauges(chargeGauges)
                .fatalChainGauge(fatalChainGauge)
                .addedBattleStatusesList(addedStatusList)
                .removedBattleStatusesList(removedStatusList)
                .currentBattleStatusesList(currentBattleStatusesDtoList)
                .heals(healValues)

                .totalHitCount(totalHitCount)
                .attackMultiHitCount(damageLogicResult.getAttackMultiHitCount())
                .damages(damageLogicResult.getDamages())
                .damageElementTypes(damageLogicResult.getElementTypes())
                .additionalDamages(damageLogicResult.getAdditionalDamages())
                .abilityCooldowns(cooldownList)
                .abilityUseCounts(useCountList)
                .abilityUsables(abilityUsablesList)

                .executeChargeAttack(executeChargeAttack)
                .executeAttackTargetType(executeAttackTargetType)
                .build();
    }


}
