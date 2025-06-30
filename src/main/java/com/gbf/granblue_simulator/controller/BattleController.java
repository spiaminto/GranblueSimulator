package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.controller.request.*;
import com.gbf.granblue_simulator.controller.response.BattleResponse;
import com.gbf.granblue_simulator.controller.response.GuardResponse;
import com.gbf.granblue_simulator.controller.response.StatusDto;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleCharacter;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.BattleLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.GuardResult;
import com.gbf.granblue_simulator.repository.MemberRepository;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.repository.actor.BattleActorRepository;
import com.gbf.granblue_simulator.repository.actor.BattleCharacterRepository;
import com.gbf.granblue_simulator.repository.actor.BattleEnemyRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BattleController {

    private final MemberRepository memberRepository;
    private final BattleCharacterRepository battleCharacterRepository;
    private final ActorRepository actorRepository;
    private final BattleActorRepository battleActorRepository;
    private final BattleEnemyRepository battleEnemyRepository;
    private final MoveRepository moveRepository;

    private final BattleLogic battleLogic;


    @GetMapping("/api/enemy-src")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEnemySrcMap(@RequestParam Long memberId) {
//        Long memberId = request.getMemberId();
//        Long roomId = request.getRoomId();
        Map<String, Object> result = new HashMap<>();

        BattleActor enemy = battleActorRepository.findByMemberIdOrderByCurrentOrderAsc(memberId).getFirst();
        // 적의 effectVideo 를 key = effectVideoSrc, value = [MoveType.parentType.className, MoveType.className1 , ...] 인 Map 으로 변환
        // ex) 같은 effectVideoSrc (standby-1.webm) 을 가지는 STAND_BY_A, STAND_BY_D 의 경우 value 를 [standby, standby-a, standby-d] 로 묶는다.
        Map<String, List<String>> enemyVideoSrcMap = new HashMap<>();
        enemyVideoSrcMap.putAll(enemy.getActor().getMoves().values().stream()
                .collect(Collectors.groupingBy(
                        move -> move.getAsset().getEffectVideoSrc() != null ? move.getAsset().getEffectVideoSrc() : "",
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                moves -> {
                                    List<String> classNames = new ArrayList<>(moves.stream()
                                            .map(move -> move.getType().getClassName())
                                            .toList());
                                    String parentClassName = moves.getFirst().getType().getParentType().getClassName();
                                    classNames.add(parentClassName);
                                    return classNames;
                                }
                        )
                )));

        result.put("video", enemyVideoSrcMap);
//        enemyVideoSrcMap.entrySet().forEach(
//                entry -> log.info("key = {}, value = {}", entry.getKey(), entry.getValue())
//        );

        Map<String, List<String>> enemyAudioSrcMap = new HashMap<>();
        enemyAudioSrcMap.putAll(enemy.getActor().getMoves().values().stream()
                .collect(Collectors.groupingBy(
                        move -> move.getAsset().getSeAudioSrc() != null ? move.getAsset().getSeAudioSrc() : "",
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                moves -> {
                                    List<String> classNames = new ArrayList<>(moves.stream()
                                            .map(move -> move.getType().getClassName())
                                            .toList());
                                    String parentClassName = moves.getFirst().getType().getParentType().getClassName();
                                    classNames.add(parentClassName);
                                    return classNames;
                                }
                        )
                )));
        result.put("audio", enemyAudioSrcMap);

        return ResponseEntity.ok(result);
    }


    @PostMapping("/api/move")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> move(@RequestBody MoveRequest moveRequest) {
        log.info("moveRequest: {}", moveRequest);

        long characterId = moveRequest.getCharacterId();
        long memberId = moveRequest.getMemberId();
        long moveId = moveRequest.getMoveId();

        List<BattleActor> partyMembers = battleCharacterRepository.findByMemberIdOrderByCurrentOrderAsc(memberId);
        List<BattleActor> allActors = battleActorRepository.findByMemberIdOrderByCurrentOrderAsc(memberId).stream().sorted(Comparator.comparing(BattleActor::getCurrentOrder)).toList();
        BattleActor mainCharacter = partyMembers.stream().filter(battleCharacter -> battleCharacter.getId().equals(characterId)).findFirst().orElseThrow();
        BattleEnemy battleEnemy = battleEnemyRepository.findByMemberId(memberId).orElseThrow();

//        partyMembers.forEach(character -> log.info("character: {}", character));
//        log.info("enemy: {}", battleEnemy);

        List<ActorLogicResult> results = battleLogic.processMove(mainCharacter, battleEnemy, partyMembers, moveId);

        List<BattleResponse> responses = results.stream()
                .map(result -> toBattleResponse(result, allActors)).toList();

        responses.stream().filter(response -> response.getMoveType() == MoveType.SUMMON_DEFAULT)
                        .findFirst().ifPresent(response -> response.setSummonId(moveId)); // 솬석인 경우 id 세팅

        responses.forEach(response -> log.info("response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/api/turn-progress")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> turnProgress(@RequestBody TurnProgressRequest turnProgressRequest) {
        log.info("turnProgressRequest: {}", turnProgressRequest);
        long memberId = turnProgressRequest.getMemberId();

        List<BattleActor> allActors = battleActorRepository.findByMemberIdOrderByCurrentOrderAsc(memberId);
        BattleActor enemy = allActors.getFirst();
        List<BattleActor> partyMembers = allActors.subList(1, allActors.size());

        List<ActorLogicResult> turnProgressResults = battleLogic.progressTurn(enemy, partyMembers);

        List<BattleResponse> responses = turnProgressResults.stream().map(result ->
                toBattleResponse(result, allActors)
        ).toList();
        responses.forEach(response -> log.info("response: {}", response));

        return ResponseEntity.ok(responses);
    }

    private static BattleResponse toBattleResponse(ActorLogicResult result, List<BattleActor> allActors) {
        return BattleResponse.builder()
                .charOrder(result.getMainBattleActorOrder())
                .moveType(result.getMoveType())
                .damages(result.getDamages().stream().map(damage -> damage > 0 ? damage + "" : "MISS").toList())
                .additionalDamages(result.getAdditionalDamages().stream().map(additionalDamage -> additionalDamage.stream().map(damage -> damage > 0 ? damage + "" : "MISS").toList()).toList())
                .elementTypes(result.getDamageElementTypes())
                .totalHitCount(result.getTotalHitCount())
                .attackMultiHitCount(result.getAttackMultiHitCount())
                .hps(result.getHps())
                .hpRates(result.getHpRates())
                .chargeGauges(result.getChargeGauges())
                .fatalChainGauge(result.getFatalChainGauge())
                .omenType(result.getOmenType())
                .omenValue(result.getOmenValue())
                .isAllTarget(result.isAllTarget())
                .omenCancelCondInfo(result.getOmenCancelCondInfo())
                .omenName(result.getOmenName())
                .omenInfo(result.getOmenInfo())
                .heals(result.getHeals())
                .addedBattleStatusesList(result.getAddedBattleStatusesList().stream()
                        .map(battleStatuses ->
                                battleStatuses.isEmpty() ? new ArrayList<StatusDto>() : battleStatuses.stream()
                                        .map(battleStatus ->
                                                StatusDto.builder()
                                                        .type(battleStatus.getStatus().getType().name())
                                                        .name(battleStatus.getStatus().getName())
                                                        .imageSrc(battleStatus.getIconSrc())
                                                        .effectText(battleStatus.getStatus().getEffectText())
                                                        .statusText(battleStatus.getStatus().getStatusText())
                                                        .duration(battleStatus.getDuration())
                                                        .build()
                                        ).toList()
                        ).toList())
                .removedBattleStatusesList(result.getRemovedBattleStatusesList().stream()
                        .map(battleStatuses ->
                                battleStatuses.isEmpty() ? new ArrayList<StatusDto>() : battleStatuses.stream()
                                        .map(battleStatus ->
                                                StatusDto.builder()
                                                        .type(battleStatus.getStatus().getType().name())
                                                        .name(battleStatus.getStatus().getName())
                                                        .imageSrc(battleStatus.getIconSrc())
                                                        .effectText(battleStatus.getStatus().getEffectText())
                                                        .statusText(battleStatus.getStatus().getStatusText())
                                                        .duration(battleStatus.getDuration())
                                                        .build()
                                        ).toList()
                        ).toList())
                .currentBattleStatusesList(allActors.stream().map(BattleActor::getBattleStatuses)
                        .map(battleStatuses -> battleStatuses.stream()
                                .map(battleStatus ->
                                        StatusDto.builder()
                                                .type(battleStatus.getStatus().getType().name())
                                                .name(battleStatus.getStatus().getName())
                                                .imageSrc(battleStatus.getIconSrc())
                                                .effectText(battleStatus.getStatus().getEffectText())
                                                .statusText(battleStatus.getStatus().getStatusText())
                                                .duration(battleStatus.getDuration())
                                                .build())
                                .toList()
                        ).toList())
                .enemyAttackTargetOrders(result.getEnemyAttackTargetOrders())
                .abilityCoolDowns(result.getAbilityCooldowns())
                .isEnemyPowerUp(result.isEnemyPowerUp())
                .isEnemyCtMax(result.isEnemyCtMax())
                .build();
    }

    @PostMapping("/api/guard")
    @ResponseBody
    public ResponseEntity<GuardResponse> guard(@RequestBody GuardRequest guardRequest) {
        log.info("guard request: {}", guardRequest);
        long memberId = guardRequest.getMemberId();
        long characterId = guardRequest.getCharacterId();

        List<BattleActor> partyMembers = battleCharacterRepository.findByMemberIdOrderByCurrentOrderAsc(memberId);
        List<BattleActor> allActors = battleActorRepository.findByMemberIdOrderByCurrentOrderAsc(memberId).stream().sorted(Comparator.comparing(BattleActor::getCurrentOrder)).toList();
        BattleActor mainCharacter = partyMembers.stream().filter(battleCharacter -> battleCharacter.getId().equals(characterId)).findFirst().orElseThrow();

        List<GuardResult> guardResults = battleLogic.processGuard(mainCharacter, partyMembers, guardRequest.getTargetType());
        boolean guardActivated = mainCharacter.isGuardOn(); // 메인 캐릭터 가드여부 따로 전달
        GuardResponse guardResponse = GuardResponse.builder()
                .isGuardActivated(guardActivated)
                .guardResults(guardResults)
                .build();
        return ResponseEntity.ok(guardResponse);
    }

    public void init() {
        Member testMember = memberRepository.findById(1L).orElseThrow();

        //배틀 액터 생성
        Actor paladinActor = actorRepository.findById(1L).get();
        Actor yachimaActor = actorRepository.findById(2L).get();
        Actor diasporaActor = actorRepository.findById(5L).get();

        // 배틀 액터 생성
        BattleCharacter paladin = BattleCharacter.builder()
                .name(paladinActor.getName())
                .member(testMember)
                .currentOrder(1)
                .build();
        paladin.setActor(paladinActor);
        paladin = battleCharacterRepository.save(paladin);
        BattleCharacter yachima = BattleCharacter.builder()
                .name(yachimaActor.getName())
                .member(testMember)
                .actor(yachimaActor)
                .currentOrder(2)
                .build();
        yachima.setActor(yachimaActor);
        yachima = battleCharacterRepository.save(yachima);
        BattleEnemy diaspora = BattleEnemy.builder()
                .member(testMember)
                .name(diasporaActor.getName())
                .currentOrder(0) // 적이 0
                .build();
        diaspora.setActor(diasporaActor);
        diaspora = battleEnemyRepository.save(diaspora);

        List<BattleActor> partyMembers = List.of(paladin, yachima);
        BattleActor enemy = diaspora;
        battleLogic.startBattle(partyMembers, enemy);
    }



}
