package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.info.AssetInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.CharacterBattleInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.EnemyInfo;
import com.gbf.granblue_simulator.battle.controller.dto.info.MoveInfo;
import com.gbf.granblue_simulator.battle.controller.dto.request.GuardRequest;
import com.gbf.granblue_simulator.battle.controller.dto.request.MoveRequest;
import com.gbf.granblue_simulator.battle.controller.dto.request.ToggleChargeAttackRequest;
import com.gbf.granblue_simulator.battle.controller.dto.request.UsePotionRequest;
import com.gbf.granblue_simulator.battle.controller.dto.response.BattleResponse;
import com.gbf.granblue_simulator.battle.controller.dto.response.GuardResponse;
import com.gbf.granblue_simulator.battle.controller.dto.response.PotionResponse;
import com.gbf.granblue_simulator.battle.controller.dto.response.ToggleChargeAttackResponse;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.PotionType;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.PotionResult;
import com.gbf.granblue_simulator.battle.service.BattleCommandRequest;
import com.gbf.granblue_simulator.battle.service.BattleCommandService;
import com.gbf.granblue_simulator.battle.service.MemberService;
import com.gbf.granblue_simulator.battle.service.StatusService;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.metadata.repository.BaseEnemyRepository;
import com.gbf.granblue_simulator.metadata.service.BaseEnemyService;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.gbf.granblue_simulator.battle.controller.BattleInfoMapper.*;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/rooms/{roomId}/members/me")
public class BattleCommandController {

    private final MemberService memberService;

    private final BattleContext battleContext;

    private final BattleCommandService battleCommandService;

    private final BattleResponseMapper responseMapper;
    private final BaseEnemyService baseEnemyService;

    private final BaseMoveService baseMoveService;
    private final StatusService statusService;

    @PostMapping("/sync")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> postSync(@RequestBody MoveRequest moveRequest,
                                                        @PathVariable Long roomId,
                                                        @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postSync] request = {}", moveRequest);

        Member member = memberService.findByRoomIdAndUserId(roomId, principalDetails.getUser().getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));

        Map<String, Object> result = new HashMap<>();

        List<BattleResponse> syncResponses = new ArrayList<>();
        if (member.isBattleStarted()) {
            // 일반 동기화
            List<MoveLogicResult> syncResults = battleCommandService.sync(BattleCommandRequest.from(member));
            syncResponses.addAll(responseMapper.toBattleResponse(syncResults));
        } else {
            // 첫입장
            List<MoveLogicResult> battleStartResults = battleCommandService.startBattle(BattleCommandRequest.from(member));
            syncResponses.addAll(responseMapper.toBattleResponse(battleStartResults));
        }

        result.put("syncResponses", syncResponses);
        log.debug("syncResponses: \n{}", syncResponses.stream().map(BattleResponse::toString).collect(Collectors.joining("\n")));

        return ResponseEntity.ok(result);
    }


    @PostMapping("/ability")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> postAbility(@RequestBody MoveRequest moveRequest,
                                                            @PathVariable Long roomId,
                                                            @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postAbility] moveRequest: {}", moveRequest);

        Member member = memberService.findByRoomIdAndUserId(roomId, principalDetails.getUser().getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));

        long characterId = moveRequest.getCharacterId();
        long moveId = moveRequest.getMoveId();
        List<MoveLogicResult> results = battleCommandService.ability(BattleCommandRequest.builder()
                .memberId(member.getId())
                .mainActorId(characterId)
                .commandMoveId(moveId)
                .build());

        List<BattleResponse> responses = responseMapper.toBattleResponse(results);
        responses.forEach(response -> log.debug("[postAbility] response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/fatal-chain")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> postFatalChain(@RequestBody MoveRequest moveRequest,
                                                               @PathVariable Long roomId,
                                                               @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postFatalChain] moveRequest: {}", moveRequest);

        Member member = memberService.findByRoomIdAndUserId(roomId, principalDetails.getUser().getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));

        long characterId = moveRequest.getCharacterId();
        List<MoveLogicResult> results = battleCommandService.fatalChain(BattleCommandRequest.of(member.getId(), characterId));

        List<BattleResponse> responses = responseMapper.toBattleResponse(results);
        // responses.forEach(response -> log.info("[postFatalChain] response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/summon")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> postSummon(@RequestBody MoveRequest request,
                                                           @PathVariable Long roomId,
                                                           @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postSummon] request =  {}", request);
        Member member = memberService.findByRoomIdAndUserId(roomId, principalDetails.getUser().getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));

        Long summonId = request.getMoveId();
        List<MoveLogicResult> results = battleCommandService.summon(BattleCommandRequest.builder()
                .memberId(member.getId())
                .summonId(summonId)
                .isUnionSummon(request.isDoUnionSummon())
                .build());

        List<BattleResponse> responses = responseMapper.toBattleResponse(results);

        // responses.forEach(response -> log.info("[postSummon] response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/turn-progress")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> postTurnProgress(@PathVariable Long roomId,
                                                                 @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postTurnProgress] roomId = {}", roomId);

        Member member = memberService.findByRoomIdAndUserId(roomId, principalDetails.getUser().getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        List<MoveLogicResult> turnProgressResults = battleCommandService.progressTurn(BattleCommandRequest.of(member.getId()));

        List<BattleResponse> responses = responseMapper.toBattleResponse(turnProgressResults);

         responses.forEach(response -> log.info("response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/guard")
    @ResponseBody
    public ResponseEntity<GuardResponse> postGuard(@RequestBody GuardRequest guardRequest,
                                                   @PathVariable Long roomId,
                                                   @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postGuard] request = {}", guardRequest);

        Member member = memberService.findByRoomIdAndUserId(roomId, principalDetails.getUser().getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        long characterId = guardRequest.getCharacterId();

        List<Boolean> guardStates = battleCommandService.guard(member, characterId, guardRequest.getTargetType());

        boolean guardActivated = battleContext.getMainActor().isGuardOn(); // 메인 캐릭터 가드여부 따로 전달
        GuardResponse guardResponse = GuardResponse.builder()
                .isGuardActivated(guardActivated)
                .guardStates(guardStates)
                .build();
        return ResponseEntity.ok(guardResponse);
    }

    @PostMapping("/toggle-charge-attack")
    @ResponseBody
    public ResponseEntity<ToggleChargeAttackResponse> postToggleChargeAttack(@RequestBody ToggleChargeAttackRequest request,
                                                                             @PathVariable Long roomId,
                                                                             @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postToggleChargeAttack] request = {}", request);

        Member member = memberService.findByRoomIdAndUserId(roomId, principalDetails.getUser().getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));

        List<Boolean> canChargeAttacks = battleCommandService.toggleChargeAttack(member, request.isChargeAttackOn());

        ToggleChargeAttackResponse response = ToggleChargeAttackResponse.builder()
                .chargeAttackOn(member.isChargeAttackOn())
                .canChargeAttacks(canChargeAttacks)
                .build();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/use-potion")
    @ResponseBody
    public ResponseEntity<PotionResponse> postUsePotion(@RequestBody UsePotionRequest request,
                                                        @PathVariable Long roomId,
                                                        @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postUsePotion] request = {}", request);

        Member member = memberService.findByRoomIdAndUserId(roomId, principalDetails.getUser().getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        PotionType potionType = request.getPotionType();

        PotionResult potionResult = battleCommandService.potion(member.getId(), potionType, request.getTargetActorId());

        AssetInfo assetInfo = null;
        CharacterBattleInfo characterInfo = null;
        Actor revivedActor = potionResult.getRevivedActor();
        Integer actorIndex = null;
        if (revivedActor != null) {
            // 사망한 캐릭터 복구용 정보
            assetInfo = toAssetInfo(List.of(revivedActor), List.of()).getFirst();
            characterInfo = toCharacterInfo(revivedActor);
            actorIndex = revivedActor.getCurrentOrder();
        }

        PotionResponse potionResponse = PotionResponse.builder()
                .heals(potionResult.getHeals())
                .hps(potionResult.getHps())
                .hpRates(potionResult.getHpRates())
                .potionCount(potionResult.getPotionCount())
                .allPotionCount(potionResult.getAllPotionCount())
                .elixirCount(potionResult.getElixirCount())
                .actorIndex(actorIndex)
                .characterInfo(characterInfo)
                .assetInfo(assetInfo)
                .build();
        return ResponseEntity.ok(potionResponse);
    }

    @GetMapping("/battle-init")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getInitData(@PathVariable Long roomId,
                                                           @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[getInitData] roomId = {}", roomId);

        Member member = memberService.findByRoomIdAndUserId(roomId, principalDetails.getId()).orElseThrow(() -> new IllegalArgumentException("잘못된 멤버 요청입니다."));

        battleContext.init(member, null);
        statusService.initStatusForRead(battleContext.getAllActors());

        Map<String, Object> result = new HashMap<>();

        List<Actor> allCharacters = battleContext.getAllCharacters();
        Map<Integer, CharacterBattleInfo> characterInfo = allCharacters.stream()
                .map(BattleInfoMapper::toCharacterInfo)
                .collect(Collectors.toMap(CharacterBattleInfo::getOrder, Function.identity()));
        result.put("characterInfo", characterInfo); // key: currentOrder
        result.put("aliveCharacterOrders", battleContext.getFrontCharacters().stream().map(Actor::getCurrentOrder).toList());

        Actor leaderCharacter = battleContext.getLeaderCharacter();
        Long leaderActorId = !leaderCharacter.isAlreadyDead() ? leaderCharacter.getId() : null;
        result.put("leaderActorId", leaderActorId);

        // 변화 move (메타데이터만, 별도조회 필요해 일단 분리)
        List<Long> changingMoveIds = allCharacters.stream()
                .flatMap(partyMember -> partyMember.getBaseActor().getMappedMove().getChangingMoveIds().stream())
                .toList();
        List<BaseMove> baseMoves = baseMoveService.findAllByIds(changingMoveIds);
        result.put("changingMoves", baseMoves.stream().map(MoveInfo::from).toList());

        // 적 인포
        Enemy enemy = (Enemy) battleContext.getEnemy();
        EnemyInfo enemyInfo = toEnemyInfo(enemy);
        result.put("enemyInfo", enemyInfo);

        // 적 hp 트리거
        BaseEnemy baseEnemy = (BaseEnemy) enemy.getBaseActor();
        String enemyRootNameEn = baseEnemy.getRootNameEn();
        List<BaseEnemy> baseEnemies = baseEnemyService.findByRootNameEn(enemyRootNameEn);
        List<Integer> triggerHps = baseEnemies.stream()
                .flatMap(base -> base.getOmens().values().stream())
                .flatMap(baseOmen -> baseOmen.getOmenType() == OmenType.HP_TRIGGER
                        // 현재 hp 트리거 발생중이라면 latestTriggerHp 를 포함, 아니면 제거
                        ? baseOmen.getTriggerHps().stream()
                          .filter(hp ->
                                  enemy.getOmen() != null && enemy.getOmen().getBaseOmen().getOmenType() == OmenType.HP_TRIGGER
                                  ? hp <= enemy.getLatestTriggeredHp()
                                  : hp < enemy.getLatestTriggeredHp())
                        : Stream.empty())
                .distinct().sorted()
                .toList();
        result.put("triggerHps", triggerHps);

        // 소환석 인포
        List<Move> summonMoves = leaderCharacter.getSummons();
        List<MoveInfo> summonInfos = summonMoves.stream().map(MoveInfo::from).toList();
        result.put("summonInfos", summonInfos);

        // 페이탈 체인 인포
        Long fatalChainMoveId = member.getFatalChainMoveId();
        BaseMove fatalChainMove = baseMoveService.findById(fatalChainMoveId).orElseThrow(() -> new IllegalArgumentException("페이탈 체인 없음"));
        MoveInfo fatalChainInfo = MoveInfo.from(fatalChainMove);
        result.put("fatalChainInfo", fatalChainInfo);

        // 페이탈 체인 게이지
        result.put("fatalChainGauge", member.getFatalChainGauge());

        // 캐릭터 + 적 에셋 (소환석, 펭탈 체인 포함) AssetInfo.Asset 으로 변환
        List<AssetInfo> assetInfos = toAssetInfo(battleContext.getCurrentFieldActors(), summonMoves);
        result.put("assetInfos", assetInfos);
        assetInfos.forEach(assetInfo -> log.info("assetInfo = {}", assetInfo));

        // 파티 초기상태로 정렬된 모든 캐릭터 id
        User user = member.getUser();
        List<Long> baseCharacterIdsOrderByParty = user.getAvailableParty().stream().filter(party -> party.getId().equals(member.getPartyId()))
                .flatMap(party -> party.getUserCharacterIds().stream().map(userCharacterId -> user.getUserCharacters().get(userCharacterId).getBaseCharacter().getId()))
                .toList();
        List<Long> allCharacterIdsOrderByParty = new ArrayList<>();
        baseCharacterIdsOrderByParty.forEach(baseCharacterId ->
                battleContext.getAllCharacters().stream().filter(character -> character.getBaseActor().getId().equals(baseCharacterId))
                        .findAny().ifPresent(actor -> allCharacterIdsOrderByParty.add(actor.getId()))
        );
        result.put("allCharacterIds", allCharacterIdsOrderByParty);

        // 기타
        result.put("currentTurn", member.getCurrentTurn());
        result.put("startTime", member.getRoom().getCreatedAt());

        result.put("usedSummon", member.usedSummon());

        result.put("isTutorial", member.getRoom().getRoomStatus() == RoomStatus.TUTORIAL);

        result.put("lastMoveTime", member.getLastMoveTime());
        result.put("moveCooldown", member.getMoveCooldown());

        return ResponseEntity.ok(result);
    }

}
