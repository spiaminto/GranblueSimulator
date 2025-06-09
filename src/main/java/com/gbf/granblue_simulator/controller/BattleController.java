package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.controller.response.CharacterInfo;
import com.gbf.granblue_simulator.controller.response.EnemyInfo;
import com.gbf.granblue_simulator.controller.response.EnemyVideoInfo;
import com.gbf.granblue_simulator.controller.response.SummonInfo;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleCharacter;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.logic.BattleLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.repository.MemberRepository;
import com.gbf.granblue_simulator.repository.RoomRepository;
import com.gbf.granblue_simulator.repository.UserRepository;
import com.gbf.granblue_simulator.repository.actor.*;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BattleController {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;

    private final BattleCharacterRepository battleCharacterRepository;
    private final CharacterRepository characterRepository;

    private final ActorRepository actorRepository;
    private final BattleActorRepository battleActorRepository;
    private final BattleLogic battleLogic;
    private final BattleEnemyRepository battleEnemyRepository;
    private final MoveRepository moveRepository;


    @GetMapping("/battle")
    @Transactional
    public String battle(Model model) {
        BattleActor paladin = battleActorRepository.findById(945L).get();
        BattleActor yachima = battleActorRepository.findById(946L).get();
        BattleActor diaspora = battleActorRepository.findById(947L).get();

        BattleEnemy enemy = (BattleEnemy) diaspora;
        String omenPrefix = null;
        Integer omenValue = null;
        OmenType omenType = null;
        String omenName = null;
        if (enemy.getCurrentStandbyType() != null) {
            Omen omen = enemy.getActor().getMoves().get(enemy.getCurrentStandbyType()).getOmen();
            omenPrefix = omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex()).getInfo(); // TODO 나중에 리팩토링 해야될듯
            omenValue = enemy.getOmenValue();
            omenType = omen.getOmenType();
            omenName = omen.getName();
        }

        EnemyInfo enemyInfo = EnemyInfo.builder()
                .id(enemy.getId())
                .name(enemy.getName())
                .phase(enemy.getCurrentForm())
                .statuses(enemy.getBattleStatuses())
                .hpRate(enemy.calcHpRate())
                .currentChargeGauge(enemy.getChargeGauge())
                .maxChargeGauge(Collections.nCopies(enemy.getMaxChargeGauge(), 1))
                .initialMoveType(enemy.getCurrentStandbyType() == null ? MoveType.IDLE_DEFAULT : enemy.getCurrentStandbyType()) // 동적으로
                .omenActivated(enemy.getCurrentStandbyType() != null)

                .omenPrefix(omenPrefix)
                .omenValue(omenValue)
                .omenType(omenType)
                .omenName(omenName)
                .build();
        model.addAttribute("enemyInfo", enemyInfo);

        CharacterInfo paladinInfo = CharacterInfo.builder()
                .id(paladin.getId())
                .name(paladin.getName())
                .portraitSrc(paladin.getActor().getBattlePortraitSrc())
                .statuses(paladin.getBattleStatuses())
                .hp(paladin.getHp())
                .maxHp(paladin.getMaxHp())
                .hpRate(paladin.calcHpRate())
                .chargeGauge(paladin.getChargeGauge())
                .maxChargeGauge(paladin.getMaxChargeGauge())
                .abilities(paladin.getActor().getMoves().values().stream().filter(move -> move.getType().getParentType() == MoveType.ABILITY).sorted(Comparator.comparing(Move::getType)).toList())
                .chargeAttack(paladin.getActor().getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                .abilityCoolDowns(List.of(paladin.getFirstAbilityCoolDown(), paladin.getSecondAbilityCoolDown(), paladin.getThirdAbilityCoolDown()))
                .build();

        CharacterInfo yachimaInfo = CharacterInfo.builder()
                .id(yachima.getId())
                .name(yachima.getName())
                .portraitSrc(yachima.getActor().getBattlePortraitSrc())
                .statuses(yachima.getBattleStatuses())
                .hp(yachima.getHp())
                .maxHp(yachima.getMaxHp())
                .hpRate(yachima.calcHpRate())
                .chargeGauge(yachima.getChargeGauge())
                .maxChargeGauge(yachima.getMaxChargeGauge())
                .abilities(yachima.getActor().getMoves().values().stream().filter(move -> move.getType().getParentType() == MoveType.ABILITY).sorted(Comparator.comparing(Move::getType)).toList())
                .chargeAttack(yachima.getActor().getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                .abilityCoolDowns(List.of(yachima.getFirstAbilityCoolDown(), yachima.getSecondAbilityCoolDown(), yachima.getThirdAbilityCoolDown()))
                .build();

        List<CharacterInfo> characterInfos = List.of(paladinInfo, yachimaInfo);
        model.addAttribute("characterInfos", characterInfos);

        // 소환석 인포


        List<SummonInfo> summonInfos = new ArrayList<>();
        
        // 소환석 무브 추가 -> 여기는 value 에 type 지정이 필요하지 않기 때문에 id 로 대체
        List<Long> summonMoveIds = paladin.getSummonMoveIds();
        summonMoveIds.forEach(System.out::println);
        List<Move> summonMoves = moveRepository.findAllById(summonMoveIds);
        Map<String, Long> summonVideoSrcMap = new HashMap<>();
        summonMoves.forEach(move -> {
            String effectVideoSrc = move.getAsset().getEffectVideoSrc();
            summonVideoSrcMap.put(effectVideoSrc, move.getId());
        });
        model.addAttribute("summonVideoSrcMap", summonVideoSrcMap);

        Map<String, Long> summonAudioSrcMap = new HashMap<>();
        summonMoves.forEach(move -> {
            summonAudioSrcMap.put(move.getAsset().getSeAudioSrc(), move.getId());
        });
        model.addAttribute("summonAudioSrcMap", summonAudioSrcMap);

        // 소환석 인포
        summonMoves.forEach(move -> {
            SummonInfo summonInfo = SummonInfo.builder()
                    .id(move.getId())
                    .name(move.getName())
                    .info(move.getInfo())
                    .iconImageSrc(move.getAsset().getIconImageSrc())
                    .cooldown(paladin.getSummonCoolDowns().get(paladin.getSummonMoveIds().indexOf(move.getId())))
                    .build();
            summonInfos.add(summonInfo);
        });
        model.addAttribute("summonInfos", summonInfos);

        // 캐릭터 무브 추가
        List<Move> firstCharacterMoves = paladin.getActor().getMoves().values().stream().toList();
        Map<String, String> firstCharacterVideoSrcMap = new HashMap<>();
        firstCharacterMoves.forEach(move -> {
                    String effectVideoSrc = move.getAsset().getEffectVideoSrc();
                    if (effectVideoSrc != null && !effectVideoSrc.isEmpty()) {
                        firstCharacterVideoSrcMap.put(effectVideoSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " effect");
                    }
                    String motionVideoSrc = move.getAsset().getMotionVideoSrc();
                    if (motionVideoSrc != null && !motionVideoSrc.isEmpty()) {
                        // 현재 모션은 없을수 있음 TODO 향후 모두 존재하도록 변경예정
                        String fullSizeClassName = move.getAsset().isMotionVideoFull() ? "full-size" : "";
                        firstCharacterVideoSrcMap.put(motionVideoSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " motion " + fullSizeClassName);
                    }
                }
        );
        firstCharacterVideoSrcMap.forEach((key, value) -> log.info("key = {}, value = {}", key, value));
        model.addAttribute("firstCharacterVideoSrcMap", firstCharacterVideoSrcMap);

        Map<String, String> firstCharacterAudioSrcMap = new HashMap<>();
        firstCharacterMoves.forEach(move -> {
            firstCharacterAudioSrcMap.put(move.getAsset().getSeAudioSrc(), move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " effect");
            String voiceAudioSrc = move.getAsset().getVoiceAudioSrc();
            if (voiceAudioSrc != null && !voiceAudioSrc.isEmpty()) {
                // 현재 보이스는 없을 수 있음 TODO 향후 모두 존재하도록 변경 예정
                firstCharacterAudioSrcMap.put(voiceAudioSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " voice");
            }
        });
        firstCharacterAudioSrcMap.forEach((key, value) -> log.info("key = {}, value = {}", key, value));
        model.addAttribute("firstCharacterAudioSrcMap", firstCharacterAudioSrcMap);


        List<Move> secondCharacterMoves = yachima.getActor().getMoves().values().stream().toList();
        Map<String, String> secondCharacterVideoSrcMap = new HashMap<>();
        secondCharacterMoves.forEach(move -> {
                    String effectVideoSrc = move.getAsset().getEffectVideoSrc();
                    if (effectVideoSrc != null && !effectVideoSrc.isEmpty()) {
                        secondCharacterVideoSrcMap.put(effectVideoSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " effect");
                    }
                    String motionVideoSrc = move.getAsset().getMotionVideoSrc();
                    if (motionVideoSrc != null && !motionVideoSrc.isEmpty()) {
                        // 현재 모션은 없을수 있음 TODO 향후 모두 존재하도록 변경예정
                        String fullSizeClassName = move.getAsset().isMotionVideoFull() ? "full-size" : "";
                        secondCharacterVideoSrcMap.put(motionVideoSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " motion " + fullSizeClassName);
                    }
                }
        );
        model.addAttribute("secondCharacterVideoSrcMap", secondCharacterVideoSrcMap);

        Map<String, String> secondCharacterAudioSrcMap = new HashMap<>();
        secondCharacterMoves.forEach(move -> {
            secondCharacterAudioSrcMap.put(move.getAsset().getSeAudioSrc(), move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " effect");
            String voiceAudioSrc = move.getAsset().getVoiceAudioSrc();
            if (voiceAudioSrc != null && !voiceAudioSrc.isEmpty()) {
                // 현재 보이스는 없을 수 있음 TODO 향후 모두 존재하도록 변경 예정
                secondCharacterAudioSrcMap.put(voiceAudioSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " voice");
            }
        });
        model.addAttribute("secondCharacterAudioSrcMap", secondCharacterAudioSrcMap);

        // 적의 effectVideo 를 key = effectVideoSrc, value = [MoveType.parentType.className, MoveType.className1 , ...] 인 Map 으로 변환
        // ex) 같은 effectVideoSrc (standby-1.webm) 을 가지는 STAND_BY_A, STAND_BY_D 의 경우 value 를 [standby, standby-a, standby-d] 로 묶는다.
        Map<String, EnemyVideoInfo> enemyVideoSrcMap = new HashMap<>();
        enemyVideoSrcMap.putAll(enemy.getActor().getMoves().values().stream()
                .collect(Collectors.groupingBy(
                        move -> move.getAsset().getEffectVideoSrc(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                moves -> {
                                    List<String> classNames = new ArrayList<>(moves.stream()
                                            .map(move -> move.getType().getClassName())
                                            .toList());
                                    String parentClassName = moves.getFirst().getType().getParentType().getClassName();
                                    Integer hitEffectDelay = moves.getFirst().getAsset().getEffectHitDelay();
                                    classNames.add(parentClassName);
                                    return EnemyVideoInfo.builder()
                                            .effectHitDelay(hitEffectDelay)
                                            .classNames(classNames)
                                            .build();
                                }
                        )
                )));

        model.addAttribute("enemyVideoSrcMap", enemyVideoSrcMap);
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
        Member testMember = memberRepository.findById(1L).orElseThrow();
        model.addAttribute("enemyAudioSrcMap", enemyAudioSrcMap);
        model.addAttribute("member", testMember);

        return "battle";
    }

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
                        move -> move.getAsset().getEffectVideoSrc(),
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


    @PostMapping("/api/ability")
    @ResponseBody
    public ResponseEntity<List<AbilityResponse>> ability(@RequestBody AbilityRequest abilityRequest) {
        log.info("abilityRequest: {}", abilityRequest);

        long memberId = abilityRequest.getMemberId();
        long characterId = abilityRequest.getCharacterId();
        long roomId = abilityRequest.getRoomId();
        long abilityId = abilityRequest.getAbilityId();
        MoveType moveType = MoveType.valueOf(abilityRequest.getMoveType());
        long abilityOrder = abilityRequest.getAbilityOrder();
        long characterOrder = abilityRequest.getCharacterOrder();

        List<BattleActor> partyMembers = battleCharacterRepository.findByMemberIdOrderByCurrentOrderAsc(memberId);
        List<BattleActor> allActors = battleActorRepository.findByMemberIdOrderByCurrentOrderAsc(memberId).stream().sorted(Comparator.comparing(BattleActor::getCurrentOrder)).toList();
        BattleActor mainCharacter = partyMembers.stream().filter(battleCharacter -> battleCharacter.getId().equals(characterId)).findFirst().orElseThrow();
        Long moveId = mainCharacter.getActor().getMoves().get(moveType).getId(); // TODO 나중에 바꿀것
        BattleEnemy battleEnemy = battleEnemyRepository.findByMemberId(memberId).orElseThrow();

//        partyMembers.forEach(character -> log.info("character: {}", character));
//        log.info("enemy: {}", battleEnemy);

        List<ActorLogicResult> results = battleLogic.processAbility(mainCharacter, battleEnemy, partyMembers, moveId);

        List<AbilityResponse> responses = results.stream().map(result ->
                AbilityResponse.builder()
                        .charOrder(result.getMainBattleActorOrder())
                        .moveType(result.getMoveType())
                        .damages(result.getDamages())
                        .elementTypes(result.getDamageElementTypes())
                        .hitCount(result.getTotalHitCount())
                        .additionalDamages(result.getAdditionalDamages())
                        .hps(result.getHps())
                        .hpRates(result.getHpRates())
                        .chargeGauges(result.getChargeGauges())
                        .omenType(result.getOmenType())
                        .omenValue(result.getOmenValue())
                        .omenCancelCondInfo(result.getOmenCancelCondInfo())
                        .omenName(result.getOmenName())
                        .addedBattleStatusList(result.getAddedBattleStatusesList().stream()
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
                        .removedBattleStatusList(result.getRemovedBattleStatusesList().stream()
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
                        .battleStatusList(allActors.stream().map(BattleActor::getBattleStatuses)
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
                        .abilityCoolDowns(result.getAbilityCooldowns())
                        .isEnemyDispelled(result.isEnemyDispelled())
                        .isPartyMemberDispelled(result.isPartyMemberDispelled())
                        .isEnemyPowerUp(result.isEnemyPowerUp())
                        .isEnemyCtMax(result.isEnemyCtMax())
                        .build()
        ).toList();
        responses.forEach(response -> log.info("response: {}", response));


        return ResponseEntity.ok(responses);
    }

    @PostMapping("/api/turn-progress")
    @ResponseBody
    public ResponseEntity<List<AbilityResponse>> turnProgress(@RequestBody TurnProgressRequest turnProgressRequest) {
        log.info("turnProgressRequest: {}", turnProgressRequest);
        long memberId = turnProgressRequest.getMemberId();

        List<BattleActor> allActors = battleActorRepository.findByMemberIdOrderByCurrentOrderAsc(memberId);
        BattleActor enemy = allActors.getFirst();
        List<BattleActor> partyMembers = allActors.subList(1, allActors.size());

        List<ActorLogicResult> turnProgressResults = battleLogic.progressTurn(enemy, partyMembers);

        List<AbilityResponse> responses = turnProgressResults.stream().map(result ->
                AbilityResponse.builder()
                        .charOrder(result.getMainBattleActorOrder())
                        .moveType(result.getMoveType())
                        .damages(result.getDamages())
                        .elementTypes(result.getDamageElementTypes())
                        .hitCount(result.getTotalHitCount())
                        .additionalDamages(result.getAdditionalDamages())
                        .hps(result.getHps())
                        .hpRates(result.getHpRates())
                        .enemyAttackTargetOrders(result.getEnemyAttackTargetOrders())
                        .isAllTarget(result.isAllTarget())
                        .omenName(result.getOmenName())
                        .omenType(result.getOmenType())
                        .omenValue(result.getOmenValue())
                        .omenCancelCondInfo(result.getOmenCancelCondInfo())
                        .chargeGauges(result.getChargeGauges())
                        .addedBattleStatusList(result.getAddedBattleStatusesList().stream()
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
                        .removedBattleStatusList(result.getRemovedBattleStatusesList().stream()
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
                        .battleStatusList(allActors.stream().map(BattleActor::getBattleStatuses)
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
                        .abilityCoolDowns(result.getAbilityCooldowns())
                        .isEnemyDispelled(result.isEnemyDispelled())
                        .isPartyMemberDispelled(result.isPartyMemberDispelled())
                        .isEnemyPowerUp(result.isEnemyPowerUp())
                        .isEnemyCtMax(result.isEnemyCtMax())
                        .build()
        ).toList();
        responses.forEach(response -> log.info("response: {}", response));


        return ResponseEntity.ok(responses);
    }

    @PostMapping("/api/summon")
    @ResponseBody
    public ResponseEntity<List<AbilityResponse>> summon(@RequestBody SummonRequest summonRequest) {
        log.info("summonRequest: {}", summonRequest);

        long memberId = summonRequest.getMemberId();
        long characterId = summonRequest.getCharacterId();
        long roomId = summonRequest.getRoomId();
        long summonMoveId = summonRequest.getSummonId();
        MoveType moveType = MoveType.valueOf(summonRequest.getMoveType());

        List<BattleActor> partyMembers = battleCharacterRepository.findByMemberIdOrderByCurrentOrderAsc(memberId);
        List<BattleActor> allActors = battleActorRepository.findByMemberIdOrderByCurrentOrderAsc(memberId).stream().sorted(Comparator.comparing(BattleActor::getCurrentOrder)).toList();
        BattleActor mainCharacter = partyMembers.stream().filter(battleCharacter -> battleCharacter.getId().equals(characterId)).findFirst().orElseThrow();
        BattleEnemy battleEnemy = battleEnemyRepository.findByMemberId(memberId).orElseThrow();

//        partyMembers.forEach(character -> log.info("character: {}", character));
//        log.info("enemy: {}", battleEnemy);

        List<ActorLogicResult> results = battleLogic.processSummon(mainCharacter, battleEnemy, partyMembers, summonMoveId);

        List<AbilityResponse> responses = results.stream().map(result ->
                AbilityResponse.builder()
                        .charOrder(result.getMainBattleActorOrder())
                        .moveType(result.getMoveType())
                        .summonId(summonMoveId)
                        .damages(result.getDamages())
                        .elementTypes(result.getDamageElementTypes())
                        .hitCount(result.getTotalHitCount())
                        .additionalDamages(result.getAdditionalDamages())
                        .hps(result.getHps())
                        .hpRates(result.getHpRates())
                        .chargeGauges(result.getChargeGauges())
                        .omenType(result.getOmenType())
                        .omenValue(result.getOmenValue())
                        .omenCancelCondInfo(result.getOmenCancelCondInfo())
                        .omenName(result.getOmenName())
                        .addedBattleStatusList(result.getAddedBattleStatusesList().stream()
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
                        .removedBattleStatusList(result.getRemovedBattleStatusesList().stream()
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
                        .battleStatusList(allActors.stream().map(BattleActor::getBattleStatuses)
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
                        .abilityCoolDowns(result.getAbilityCooldowns())
                        .isEnemyDispelled(result.isEnemyDispelled())
                        .isPartyMemberDispelled(result.isPartyMemberDispelled())
                        .build()
        ).toList();
        responses.forEach(response -> log.info("response: {}", response));


        return ResponseEntity.ok(responses);
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
