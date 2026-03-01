package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.info.*;
import com.gbf.granblue_simulator.battle.controller.dto.request.*;
import com.gbf.granblue_simulator.battle.controller.dto.response.*;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.system.dto.PotionResult;
import com.gbf.granblue_simulator.battle.repository.MemberRepository;
import com.gbf.granblue_simulator.battle.repository.RoomRepository;
import com.gbf.granblue_simulator.battle.service.*;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.metadata.repository.BaseEnemyRepository;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import com.gbf.granblue_simulator.metadata.service.BaseActorService;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
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
    private final BaseMoveRepository baseMoveRepository;
    private final PartyRepository partyRepository;

    private final BattleContext battleContext;
    private final BattleLogService battleLogService;
    private final BattleCommandService battleCommandService;

    private final BattleResponseMapper responseMapper;
    private final BaseEnemyRepository baseEnemyRepository;
    private final RoomRepository roomRepository;
    private final MemberService memberService;
    private final RoomChatService roomChatService;
    private final RoomService roomService;
    private final BaseActorService baseActorService;

    @GetMapping("/members/me/tutorial")
    public String getTutorialRoom(@AuthenticationPrincipal PrincipalDetails principalDetails, Model model) {
        Long userId = principalDetails.getUser().getId();
        Room room = roomService.enterTutorialRoom(userId);

        Member member;
        if (room.getMembers().isEmpty()) {
            member = memberService.enterTutorialRoom(room.getId(), userId);
            battleContext.init(member, null);
            List<MoveLogicResult> battleStartResults = battleCommandService.startBattle();
            responseMapper.toBattleResponse(battleStartResults).forEach(response -> log.info("[getRoom] battleStartResponse: \n{}", response));
        } else {
            member = room.getMembers().stream().filter(roomMember -> roomMember.getUser().getId().equals(userId)).toList().getFirst();
            battleContext.init(member, null);
        }

        setInfoAttributes(model, member);

        return "battle/battleTutorial";
    }

    @GetMapping("/room/{roomId}")
    public String getRoom(@PathVariable Long roomId,
                          @AuthenticationPrincipal PrincipalDetails principal,
                          Model model,
                          RedirectAttributes redirectAttributes) {

        Member member = memberRepository.findByRoomIdAndUserId(roomId, principal.getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        if (member.getRoom().getRoomStatus() != RoomStatus.ACTIVE || member.checkedResult()) {
            // 결과창으로 이동
            return "redirect:/room/" + roomId + "/result";
        }

        if (member.getActors().isEmpty()) {
            // 이미 나간 방
            redirectAttributes.addFlashAttribute("alertMessage", "이미 퇴장한 방입니다.");
            return "redirect:/";
        }

        battleContext.init(member, null);

        if (member.getCurrentTurn() <= 0) {
            // 첫입장
            List<MoveLogicResult> battleStartResults = battleCommandService.startBattle();
            responseMapper.toBattleResponse(battleStartResults).forEach(response -> log.info("[getRoom] battleStartResponse: \n{}", response));
        }

        setInfoAttributes(model, member);

        return "battle/battle";
    }

    @GetMapping("/room/{roomId}/result")
    public String getRoomResult(@PathVariable Long roomId,
                                @AuthenticationPrincipal PrincipalDetails principal, Model model) {

        Member member = memberRepository.findByRoomIdAndUserId(roomId, principal.getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        // CHECK 나중에 수정
//        Member member = memberRepository.findByRoomIdAndUserId(roomId, 1L).orElseThrow(() -> new IllegalArgumentException("멤버를 찾을수 없음"));

        model.addAttribute("member", member);
        Room room = member.getRoom();
        BaseActor enemy = baseActorService.findById(room.getEnemyBaseId()).orElseThrow(() -> new IllegalArgumentException("잘못된 적 정보입니다. id = " + room.getEnemyBaseId()));

        if (!member.checkedResult()) {
            member.updateCheckedResult(true);
        }

        String formattedEndedAt = room.getEndedAt().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.KOREA));
        BattleResultInfo resultInfo = BattleResultInfo.builder()
                .enemyName(enemy.getName())
                .enemyPortraitSrc(enemy.getDefaultVisual().getPortraitImageSrc())
                .endedAt(formattedEndedAt)
                .enterUserCount(room.getEnterUserCount())
                .build();
        model.addAttribute("resultInfo", resultInfo);

        Duration totalDuration = Duration.between(room.getCreatedAt(), room.getEndedAt());
        int minutesPart = totalDuration.toMinutesPart();
        int secondsPart = totalDuration.toSecondsPart();
        String totalTime = String.format("%02d:%02d", minutesPart, secondsPart);

        List<BattleResultMemberInfo> resultMemberInfos = room.getMembers().stream()
                .map(roomMember -> {
                            int totalDamage = battleLogService.getEnemyTakenDamageSumByMember(roomMember);
                            return BattleResultMemberInfo.builder()
                                    .username(roomMember.getUser().getUsername())
                                    .enemyHp(String.format("%,d", enemy.getMaxHp()))
                                    .totalTurns(roomMember.getCurrentTurn())
                                    .totalTime(totalTime)
                                    .totalDamage(totalDamage)
                                    .formattedTotalDamage(String.format("%,d", totalDamage))
                                    .totalDamageRate((int) ((double) totalDamage / enemy.getMaxHp() * 100 * 100) / 100.0)
                                    .totalHonor(String.format("%,d", roomMember.getHonor()))
                                    .build();
                        }
                ).sorted(Comparator.comparing(BattleResultMemberInfo::getTotalDamage).reversed())
                .toList();
        model.addAttribute("memberInfos", resultMemberInfos);

        String findUsername = member.getUser().getUsername();
        BattleResultMemberInfo myInfo = resultMemberInfos.stream().filter(memberInfo -> memberInfo.getUsername().equals(findUsername)).findFirst().orElseThrow(() -> new IllegalArgumentException("there are no member.getUsername, username = " + findUsername));
        model.addAttribute("myInfo", myInfo);

        return "battle/result";
    }

    @GetMapping("/api/enemy-src")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEnemySrcMap(@RequestParam Long memberId) {
        log.info("[getEnemySrcMap] memberId = {}", memberId);
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        battleContext.init(member, null);
        Actor enemy = battleContext.getEnemy();
        Enemy enemyConcrete = (Enemy) enemy;

        Map<String, Object> result = new HashMap<>();

        List<AssetInfo> assetInfoAssets = toAssetInfo(List.of(enemy), new ArrayList<>());
        result.put("assetInfo", assetInfoAssets.getFirst());
        result.put("actorName", enemy.getName());
        result.put("formOrder", enemyConcrete.getCurrentForm());

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

    @GetMapping("/api/rooms/{roomId}/chats")
    @ResponseBody
    public List<ChatResponse> getChats(@PathVariable Long roomId,
                                       @RequestParam(required = false) Long lastId) {
        return roomChatService.getChats(roomId, lastId);
    }

    @PostMapping("/api/rooms/{roomId}/chats")
    @ResponseBody
    public ChatResponse send(@PathVariable Long roomId,
                             @RequestBody ChatSendRequest request,
                             @AuthenticationPrincipal PrincipalDetails principalDetails) {
        Long userId = principalDetails.getUser().getId();
        return roomChatService.save(roomId, userId, request);
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
        List<MoveLogicResult> syncResults = battleCommandService.sync();
        List<BattleResponse> syncResponse = responseMapper.toBattleResponse(syncResults);

//        log.info("syncResponse: {}", syncResponse);

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

        battleContext.init(findMember, characterId, moveId);
        List<MoveLogicResult> results = battleCommandService.ability(moveId);

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
        List<MoveLogicResult> results = battleCommandService.fatalChain();

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
        List<MoveLogicResult> results = battleCommandService.summon(summonId, request.isDoUnionSummon());

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
        List<MoveLogicResult> turnProgressResults = battleCommandService.progressTurn();

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
    @Transactional
    public ResponseEntity<ToggleChargeAttackResponse> postToggleChargeAttack(@RequestBody ToggleChargeAttackRequest request,
                                                                             @AuthenticationPrincipal PrincipalDetails principalDetails) {
        log.info("chargeAttackOnRequest = {}", request);

        // TODO 검증
        Long userId = principalDetails == null ? 1L : principalDetails.getId();

        Member member = memberRepository.findByRoomIdAndUserId(request.getRoomId(), userId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        member.updateChargeAttackOn(request.isChargeAttackOn());
        battleContext.init(member, null); // statusDetails 초기화를 위해 필요함
        List<Boolean> canChargeAttacks = member.getActors().stream().sorted(Comparator.comparing(Actor::getCurrentOrder)).map(Actor::canCharacterChargeAttack).toList();

        ToggleChargeAttackResponse response = ToggleChargeAttackResponse.builder()
                .chargeAttackOn(member.isChargeAttackOn())
                .canChargeAttacks(canChargeAttacks)
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

    @GetMapping("/api/rooms/{roomId}/members/me/battle-init")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getInitData(@PathVariable Long roomId,
                                                           @AuthenticationPrincipal PrincipalDetails principalDetails) {
        // 캐릭터 인포, leaderActorId
        Member member = memberRepository.findByRoomIdAndUserId(roomId, principalDetails.getId()).orElseThrow(() -> new IllegalArgumentException("잘못된 멤버 요청입니다."));

        Map<String, Object> result = new HashMap<>();

        battleContext.init(member, null);

        List<Actor> partyMembers = battleContext.getFrontCharacters();
        Map<Integer, CharacterBattleInfo> characterInfo = partyMembers.stream()
                .map(BattleInfoMapper::toCharacterInfo)
                .collect(Collectors.toMap(CharacterBattleInfo::getOrder, Function.identity()));
        result.put("characterInfo", characterInfo);

        Actor leaderCharacter = battleContext.getLeaderCharacter();
        Long leaderActorId = !leaderCharacter.isAlreadyDead() ? leaderCharacter.getId() : null;
        result.put("leaderActorId", leaderActorId);

        // 적 인포
        Enemy enemy = (Enemy) battleContext.getEnemy();
        EnemyInfo enemyInfo = toEnemyInfo(enemy);
        result.put("enemyInfo", enemyInfo);

        // 적 hp 트리거
        BaseEnemy baseEnemy = (BaseEnemy) enemy.getBaseActor();
        String enemyRootNameEn = baseEnemy.getRootNameEn();
        List<BaseEnemy> baseEnemies = baseEnemyRepository.findByRootNameEn(enemyRootNameEn);
        List<Integer> triggerHps = baseEnemies.stream()
                .flatMap(base -> base.getOmens().values().stream())
                .flatMap(baseOmen -> baseOmen.getOmenType() == OmenType.HP_TRIGGER
                        ? baseOmen.getTriggerHps().stream() : Stream.empty())
                .toList();
        result.put("triggerHps", triggerHps);

        // 소환석 인포
        List<Move> summonMoves = leaderCharacter.getSummons();
        List<MoveInfo> summonInfos = summonMoves.stream().map(MoveInfo::from).toList();
        result.put("summonInfos", summonInfos);

        // 페이탈 체인 인포
        Long fatalChainMoveId = member.getFatalChainMoveId();
        BaseMove fatalChainMove = baseMoveRepository.findById(fatalChainMoveId).orElseThrow(() -> new IllegalArgumentException("페이탈 체인 없음"));
        MoveInfo fatalChainInfo = MoveInfo.from(fatalChainMove);
        result.put("fatalChainInfo", fatalChainInfo);

        // 페이탈 체인 게이지
        result.put("fatalChainGauge", member.getFatalChainGauge());

        // 캐릭터 + 적 에셋 (소환석, 펭탈 체인 포함) AssetInfo.Asset 으로 변환
        List<AssetInfo> assetInfos = toAssetInfo(battleContext.getCurrentFieldActors(), summonMoves);
        result.put("assetInfos", assetInfos);
        assetInfos.forEach(assetInfo -> log.info("assetInfo = {}", assetInfo));

        // 기타
        result.put("currentTurn", member.getCurrentTurn());
        result.put("startTime", member.getRoom().getCreatedAt());
        result.put("usedSummon", member.usedSummon());

        return ResponseEntity.ok(result);
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

        // 멤버
        MemberInfo memberInfo = MemberInfo.builder()
                .id(member.getId())
                .currentTurn(member.getCurrentTurn())
                .isChargeAttackOn(member.isChargeAttackOn())
                .build();
        model.addAttribute("memberInfo", memberInfo);

        // 방
        Room room = member.getRoom();
        LocalDateTime roomCreatedAt = room.getCreatedAt();
        model.addAttribute("roomId", room.getId());
        model.addAttribute("roomCreatedAt", roomCreatedAt);

        // 캐릭터 인포, leaderActorId
        Map<Integer, CharacterBattleInfo> battleCharacterInfoMap = partyMembers.stream()
                .map(BattleInfoMapper::toCharacterInfo)
                .collect(Collectors.toMap(CharacterBattleInfo::getOrder, Function.identity()));
        model.addAttribute("battleCharacterInfoMap", battleCharacterInfoMap);
        Long leaderActorId = !leaderCharacter.isAlreadyDead() ? leaderCharacter.getId() : null;
        model.addAttribute("leaderActorId", leaderActorId);

        // 적 인포
        Enemy enemy = (Enemy) enemyActor;
        EnemyInfo enemyInfo = toEnemyInfo(enemy);
        model.addAttribute("enemyInfo", enemyInfo);

        // 적 hp 트리거
        BaseEnemy baseEnemy = (BaseEnemy) enemy.getBaseActor();
        String enemyRootNameEn = baseEnemy.getRootNameEn();
        List<BaseEnemy> baseEnemies = baseEnemyRepository.findByRootNameEn(enemyRootNameEn);
        List<Integer> triggerHps = baseEnemies.stream()
                .flatMap(base -> base.getOmens().values().stream())
                .flatMap(baseOmen -> baseOmen.getOmenType() == OmenType.HP_TRIGGER
                        ? baseOmen.getTriggerHps().stream() : Stream.empty())
                .toList();

//        List<Integer> triggerHps = baseEnemies.stream().map(BaseEnemy::getHpTriggers)
//                .flatMap(Collection::stream)
//                .toList();
        model.addAttribute("triggerHps", triggerHps);

        // 소환석 인포
        List<Move> summonMoves = leaderCharacter.getSummons();
        List<MoveInfo> summonInfos = summonMoves.stream().map(BattleInfoMapper::toSummonInfo).toList();
        model.addAttribute("summonInfos", summonInfos);

        // 페이탈 체인 인포
        Long fatalChainMoveId = member.getFatalChainMoveId();
        BaseMove fatalChainMove = baseMoveRepository.findById(fatalChainMoveId).orElseThrow(() -> new IllegalArgumentException("페이탈 체인 없음"));
        MoveInfo fatalChainInfo = toFatalChainInfo(fatalChainMove);
        model.addAttribute("fatalChainInfo", fatalChainInfo);

        // 페이탈 체인 게이지
        model.addAttribute("fatalChainGauge", member.getFatalChainGauge());

        // 캐릭터 + 적 에셋 (소환석, 펭탈 체인 포함) AssetInfo.Asset 으로 변환
        List<AssetInfo> assetInfos = toAssetInfo(currentFieldActors, summonMoves);

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
