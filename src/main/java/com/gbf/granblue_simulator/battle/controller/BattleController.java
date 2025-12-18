package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.response.*;
import com.gbf.granblue_simulator.battle.repository.RoomRepository;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.repository.BaseEnemyRepository;
import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import com.gbf.granblue_simulator.battle.controller.dto.info.*;
import com.gbf.granblue_simulator.battle.controller.dto.request.*;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.metadata.domain.asset.Asset;
import com.gbf.granblue_simulator.metadata.domain.move.MotionType;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.PotionResult;
import com.gbf.granblue_simulator.metadata.repository.AssetRepository;
import com.gbf.granblue_simulator.battle.repository.MemberRepository;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.metadata.repository.MoveRepository;
import com.gbf.granblue_simulator.battle.service.BattleCommandService;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import com.gbf.granblue_simulator.battle.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.gbf.granblue_simulator.battle.controller.BattleInfoMapper.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BattleController {

    private final MemberRepository memberRepository;
    private final MoveRepository moveRepository;

    private final MemberService memberService;
    private final PartyRepository partyRepository;
    private final AssetRepository assetRepository;

    private final BattleContext battleContext;
    private final BattleLogService battleLogService;
    private final BattleCommandService battleCommandService;

    private final BattleResponseMapper responseMapper;
    private final BaseEnemyRepository baseEnemyRepository;
    private final RoomRepository roomRepository;

    @GetMapping("/room/{roomId}")
    @Transactional
    public String getRoom(@PathVariable Long roomId,
                          @AuthenticationPrincipal PrincipalDetails principal, Model model) {

        Member findMember = memberRepository.findByRoomIdAndUserId(roomId, principal.getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        model.addAttribute("member", findMember);
        battleContext.init(findMember, null);

        setInfoAttributes(model, findMember);

        model.asMap().entrySet().forEach(entry -> {
//            log.info("k = {}", entry.getKey());
//            log.info("v = {}", entry.getValue());
        });

        return "battle/battle";
    }

    @GetMapping("/room/{roomId}/result")
    @Transactional
    public String getRoomResult(@PathVariable Long roomId,
                                @AuthenticationPrincipal PrincipalDetails principal, Model model) {

//        Member findMember = memberRepository.findByRoomIdAndUserId(roomId, principal.getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        // CHECK 나중에 수정
        Member findMember = memberRepository.findByRoomIdAndUserId(167L, 1L).orElseThrow(() -> new IllegalArgumentException("멤버를 찾을수 없음"));

        model.addAttribute("member", findMember);
        Room room = findMember.getRoom();
        Actor enemy = findMember.getActors().stream().filter(Actor::isEnemy).findFirst().orElseThrow(() -> new IllegalArgumentException("적 없음"));

        String formattedEndedAt = room.getEndedAt().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.KOREA));
        BattleResultInfo resultInfo = BattleResultInfo.builder()
                .enemyName(enemy.getName())
                .enemyPortraitSrc(enemy.getBaseActor().getBattlePortraitSrc())
                .endedAt(formattedEndedAt)
                .enterUserCount(room.getEnterUserCount())
                .build();
        model.addAttribute("resultInfo", resultInfo);

        Duration totalDuration = Duration.between(room.getCreatedAt(), room.getEndedAt());
        int minutesPart = totalDuration.toMinutesPart();
        int secondsPart = totalDuration.toSecondsPart();
        String totalTime = String.format("%02d:%02d", minutesPart, secondsPart);

        List<BattleResultMemberInfo> resultMemberInfos = room.getMembers().stream()
                .map(member -> {
                            int totalDamage = battleLogService.getEnemyTakenDamageSumByMember(member, enemy);
                            return BattleResultMemberInfo.builder()
                                    .username(member.getUser().getUsername())
                                    .enemyHp(String.format("%,d", enemy.getMaxHp()))
                                    .totalTurns(member.getCurrentTurn())
                                    .totalTime(totalTime)
                                    .totalDamage(String.format("%,d", totalDamage))
                                    .totalDamageRate((int) ((double) totalDamage / enemy.getMaxHp() * 100 * 100) / 100.0)
                                    .totalHonor(String.format("%,d", member.getHonor()))
                                    .build();
                        }
                ).sorted(Comparator.comparing(BattleResultMemberInfo::getTotalDamage).reversed())
                .toList();
        model.addAttribute("memberInfos", resultMemberInfos);

        String findUsername = findMember.getUser().getUsername();
        BattleResultMemberInfo myInfo = resultMemberInfos.stream().filter(memberInfo -> memberInfo.getUsername().equals(findUsername)).findFirst().orElseThrow(() -> new IllegalArgumentException("there are no findMember.getUsername, username = " + findUsername));
        model.addAttribute("myInfo", myInfo);

        return "battle/result";
    }

    @GetMapping("/api/enemy-src")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEnemySrcMap(@RequestParam Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        battleContext.init(member, null);
        Actor enemy = battleContext.getEnemy();

        Map<String, Object> result = new HashMap<>();
        List<Asset> enemyAssets = assetRepository.findAllByActorId(enemy.getBaseActor().getId());
        List<AssetInfo.Asset> assetInfoAsset = toAssetInfoAsset(0L, "", enemyAssets, new ArrayList<>(), null);
        AssetInfo enemyAssetInfo = AssetInfo.builder()
                .asset(assetInfoAsset.getFirst())
                .startMotion(MotionType.WAIT)
                .isChargeAttackSkip(false)
                .isLeaderCharacter(false)
                .isEnemy(true)
                .build();
        result.put("assetInfo", enemyAssetInfo);
        result.put("actorName", enemy.getName());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/room/{roomId}/members")
    @ResponseBody
    public ResponseEntity<List<MemberResponse>> getMembers(@PathVariable Long roomId) {
        log.info("roomId = {}", roomId);

        // 기본적으로 멤버정보는 에러나도 진행에 문제는 없으므로 Exception throw 하지 않음
        List<MemberResponse> memberResponses = roomRepository.findById(roomId).map(room ->
                room.getMembers().stream()
                        .map(member -> {
                            Actor leaderActor = member.getActors().stream()
                                    .filter(actor -> actor.getBaseActor().isLeaderCharacter())
                                    .findAny()
                                    .orElseGet(() -> Actor.getTransientCharacter(member));
                            return MemberResponse.builder()
                                    .username(member.getUser().getUsername())
                                    .leaderActorName(leaderActor.getName())
                                    .leaderActorElementType(leaderActor.getElementType().name())
                                    .honor(member.getHonor())
                                    .build();
                        })
                        .sorted(Comparator.comparing(MemberResponse::getHonor).reversed())
                        .toList()
        ).orElseGet(ArrayList::new);

        return ResponseEntity.ok(memberResponses);
    }


    @PostMapping("/api/sync")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> postSync(@RequestBody MoveRequest moveRequest,
                                                         @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postSync] moveRequest: {}", moveRequest);
        long memberId = moveRequest.getMemberId();
        Member findMember = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
//        if (!Objects.equals(findMember.getUser().getId(), principalDetails.getId())) throw new IllegalArgumentException("잘못된 요청");

        battleContext.init(findMember, null);
        ActorLogicResult syncResult = battleCommandService.sync();
        List<BattleResponse> syncResponse = responseMapper.toBattleResponse(List.of(syncResult));

        log.info("syncResponse: {}", syncResponse);

        return ResponseEntity.ok(syncResponse);
    }


    @PostMapping("/api/ability")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> postAbility(@RequestBody MoveRequest moveRequest,
                                                            @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postAbility] moveRequest: {}", moveRequest);

        long characterId = moveRequest.getCharacterId();
        long memberId = moveRequest.getMemberId();
        long moveId = moveRequest.getMoveId();
        Member findMember = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
//        if (!Objects.equals(findMember.getUser().getId(), principalDetails.getId())) throw new IllegalArgumentException("잘못된 요청");

        battleContext.init(findMember, characterId);
        List<ActorLogicResult> results = battleCommandService.ability(moveId);

        List<BattleResponse> responses = responseMapper.toBattleResponse(results);
        responses.forEach(response -> log.info("[postAbility] response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/api/fatal-chain")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> postFatalChain(@RequestBody MoveRequest moveRequest,
                                                               @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postFatalChain] moveRequest: {}", moveRequest);

        long characterId = moveRequest.getCharacterId();
        long memberId = moveRequest.getMemberId();
        Member findMember = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
//        if (!Objects.equals(findMember.getUser().getId(), principalDetails.getId())) throw new IllegalArgumentException("잘못된 요청");

        battleContext.init(findMember, characterId);
        List<ActorLogicResult> results = battleCommandService.fatalChain();

        List<BattleResponse> responses = responseMapper.toBattleResponse(results);
        responses.forEach(response -> log.info("[postFatalChain] response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/api/summon")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> postSummon(@RequestBody MoveRequest request,
                                                           @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("[postSummon] summonRequest: {}", request);

        Long memberId = request.getMemberId();
        Long summonId = request.getMoveId();
        Member findMember = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
//        if (!Objects.equals(findMember.getUser().getId(), principalDetails.getId())) throw new IllegalArgumentException("잘못된 요청");

        Actor leaderActor = findMember.getActors().stream().filter(actor -> actor.getBaseActor().isLeaderCharacter()).findFirst().orElseThrow(() -> new MoveValidationException("주인공 캐릭터가 없음"));

        battleContext.init(findMember, leaderActor.getId());
        List<ActorLogicResult> results = battleCommandService.summon(summonId, request.isDoUnionSummon());

        List<BattleResponse> responses = responseMapper.toBattleResponse(results);

        responses.forEach(response -> log.info("[postSummon] response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/api/turn-progress")
    @ResponseBody
    public ResponseEntity<List<BattleResponse>> postTurnProgress(@RequestBody TurnProgressRequest turnProgressRequest) {
        log.info("turnProgressRequest: {}", turnProgressRequest);
        long memberId = turnProgressRequest.getMemberId();
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("멤버가 없음"));

        battleContext.init(member, null);
        List<ActorLogicResult> turnProgressResults = battleCommandService.progressTurn();

        List<BattleResponse> responses = responseMapper.toBattleResponse(turnProgressResults);

        responses.forEach(response -> log.info("response: {}", response));

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/api/guard")
    @ResponseBody
    public ResponseEntity<GuardResponse> postGuard(@RequestBody GuardRequest guardRequest,
                                                   @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("guard request: {}", guardRequest);
        long memberId = guardRequest.getMemberId();
        long characterId = guardRequest.getCharacterId();

        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        battleContext.init(member, characterId);

        List<Boolean> guardStates = battleCommandService.guard(guardRequest.getTargetType());

        boolean guardActivated = battleContext.getMainActor().isGuardOn(); // 메인 캐릭터 가드여부 따로 전달
        GuardResponse guardResponse = GuardResponse.builder()
                .isGuardActivated(guardActivated)
                .guardStates(guardStates)
                .build();
        return ResponseEntity.ok(guardResponse);
    }

    @PostMapping("/api/toggle-charge-attack")
    @ResponseBody
    public ResponseEntity<ToggleChargeAttackResponse> postToggleChargeAttack(@RequestBody ToggleChargeAttackRequest request,
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
    public ResponseEntity<PotionResponse> postUsePotion(@RequestBody UsePotionRequest request,
                                                        @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("usePotionRequest = {}", request);

        // TODO 검증
        Member member = memberRepository.findById(request.getMemberId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        Long mainActorId = request.getActorId();
        String potionType = request.getPotionType();
        StatusEffectTargetType potionTargetType = potionType.equals("single")
                ? StatusEffectTargetType.SELF
                : potionType.equals("all")
                ? StatusEffectTargetType.PARTY_MEMBERS
                : null;
        if (potionTargetType == null) ResponseEntity.badRequest().build();
        battleContext.init(member, mainActorId);

        PotionResult potionResult = battleCommandService.potion(potionTargetType);

        PotionResponse potionResponse = PotionResponse.builder()
                .heals(potionResult.getHeals())
                .hps(potionResult.getHps())
                .hpRates(potionResult.getHpRates())
                .potionCount(potionResult.getPotionCount())
                .allPotionCount(potionResult.getAllPotionCount())
                .build();
        return ResponseEntity.ok(potionResponse);
    }

    /**
     * 초기 SSR 시 필요한 정보 model 에 set
     *
     * @param model
     * @param member
     */
    protected void setInfoAttributes(Model model, Member member) {
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        log.info("frontCharacters = {}", partyMembers);
        Actor enemyActor = battleContext.getEnemy();
        log.info("enemy = {}", enemyActor);
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        List<Actor> currentFieldActors = battleContext.getCurrentFieldActors();

        Party party = partyRepository.findById(member.getPartyId()).orElseThrow(() -> new IllegalArgumentException("파티가 지정되있지 않습니다."));

        // 캐릭터 인포, leaderActorId
        Map<Integer, CharacterInfo> battleCharacterInfoMap = partyMembers.stream()
                .map(BattleInfoMapper::toCharacterInfo)
                .collect(Collectors.toMap(CharacterInfo::getOrder, Function.identity()));
        model.addAttribute("battleCharacterInfoMap", battleCharacterInfoMap);
        Long leaderActorId = !leaderCharacter.isAlreadyDead() ? leaderCharacter.getBaseActor().getId() : null;
        model.addAttribute("leaderActorId", leaderActorId);

        // 적 인포
        Enemy enemy = (Enemy) enemyActor;
        EnemyInfo enemyInfo = toEnemyInfo(enemy);
        model.addAttribute("enemyInfo", enemyInfo);

        // 적 hp 트리거
        BaseEnemy baseEnemy = (BaseEnemy) enemy.getBaseActor();
        String enemyRootNameEn = baseEnemy.getRootNameEn();
        List<BaseEnemy> baseEnemies = baseEnemyRepository.findByRootNameEn(enemyRootNameEn);
        List<Integer> triggerHps = baseEnemies.stream().map(BaseEnemy::getHpTriggers)
                .flatMap(Collection::stream)
                .toList();
        model.addAttribute("triggerHps", triggerHps);


        // 소환석 인포
        List<Long> summonMoveIds = leaderCharacter.getSummonMoveIds();
        List<Move> summonMoves = moveRepository.findAllById(summonMoveIds);
        List<MoveInfo> summonInfos = summonMoves.stream().map(move -> toSummonInfo(move, leaderCharacter)).toList();
        model.addAttribute("summonInfos", summonInfos);

        // 페이탈 체인 인포
        Long fatalChainMoveId = member.getFatalChainMoveId();
        MoveInfo fatalChainInfo = moveRepository.findById(fatalChainMoveId).map(BattleInfoMapper::toFatalChainInfo).orElseGet(() -> MoveInfo.builder().build());
        model.addAttribute("fatalChainInfo", fatalChainInfo);

        // 페이탈 체인 게이지
        model.addAttribute("fatalChainGauge", member.getFatalChainGauge());

        // 캐릭터 + 적 에셋
        List<Long> assetIds = partyMembers.stream()
                .map(partyMember -> party.getActorIds().indexOf(partyMember.getBaseActor().getId())) // 파티에서 현재 프론트 멤버의 id 기준 인덱스 구해서
                .map(index -> party.getActorAssetIds().get(index))// 해당 인덱스에 대응하는 assetId 를 반환 CHECK actorIds 와 actorAssetIds 는 반드시 순서가 동일해야함
                .toList();
        List<Asset> actorAssets = assetRepository.findWithChildrenByAssetId(assetIds); // CHECK 여기서 IN절 조회로 순서섞임, 캐릭터의 무브별로 분리되어있으므로, AssetInfo 로 변환후 정렬
        List<Asset> enemyAsset = assetRepository.findAllByActorId(enemyActor.getBaseActor().getId());
        actorAssets.addAll(enemyAsset);
        // 솬석, 페이탈체인 에셋
        List<Asset> summonAssets = assetRepository.findAllByMoveIdIn(party.getSummonIds());
        Asset fatalChainAsset = assetRepository.findByMoveId(member.getFatalChainMoveId()).getFirst();

        // AssetInfo.Asset 으로 변환
        List<AssetInfo.Asset> assetInfoAssets = toAssetInfoAsset(leaderCharacter.getBaseActor().getId(), leaderCharacter.getBaseActor().getWeaponId(), actorAssets, summonAssets, fatalChainAsset);
        // BattleActor.currentOrder 을 매핑해주기 위한 맵
        Map<Long, Integer> actorIdByCurrentOrderMap = currentFieldActors.stream().collect(Collectors.toMap(battleActor -> battleActor.getBaseActor().getId(), Actor::getCurrentOrder));

        // 에셋 기타 상태
        Move enemyStartMove = enemy.getCurrentStandbyType() != null ? enemy.getMove(enemy.getCurrentStandbyType()) : enemy.getMove(MoveType.IDLE_DEFAULT);
        boolean isChargeAttackSkip = leaderCharacter.getMember().isChargeAttackSkip();

        // AssetInfo 조합
        List<AssetInfo> assetInfos = assetInfoAssets.stream().map(asset -> {
                    boolean isEnemy = enemyActor.getBaseActor().getId().equals(asset.getActorId());
                    boolean isMainCharacter = leaderCharacter.getBaseActor().getId().equals(asset.getActorId());
                    MotionType startMotion = isEnemy ? enemyStartMove.getMotionType() : MotionType.STB_WAIT;
                    int currentOrder = actorIdByCurrentOrderMap.get(asset.getActorId());
                    return AssetInfo.builder()
                            .currentOrder(currentOrder)
                            .asset(asset)
                            .isChargeAttackSkip(isChargeAttackSkip)
                            .isEnemy(isEnemy)
                            .isLeaderCharacter(isMainCharacter)
                            .startMotion(startMotion)
                            .build();
                }
        ).toList();

        assetInfos.forEach(assetInfo -> log.info("assertInfo = {}", assetInfo));
        model.addAttribute("assetInfos", assetInfos);

        // 가드상태
        List<Boolean> guardStates = new ArrayList<>(Collections.nCopies(5, false));
        partyMembers.forEach(partyMember -> guardStates.set(partyMember.getCurrentOrder(), partyMember.isGuardOn()));
        model.addAttribute("guardStates", guardStates);

        // 포션
        PotionInfo potionInfo = PotionInfo.builder()
                .potionCount(member.getPotionCount())
                .allPotionCount(member.getAllPotionCount())
                .elixirCount(0) // 미구현
                .build();
        model.addAttribute("potionInfo", potionInfo);
    }


}
