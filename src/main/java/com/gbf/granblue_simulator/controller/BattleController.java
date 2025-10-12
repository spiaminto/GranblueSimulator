package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.auth.PrincipalDetails;
import com.gbf.granblue_simulator.controller.request.battle.*;
import com.gbf.granblue_simulator.controller.response.battle.*;
import com.gbf.granblue_simulator.controller.response.info.battle.*;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.actor.Party;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.asset.Asset;
import com.gbf.granblue_simulator.domain.move.MotionType;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.actor.battle.BattleContext;
import com.gbf.granblue_simulator.logic.BattleLogic;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.GuardResult;
import com.gbf.granblue_simulator.logic.common.dto.PotionResult;
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
import java.util.function.Function;
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

    private final BattleContext battleContext;


    @GetMapping("/api/enemy-src")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEnemySrcMap(@RequestParam Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        battleContext.init(member, null);
        BattleActor enemy = battleContext.getEnemy();

        Map<String, Object> result = new HashMap<>();
        List<Asset> enemyAssets = assetRepository.findAllByActorId(enemy.getActor().getId());
        List<AssetInfo.Asset> assetInfoAsset = toAssetInfoAsset(0L, "", enemyAssets, new ArrayList<>(), null);
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

    @PostMapping("/api/sync")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> requestSync(@RequestBody MoveRequest moveRequest,
                                                            @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("moveRequest: {}", moveRequest);
        long memberId = moveRequest.getMemberId();
        Member findMember = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
//        if (!Objects.equals(findMember.getUser().getId(), principalDetails.getId())) throw new IllegalArgumentException("잘못된 요청");

        battleContext.init(findMember, null);
        ActorLogicResult syncResult = battleLogic.syncBattle(findMember);
        BattleResponse syncResponse = toBattleResponse(syncResult);

        return ResponseEntity.ok(List.of(syncResponse));
    }


    @PostMapping("/api/move")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> move(@RequestBody MoveRequest moveRequest,
                                                     @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("moveRequest: {}", moveRequest);

        long characterId = moveRequest.getCharacterId();
        long memberId = moveRequest.getMemberId();
        long moveId = moveRequest.getMoveId();
        Member findMember = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
//        if (!Objects.equals(findMember.getUser().getId(), principalDetails.getId())) throw new IllegalArgumentException("잘못된 요청");

        battleContext.init(findMember, characterId);
        List<ActorLogicResult> results = battleLogic.processMove(battleContext, moveId);

        List<BattleResponse> responses = results.stream().map(BattleController::toBattleResponse).toList();

        // 소환석 결과 후처리
        responses.stream().filter(response -> response.getMoveType() == MoveType.SUMMON_DEFAULT)
                .forEach(summonResponse -> {
                    List<Long> summonIds = summonResponse.getSummonIds();
                    if (summonIds.isEmpty()) return;
                    Asset summonAsset = assetRepository.findByMoveId(summonIds.getFirst()).getFirst();

                    List<String> summonCjsNames = new ArrayList<>();
                    summonCjsNames.add(summonAsset.getRootCjsName());
                    String summonMoveName = summonAsset.getRootName(); // 제우스

                    if (summonIds.size() > 1) {
                        Asset unionSummonAsset = assetRepository.findByMoveId(summonIds.get(1)).getFirst();
                        summonCjsNames.add(unionSummonAsset.getRootCjsName());
                        summonMoveName = summonMoveName + " + " + unionSummonAsset.getRootName();
                    }

                    //CHECK 나중에 분리해야할듯...
                    summonResponse.setSummonCjsNames(summonCjsNames);
                    summonResponse.setMoveName(summonMoveName);
                });

        responses.forEach(response -> log.info("response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/api/turn-progress")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> turnProgress(@RequestBody TurnProgressRequest turnProgressRequest) {
        log.info("turnProgressRequest: {}", turnProgressRequest);
        long memberId = turnProgressRequest.getMemberId();
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("멤버가 없음"));

        battleContext.init(member, null);
        List<ActorLogicResult> turnProgressResults = battleLogic.progressTurn(battleContext);

        List<BattleResponse> responses = turnProgressResults.stream().map(BattleController::toBattleResponse).toList();

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

        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        battleContext.init(member, characterId);

        List<GuardResult> guardResults = battleLogic.processGuard(battleContext, guardRequest.getTargetType());

        boolean guardActivated = battleContext.getMainCharacter().isGuardOn(); // 메인 캐릭터 가드여부 따로 전달
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

    @PostMapping("/api/use-potion")
    @ResponseBody
    public ResponseEntity<PotionResponse> requestUsePotion(@RequestBody UsePotionRequest request,
                                                           @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("usePotionRequest = {}", request);

        // TODO 검증
        Member member = memberRepository.findById(request.getMemberId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        Long mainActorId = request.getActorId();
        battleContext.init(member, mainActorId);
        PotionResult potionResult = battleLogic.processPotion(battleContext, request.getTargetType());

        PotionResponse potionResponse = PotionResponse.builder()
                .heals(potionResult.getHeals())
                .potionCount(potionResult.getPotionCount())
                .allPotionCount(potionResult.getAllPotionCount())
                .build();
        return ResponseEntity.ok(potionResponse);
    }

    @GetMapping("/room/{roomId}")
    @Transactional
    public String getRoom(@PathVariable Long roomId,
                          @AuthenticationPrincipal PrincipalDetails principal, Model model) {

        Member findMember = memberRepository.findByRoomIdAndUserId(roomId, principal.getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        model.addAttribute("member", findMember);
        battleContext.init(findMember, null);

        setInfoAttributes(model, findMember);

        model.asMap().entrySet().forEach(entry -> {
            log.info("k = {}", entry.getKey());
            log.info("v = {}", entry.getValue());
        });

        return "battle-canvas";
    }

    /**
     * 테스트 페이지용 메서드
     */
    @GetMapping("/battle")
    @Transactional
    public String battlePage(Model model) {

        Member findMember = memberRepository.findByRoomIdAndUserId(147L, 1L).orElseThrow(() -> new IllegalArgumentException("멤버를 찾을수 없음"));
        model.addAttribute("member", findMember);
        battleContext.init(findMember, null);

        // model 에 정보추가
        setInfoAttributes(model, findMember);

        model.asMap().entrySet().forEach(entry -> {
            log.info("k = {}", entry.getKey());
            log.info("v = {}", entry.getValue());
        });

        return "battle-canvas";
    }

    public void setInfoAttributes(Model model, Member member) {
        List<BattleActor> partyMembers = battleContext.getFrontCharacters();
        log.info("frontCharacters = {}", partyMembers);
        BattleActor enemyActor = battleContext.getEnemy();
        log.info("enemy = {}", enemyActor);
        BattleActor leaderCharacter = battleContext.getLeaderCharacter();
        List<BattleActor> currentFieldActors = battleContext.getCurrentFieldActors();

        Party party = partyRepository.findById(member.getPartyId()).orElseThrow(() -> new IllegalArgumentException("파티가 지정되있지 않습니다."));

        // 캐릭터 인포
        Map<Integer, BattleCharacterInfo> battleCharacterInfoMap = partyMembers.stream()
                .map(BattleInfoMapper::toCharacterInfo)
                .collect(Collectors.toMap(BattleCharacterInfo::getOrder, Function.identity()));
        model.addAttribute("battleCharacterInfoMap", battleCharacterInfoMap);

        // 적 인포
        BattleEnemy enemy = (BattleEnemy) enemyActor;
        BattleEnemyInfo battleEnemyInfo = toEnemyInfo(enemy);
        model.addAttribute("battleEnemyInfo", battleEnemyInfo);

        // 소환석 인포
        List<Long> summonMoveIds = leaderCharacter.getSummonMoveIds();
        List<Move> summonMoves = moveRepository.findAllById(summonMoveIds);
        List<SummonInfo> summonInfos = summonMoves.stream().map(move -> toSummonInfo(move, leaderCharacter)).toList();
        model.addAttribute("summonInfos", summonInfos);

        // 페이탈 체인 인포
        Long fatalChainMoveId = leaderCharacter.getFatalChainMoveId();
        FatalChainInfo fatalChainInfo = moveRepository.findById(fatalChainMoveId).map(move -> toFatalChainInfo(leaderCharacter, move)).orElseGet(() -> null);
        model.addAttribute("fatalChainInfo", fatalChainInfo);

        // 캐릭터 + 적 에셋
        List<Long> assetIds = partyMembers.stream()
                .map(partyMember -> party.getActorIds().indexOf(partyMember.getActor().getId())) // 파티에서 현재 프론트 멤버의 id 기준 인덱스 구해서
                .map(index -> party.getActorAssetIds().get(index))// 해당 인덱스에 대응하는 assetId 를 반환 CHECK actorIds 와 actorAssetIds 는 반드시 순서가 동일해야함
                .toList();
        List<Asset> actorAssets = assetRepository.findWithChildrenByAssetId(assetIds); // CHECK 여기서 IN절 조회로 순서섞임, 캐릭터의 무브별로 분리되어있으므로, AssetInfo 로 변환후 정렬
        List<Asset> enemyAsset = assetRepository.findAllByActorId(enemyActor.getActor().getId());
        actorAssets.addAll(enemyAsset);
        // 솬석, 페이탈체인 에셋
        List<Asset> summonAssets = assetRepository.findAllByMoveIdIn(party.getSummonIds());
        Asset fatalChainAsset = assetRepository.findByMoveId(leaderCharacter.getFatalChainMoveId()).getFirst();

        // AssetInfo.Asset 으로 변환
        List<AssetInfo.Asset> assetInfoAssets = toAssetInfoAsset(leaderCharacter.getActor().getId(), leaderCharacter.getActor().getWeaponId(), actorAssets, summonAssets, fatalChainAsset);
        // BattleActor.currentOrder 을 매핑해주기 위한 맵
        Map<Long, Integer> actorIdByCurrentOrderMap = currentFieldActors.stream().collect(Collectors.toMap(battleActor -> battleActor.getActor().getId(), BattleActor::getCurrentOrder));

        // 에셋 기타 상태
        Move enemyStartMove = enemy.getCurrentStandbyType() != null ? enemy.getMove(enemy.getCurrentStandbyType()) : enemy.getMove(MoveType.IDLE_DEFAULT);
        boolean isChargeAttackSkip = leaderCharacter.getMember().isChargeAttackSkip();

        // AssetInfo 조합
        List<AssetInfo> assetInfos = assetInfoAssets.stream().map(asset -> {
                    boolean isEnemy = enemyActor.getActor().getId().equals(asset.getActorId());
                    boolean isMainCharacter = leaderCharacter.getActor().getId().equals(asset.getActorId());
                    MotionType startMotion = isEnemy ? enemyStartMove.getMotionType() : MotionType.STB_WAIT;
                    int currentOrder = actorIdByCurrentOrderMap.get(asset.getActorId());
                    return AssetInfo.builder()
                            .currentOrder(currentOrder)
                            .asset(asset)
                            .isChargeAttackSkip(isChargeAttackSkip)
                            .isEnemy(isEnemy)
                            .isMainCharacter(isMainCharacter)
                            .startMotion(startMotion)
                            .build();
                }
        ).toList();

        assetInfos.forEach(assetInfo -> log.info("assertInfo = {}", assetInfo));
        model.addAttribute("assetInfos", assetInfos);

    }

    private static BattleResponse toBattleResponse(ActorLogicResult result) {
        return BattleResponse.builder()
                .charOrder(result.getMainBattleActorOrder())
                .moveType(result.getMoveType())
                .moveName(result.getMoveName())
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
                .summonIds(result.getSummonIds())
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
                .currentBattleStatusesList(result.getCurrentBattleStatusesList().stream()
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
                .enemyAttackTargetOrders(result.getEnemyAttackTargetOrders())
                .abilityCoolDowns(result.getAbilityCooldowns())
                .isEnemyPowerUp(result.isEnemyPowerUp())
                .isEnemyCtMax(result.isEnemyCtMax())
                .build();
    }


}
