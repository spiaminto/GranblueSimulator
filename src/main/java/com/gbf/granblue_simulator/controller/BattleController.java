package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.auth.PrincipalDetails;
import com.gbf.granblue_simulator.controller.request.battle.GuardRequest;
import com.gbf.granblue_simulator.controller.request.battle.MoveRequest;
import com.gbf.granblue_simulator.controller.request.battle.ToggleChargeAttackRequest;
import com.gbf.granblue_simulator.controller.request.battle.TurnProgressRequest;
import com.gbf.granblue_simulator.controller.response.battle.BattleResponse;
import com.gbf.granblue_simulator.controller.response.battle.GuardResponse;
import com.gbf.granblue_simulator.controller.response.battle.StatusDto;
import com.gbf.granblue_simulator.controller.response.battle.ToggleChargeAttackResponse;
import com.gbf.granblue_simulator.controller.response.info.battle.*;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.Room;
import com.gbf.granblue_simulator.domain.actor.Party;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.asset.Asset;
import com.gbf.granblue_simulator.domain.move.MotionType;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.BattleLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.GuardResult;
import com.gbf.granblue_simulator.repository.AssetRepository;
import com.gbf.granblue_simulator.repository.MemberRepository;
import com.gbf.granblue_simulator.repository.PartyRepository;
import com.gbf.granblue_simulator.repository.RoomRepository;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.repository.actor.BattleActorRepository;
import com.gbf.granblue_simulator.repository.actor.BattleCharacterRepository;
import com.gbf.granblue_simulator.repository.actor.BattleEnemyRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import com.gbf.granblue_simulator.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

import static com.gbf.granblue_simulator.controller.BattleInfoMapper.*;

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
    private final MemberService memberService;
    private final RoomRepository roomRepository;
    private final PartyRepository partyRepository;
    private final AssetRepository assetRepository;


    @GetMapping("/api/enemy-src")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEnemySrcMap(@RequestParam Long memberId) {


        Map<String, Object> result = new HashMap<>();

        BattleActor enemy = battleActorRepository.findByMemberIdOrderByCurrentOrderAsc(memberId).getFirst();
        List<Asset> enemyAssets = assetRepository.findAllByActorId(5L);
        List<AssetInfo.Asset> assetInfoAsset = toAssetInfo(0L, "", enemyAssets, new ArrayList<>(), null);
        AssetInfo enemyAssetInfo = AssetInfo.builder()
                .asset(assetInfoAsset.getFirst())
                .startMotion(MotionType.WAIT)
                .isChargeAttackSkip(false)
                .isMainCharacter(false)
                .isEnemy(true)
                .build();
        result.put("assetInfo", enemyAssetInfo);

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

    @PostMapping("/api/guard")
    @ResponseBody
    public ResponseEntity<GuardResponse> guard(@RequestBody GuardRequest guardRequest,
                                               @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("guard request: {}", guardRequest);
        long memberId = guardRequest.getMemberId();
        long characterId = guardRequest.getCharacterId();

        // TODO 검증

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

    @PostMapping("/api/toggle-charge-attack")
    @ResponseBody
    public ResponseEntity<ToggleChargeAttackResponse> chargeAttackOn(@RequestBody ToggleChargeAttackRequest request,
                                                                     @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("chargeAttackOnRequest = {}", request);

        // TODO 검증
        Long userId = principalDetails == null ? 1L : principalDetails.getId();

        boolean chargeAttackOn = memberService.updateChargeAttackOn(request.getRoomId(), userId, request.isChargeAttackOn());
        ToggleChargeAttackResponse response = ToggleChargeAttackResponse.builder()
                .chargeAttackOn(chargeAttackOn)
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/room/{roomId}")
    @Transactional
    public String getRoom(@PathVariable Long roomId,
                          @AuthenticationPrincipal PrincipalDetails principal, Model model) {
        if (principal == null) {
            return "redirect:/";
        }

        Member findMember = memberRepository.findByRoomIdAndUserId(roomId, principal.getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        List<BattleActor> battleActors = findMember.getBattleActors();
        battleActors.sort(Comparator.comparing(BattleActor::getCurrentOrder));

        BattleActor enemyActor = battleActors.getFirst();
        List<BattleActor> partyMembers = battleActors.subList(1, battleActors.size());
        BattleActor mainCharacter = partyMembers.stream().filter(battleActor -> battleActor.getActor().isMainCharacter()).findFirst().orElseThrow(() -> new IllegalArgumentException("메인 캐릭터 찾을수 없음"));

        Party party = partyRepository.findById(findMember.getPartyId()).orElseThrow(() -> new IllegalArgumentException("파티가 지정되있지 않습니다."));

        List<Asset> assets = assetRepository.findWithChildrenByAssetId(party.getActorAssetIds()).stream()
                .sorted(Comparator.comparing(asset -> party.getActorIds().indexOf(asset.getId())))
                .collect(Collectors.toList());
        List<Asset> summonAssets = assetRepository.findAllByMoveIdIn(party.getSummonIds());
        Asset fatalChainAsset = assetRepository.findByMoveId(mainCharacter.getFatalChainMoveId());
        List<Asset> enemyAsset = assetRepository.findAllByActorId(enemyActor.getActor().getId());
        assets.addAll(enemyAsset);

        if (battleActors.isEmpty()) {
            return "redirect:/";
        }

        // 인포 추가 시작

        // 캐릭터 인포
        List<BattleCharacterInfo> battleCharacterInfos = partyMembers.stream().map(BattleInfoMapper::toCharacterInfo).toList();
        model.addAttribute("battleCharacterInfos", battleCharacterInfos);

        // 적 인포
        BattleEnemy enemy = (BattleEnemy) enemyActor;
        BattleEnemyInfo battleEnemyInfo = toEnemyInfo(enemy);
        model.addAttribute("battleEnemyInfo", battleEnemyInfo);

        // 소환석 인포
        List<Long> summonMoveIds = mainCharacter.getSummonMoveIds();
        List<Move> summonMoves = moveRepository.findAllById(summonMoveIds);
        List<SummonInfo> summonInfos = summonMoves.stream().map(move -> toSummonInfo(move, mainCharacter)).toList();
        model.addAttribute("summonInfos", summonInfos);

        // 페이탈 체인 인포
        Long fatalChainMoveId = mainCharacter.getFatalChainMoveId();
        FatalChainInfo fatalChainInfo = moveRepository.findById(fatalChainMoveId).map(move -> toFatalChainInfo(mainCharacter, move)).orElseGet(() -> null);
        model.addAttribute("fatalChainInfo", fatalChainInfo);

        // 에셋
        List<AssetInfo.Asset> assetInfoAssets = toAssetInfo(mainCharacter.getActor().getId(), mainCharacter.getActor().getWeaponId(), assets, summonAssets, fatalChainAsset);

        List<Long> sortedActorIds = battleActors.stream().map(battleActor -> battleActor.getActor().getId()).toList();
        assetInfoAssets.sort(Comparator.comparing(assetInfo -> sortedActorIds.indexOf(assetInfo.getActorId()) + 1));

        Move enemyStartMove = enemy.getCurrentStandbyType() != null ? enemy.getMove(enemy.getCurrentStandbyType()) : enemy.getMove(MoveType.IDLE_DEFAULT);
        boolean isChargeAttackSkip = mainCharacter.getMember().isChargeAttackSkip();
        List<AssetInfo> assetInfos = assetInfoAssets.stream().map(asset -> {
                    boolean isEnemy = enemyActor.getActor().getId().equals(asset.getActorId());
                    boolean isMainCharacter = mainCharacter.getActor().getId().equals(asset.getActorId());
                    MotionType startMotion = isEnemy ? enemyStartMove.getMotionType() : MotionType.STB_WAIT;
                    return AssetInfo.builder()
                            .asset(asset)
                            .isChargeAttackSkip(isChargeAttackSkip)
                            .isEnemy(isEnemy)
                            .isMainCharacter(isMainCharacter)
                            .startMotion(startMotion)
                            .build();
                }
        ).toList();

        assetInfos.forEach(assetInfo -> log.info("assertInfo = {}", assetInfo));
        log.info("sortedActorIds = {}", sortedActorIds);
        model.addAttribute("assetInfos", assetInfos);

        // 멤버 추가
        model.addAttribute("member", findMember);

        model.asMap().entrySet().forEach(entry -> {
            log.info("k = {}", entry.getKey());
            log.info("v = {}", entry.getValue());
        });


        return "battle-canvas";
    }

    @GetMapping("/battle")
    @Transactional
    public String battle(Model model) {

//        Room findRoom = roomRepository.findById(147L).orElseThrow(() -> new IllegalArgumentException("방을 찾을수 없음"));

        Member findMember = memberRepository.findByRoomIdAndUserId(147L, 1L).orElseThrow(() -> new IllegalArgumentException("멤버를 찾을수 없음"));
        List<BattleActor> battleActors = findMember.getBattleActors();
        battleActors.sort(Comparator.comparing(BattleActor::getCurrentOrder));

        BattleActor enemyActor = battleActors.getFirst();
        List<BattleActor> partyMembers = battleActors.subList(1, battleActors.size());
        BattleActor mainCharacter = partyMembers.stream().filter(battleActor -> battleActor.getActor().isMainCharacter()).findFirst().orElseThrow(() -> new IllegalArgumentException("메인 캐릭터 찾을수 없음"));

        Party party = partyRepository.findById(findMember.getPartyId()).orElseThrow(() -> new IllegalArgumentException("파티가 지정되있지 않습니다."));

        List<Asset> assets = assetRepository.findWithChildrenByAssetId(party.getActorAssetIds()).stream()
                .sorted(Comparator.comparing(asset -> party.getActorIds().indexOf(asset.getId())))
                .collect(Collectors.toList());
        List<Asset> summonAssets = assetRepository.findAllByMoveIdIn(party.getSummonIds());
        Asset fatalChainAsset = assetRepository.findByMoveId(mainCharacter.getFatalChainMoveId());
        List<Asset> enemyAsset = assetRepository.findAllByActorId(enemyActor.getActor().getId());
        assets.addAll(enemyAsset);

        if (battleActors.isEmpty()) {
            return "redirect:/";
        }

        // 인포 추가 시작

        // 캐릭터 인포
        List<BattleCharacterInfo> battleCharacterInfos = partyMembers.stream().map(BattleInfoMapper::toCharacterInfo).toList();
        model.addAttribute("battleCharacterInfos", battleCharacterInfos);

        // 적 인포
        BattleEnemy enemy = (BattleEnemy) enemyActor;
        BattleEnemyInfo battleEnemyInfo = toEnemyInfo(enemy);
        model.addAttribute("battleEnemyInfo", battleEnemyInfo);

        // 소환석 인포
        List<Long> summonMoveIds = mainCharacter.getSummonMoveIds();
        List<Move> summonMoves = moveRepository.findAllById(summonMoveIds);
        List<SummonInfo> summonInfos = summonMoves.stream().map(move -> toSummonInfo(move, mainCharacter)).toList();
        model.addAttribute("summonInfos", summonInfos);

        // 페이탈 체인 인포
        Long fatalChainMoveId = mainCharacter.getFatalChainMoveId();
        FatalChainInfo fatalChainInfo = moveRepository.findById(fatalChainMoveId).map(move -> toFatalChainInfo(mainCharacter, move)).orElseGet(() -> null);
        model.addAttribute("fatalChainInfo", fatalChainInfo);

        // 에셋
        List<AssetInfo.Asset> assetInfoAssets = toAssetInfo(mainCharacter.getActor().getId(), mainCharacter.getActor().getWeaponId(), assets, summonAssets, fatalChainAsset);

        List<Long> sortedActorIds = battleActors.stream().map(battleActor -> battleActor.getActor().getId()).toList();
        assetInfoAssets.sort(Comparator.comparing(assetInfo -> sortedActorIds.indexOf(assetInfo.getActorId()) + 1));

        Move enemyStartMove = enemy.getCurrentStandbyType() != null ? enemy.getMove(enemy.getCurrentStandbyType()) : enemy.getMove(MoveType.IDLE_DEFAULT);
        boolean isChargeAttackSkip = mainCharacter.getMember().isChargeAttackSkip();
        List<AssetInfo> assetInfos = assetInfoAssets.stream().map(asset -> {
                    boolean isEnemy = enemyActor.getActor().getId().equals(asset.getActorId());
                    boolean isMainCharacter = mainCharacter.getActor().getId().equals(asset.getActorId());
                    MotionType startMotion = isEnemy ? enemyStartMove.getMotionType() : MotionType.STB_WAIT;
                    return AssetInfo.builder()
                            .asset(asset)
                            .isChargeAttackSkip(isChargeAttackSkip)
                            .isEnemy(isEnemy)
                            .isMainCharacter(isMainCharacter)
                            .startMotion(startMotion)
                            .build();
                }
        ).toList();

        assetInfos.forEach(assetInfo -> log.info("assertInfo = {}", assetInfo));
        log.info("sortedActorIds = {}", sortedActorIds);
        model.addAttribute("assetInfos", assetInfos);

        // 멤버 추가
        model.addAttribute("member", findMember);

        model.asMap().entrySet().forEach(entry -> {
            log.info("k = {}", entry.getKey());
            log.info("v = {}", entry.getValue());
        });


        return "battle-canvas";
    }

    private static BattleResponse toBattleResponse(ActorLogicResult result, List<BattleActor> allActors) {
        return BattleResponse.builder()
                .charOrder(result.getMainBattleActorOrder())
                .moveType(result.getMoveType())
                .motion(result.getMotionType().getMotion())
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
                                                        .type(battleStatus.getStatusType().name())
                                                        .name(battleStatus.getName())
                                                        .imageSrc(battleStatus.getIconSrc())
                                                        .effectText(battleStatus.getEffectText())
                                                        .statusText(battleStatus.getStatusText())
                                                        .duration(battleStatus.getDuration())
                                                        .build()
                                        ).toList()
                        ).toList())
                .removedBattleStatusesList(result.getRemovedBattleStatusesList().stream()
                        .map(battleStatuses ->
                                battleStatuses.isEmpty() ? new ArrayList<StatusDto>() : battleStatuses.stream()
                                        .map(battleStatus ->
                                                StatusDto.builder()
                                                        .type(battleStatus.getStatusType().name())
                                                        .name(battleStatus.getName())
                                                        .imageSrc(battleStatus.getIconSrc())
                                                        .effectText(battleStatus.getEffectText())
                                                        .statusText(battleStatus.getStatusText())
                                                        .duration(battleStatus.getDuration())
                                                        .build()
                                        ).toList()
                        ).toList())
                .currentBattleStatusesList(allActors.stream()
                        .map(BattleActor::getBattleStatuses)
                        .map(battleStatuses -> battleStatuses.stream()
                                .sorted(Comparator.comparing(BattleStatus::getUpdatedAt).reversed())
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


}
