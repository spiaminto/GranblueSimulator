package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.BattleStatusDto;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class EnemyLogicResultMapper {

    /**
     * 적 로직에서 아무것도 하지않았을때 리턴 (null X)
     *
     * @return
     */
    public ActorLogicResult emptyResult() {
        return ActorLogicResult.builder().moveType(MoveType.NONE).build();
    }

    /**
     * 적의 경우 무브만 필요한때 사용
     * break
     *
     * @param mainActor
     * @param partyMembers
     * @param move
     * @return
     */
    public ActorLogicResult toResultMoveOnly(BattleActor mainActor, List<BattleActor> partyMembers, Move move) {
        return map(mainActor, partyMembers, move, null, null, null, null, null, false, false);
    }

    /**
     * 적의 Omen 결과를 같이 맵핑해야할때 사용
     * STANDBY 시작, 적의 피격으로인한 STANDBY 갱신(전조 갱신), BREAK 때 사용
     *
     * @param mainActor
     * @param partyMembers
     * @param move
     * @param omen
     * @return
     */
    public ActorLogicResult toResultWithOmen(BattleActor mainActor, List<BattleActor> partyMembers, Move move, Omen omen) {
        return map(mainActor, partyMembers, move, null, null, null, null, omen, false, false);
    }

    /**
     * 데미지만 발생하는 일반공격 결과
     *
     * @param mainActor
     * @param partyMembers
     * @param move
     * @return
     */
    public ActorLogicResult attackToResult(BattleActor mainActor, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, List<Integer> damageTargetOrders) {
        return map(mainActor, partyMembers, move, damageLogicResult, damageTargetOrders, null, null, null, false, false);
    }

    /**
     * 데미지와 스테이터스가 발생하는 기본결과 맵핑
     *
     * @param mainActor
     * @param partyMembers
     * @param move
     * @return
     */
    public ActorLogicResult toResult(BattleActor mainActor, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, List<Integer> damageTargetOrders, SetStatusResult statusResult) {
        return map(mainActor, partyMembers, move, damageLogicResult, damageTargetOrders, statusResult, null, null, false, false);
    }


    /**
     * 데미지와 스테이터스가 발생하는 기본결과 맵핑
     *
     * @param mainActor
     * @param partyMembers
     * @param move
     * @return
     */
    public ActorLogicResult toResultWithEffect(BattleActor mainActor, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, List<Integer> damageTargetOrders, SetStatusResult statusResult, boolean powerUp, boolean ctMax) {
        return map(mainActor, partyMembers, move, damageLogicResult, damageTargetOrders, statusResult, null, null, powerUp, ctMax);
    }

    protected ActorLogicResult map(BattleActor mainActor, List<BattleActor> partyMembers, Move move, DamageLogicResult damageLogicResult, List<Integer> targetOrders, SetStatusResult setStatusResult, StatusTargetType executeAttackTargetType, Omen omen, boolean powerUp, boolean ctMax) {
        if (setStatusResult == null) setStatusResult = SetStatusResult.emptyResult(); // 스테이터스 효과가 발생하지 않은 경우 빈객체
        if (damageLogicResult == null)
            damageLogicResult = DamageLogicResult.builder().build(); // 데미지가 발생하지 않은경우 빈 객체 생성

        int hitCount = damageLogicResult.getDamages().stream().filter(damage -> damage > 0).toList().size();
        ; // 적은 공격횟수가 가변인경우가 없음
        int totalHitCount = hitCount + damageLogicResult.getAdditionalDamages().stream()
                .map(additionalDamages -> additionalDamages.stream()
                        .filter(damage -> damage > 0)
                        .toList())
                .mapToInt(List::size)
                .sum();
        int fatalChainGauge = partyMembers.stream()
                .filter(battleActor -> battleActor.getActor().isLeaderCharacter()).findFirst()
                .map(BattleActor::getFatalChainGauge).orElseGet(() -> 0);

        // 체력
        List<Integer> hps = IntStream.range(0, 5).mapToObj(i -> 0).collect(Collectors.toList());
        List<Integer> hpRates = IntStream.range(0, 5).mapToObj(i -> 0).collect(Collectors.toList());
        hps.set(0, mainActor.getHp());
        hpRates.set(0, mainActor.getHpRate());
        partyMembers.forEach(battleActor -> {
            hps.set(battleActor.getCurrentOrder(), battleActor.getHp());
            hpRates.set(battleActor.getCurrentOrder(), battleActor.getHpRate());
        });

        // 오의게이지
        List<Integer> chargeGauges = IntStream.range(0, 5).mapToObj(i -> 0).collect(Collectors.toList());
        chargeGauges.set(0, mainActor.getChargeGauge());
        partyMembers.forEach(battleActor -> chargeGauges.set(battleActor.getCurrentOrder(), battleActor.getChargeGauge()));

        // 추가된 스테이터스
        List<List<BattleStatusDto>> addedStatusList = setStatusResult.getAddedStatusesList();
        // 삭제된 스테이터스
        List<List<BattleStatusDto>> removedStatusList = setStatusResult.getRemovedStatuesList();
        // 결과 스테이터스
        List<List<BattleStatus>> currentBattleStatusesList = IntStream.range(0, 5).mapToObj(i -> new ArrayList<BattleStatus>()).collect(Collectors.toList());
        currentBattleStatusesList.set(0, mainActor.getBattleStatuses());
        partyMembers.forEach(partyMember -> currentBattleStatusesList.set(partyMember.getCurrentOrder(), partyMember.getBattleStatuses()));
        List<List<BattleStatusDto>> currentBattleStatusesDtoList = currentBattleStatusesList.stream()
                .map(currentBattleStatuses -> currentBattleStatuses.stream()
                        .map(BattleStatusDto::of)
                        .sorted(Comparator.comparing(BattleStatusDto::getCreatedAt))
                        .toList())
                .toList();

        // 힐
        List<Integer> healValues = new ArrayList<>(setStatusResult.getHealValues());

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

        // 전조 있을때 처리
        BattleEnemy enemy = (BattleEnemy) mainActor;
        OmenType omenType = omen != null ? omen.getOmenType() : null; // omenValue 가 존재하면 omenType 지정 (omenValue 가 없을때 omenType 을 null 로 놔둠)
        String omenCancelCondInfo = omen != null ? omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex()).getInfo() : null; // 보여줄 텍스트만
        Integer omenValue = omen != null ? enemy.getOmenValue() : null;
        String omenName = omen != null ? omen.getName() : null;
        String omenInfo = omen != null ? omen.getInfo() : null;

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

                .omenType(omenType)
                .omenCancelCondInfo(omenCancelCondInfo)
                .omenValue(omenValue)
                .omenName(omenName)
                .omenInfo(omenInfo)

                .enemyAttackTargetOrders(targetOrders)
                .isAllTarget(move.isAllTarget())

                .totalHitCount(totalHitCount)
                .attackMultiHitCount(damageLogicResult.getAttackMultiHitCount()) // 현재 적은 난격적용없으므로 1로 고정
                .damages(damageLogicResult.getDamages())
                .damageElementTypes(damageLogicResult.getElementTypes())
                .additionalDamages(damageLogicResult.getAdditionalDamages())
                .abilityCooldowns(cooldownList)
                .abilityUsables(abilityUsablesList)
                .abilityUseCounts(useCountList)

                .executeChargeAttack(false) // 적은 오의 2회발동 없음
                .executeAttackTargetType(null) // 적은 턴 진행 없이 일반공격 없음

                .enemyPowerUp(powerUp)
                .enemyCtMax(ctMax)

                .build();
    }


}
