package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.info.*;
import com.gbf.granblue_simulator.battle.controller.dto.request.ChatSendRequest;
import com.gbf.granblue_simulator.battle.controller.dto.response.ChatResponse;
import com.gbf.granblue_simulator.battle.controller.dto.response.MemberResponse;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultStatusDto;
import com.gbf.granblue_simulator.battle.service.*;
import com.gbf.granblue_simulator.metadata.domain.Raid;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.service.BaseActorService;
import com.gbf.granblue_simulator.metadata.service.BaseEnemyService;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
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
import java.util.stream.Collectors;

import static com.gbf.granblue_simulator.battle.controller.BattleInfoMapper.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BattleController {

    private final BattleContext battleContext;
    private final BattleLogService battleLogService;
    private final BattleCommandService battleCommandService;

    private final MemberService memberService;
    private final RoomService roomService;
    private final RoomChatService roomChatService;

    private final BaseActorService baseActorService;
    private final BaseEnemyService baseEnemyService;
    private final BaseMoveService baseMoveService;
    private final StatusService statusService;

    private final BattleResponseMapper responseMapper;

    @GetMapping("/members/me/tutorial")
    public String getTutorialRoom(@AuthenticationPrincipal PrincipalDetails principalDetails, Model model) {
        Long userId = principalDetails.getUser().getId();
        Room room = roomService.enterTutorialRoom(userId);

        Member member;
        if (room.getMembers().isEmpty()) {
            member = memberService.enterTutorialRoom(room.getId(), userId);
            battleCommandService.startBattle(BattleCommandRequest.of(member.getId()));
        } else {
            member = room.getMembers().stream().filter(roomMember -> roomMember.getUser().getId().equals(userId)).toList().getFirst();
            if (!member.isBattleStarted()) battleCommandService.startBattle(BattleCommandRequest.of(member.getId()));
            battleCommandService.adjustTutorial(BattleCommandRequest.of(member.getId()));
        }

        setInfoAttributes(model, member);
        model.addAttribute("tutorialIndex", member.getTutorialIndex());

        return "battle/battle";
    }

    public record TutorialSaveRequest(Integer tutorialIndex, Long roomId) {
    }

    @PostMapping("/members/me/tutorial/save")
    @Transactional
    public ResponseEntity<Map<String, Object>> postTutorialSave(@AuthenticationPrincipal PrincipalDetails principalDetails, @RequestBody TutorialSaveRequest request) {
        log.info("[postTutorialSave] request = {}", request);

        Member member = memberService.findByRoomIdAndUserId(request.roomId(), principalDetails.getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버 입니다."));
        if (request.tutorialIndex() == null) throw new IllegalArgumentException("튜토리얼 저장 오류");
        member.updateTutorialIndex(request.tutorialIndex());

        return ResponseEntity.ok(Collections.singletonMap("success", true));
    }

    @GetMapping("/room/{roomId}")
    public String getRoom(@PathVariable Long roomId,
                          @AuthenticationPrincipal PrincipalDetails principal,
                          Model model,
                          RedirectAttributes redirectAttributes) {

        Member member = memberService.findByRoomIdAndUserId(roomId, principal.getId()).orElseThrow(() -> new IllegalStateException("잘못된 접근입니다."));
        if (member.getRoom().getRoomStatus() != RoomStatus.ACTIVE || member.checkedResult()) {
            // 결과창으로 이동
            return "redirect:/room/" + roomId + "/result";
        }

        if (member.getActors().isEmpty()) {
            // 이미 나간 방
            redirectAttributes.addFlashAttribute("alertMessage", "이미 퇴장한 방입니다.");
            return "redirect:/";
        }

        setInfoAttributes(model, member);

        return "battle/battle";

    }

    @GetMapping("/room/{roomId}/result")
    public String getRoomResult(@PathVariable Long roomId,
                                @AuthenticationPrincipal PrincipalDetails principal, Model model) {
        Member member = memberService.findByRoomIdAndUserId(roomId, principal.getId()).orElseThrow(() -> new IllegalStateException("유효하지 않은 멤버입니다."));

        model.addAttribute("member", member);
        Room room = member.getRoom();
        Raid raid = room.getRaid();

        String formattedEndedAt = room.getEndedAt().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(Locale.KOREA));
        BattleResultInfo resultInfo = BattleResultInfo.builder()
                .raidName(raid.getName())
                .enemyPortraitSrc(raid.getRaidImageSrc())
                .endedAt(formattedEndedAt)
                .enterUserCount(room.getEnterUserCount())
                .build();
        model.addAttribute("resultInfo", resultInfo);

        Duration totalDuration = Duration.between(room.getCreatedAt(), room.getEndedAt());
        int minutesPart = totalDuration.toMinutesPart();
        int secondsPart = totalDuration.toSecondsPart();
        String totalTime = String.format("%02d:%02d", minutesPart, secondsPart);

        Map<Long, Integer> damageSumByMemberId = room.getMembers().stream()
                .collect(Collectors.toMap(
                        Member::getId,
                        battleLogService::getEnemyTakenDamageSumByMember
                ));
        final int roomTotalDamage = damageSumByMemberId.values().stream().mapToInt(Integer::intValue).sum();

        ResultStatusDto lastEnemyStatus = battleLogService.getLastEnemyStatus(member);
        String enemyHpString;
        String enemyHpRateString;
        if (lastEnemyStatus != null) {
            int lastEnemyHp = Math.min(0, lastEnemyStatus.getHp());
            enemyHpString = String.format("%,d", lastEnemyHp);
            enemyHpRateString = (int) (((double) lastEnemyHp / lastEnemyStatus.getMaxHp()) * 10000) / 100 + "";
        } else {
            enemyHpString = "-";
            enemyHpRateString = "-";
        }

        List<BattleResultMemberInfo> resultMemberInfos = room.getMembers().stream()
                .map(roomMember -> {
                            Integer memberDamage = damageSumByMemberId.getOrDefault(roomMember.getId(), 0);
                            BaseActor leaderActor = baseActorService.findById(roomMember.getLeaderCharacterBaseId()).orElse(null);
                            String leaderActorCharacterIconSrc = leaderActor != null ? leaderActor.getDefaultVisual().getCharacterIconImageSrc() : "";
                            return BattleResultMemberInfo.builder()
                                    .username(roomMember.getUser().getUsername())
                                    .enemyHp(enemyHpString)
                                    .enemyHpRate(enemyHpRateString)
                                    .totalTurns(roomMember.getCurrentTurn())
                                    .totalTime(totalTime)
                                    .totalDamage(String.format("%,d", roomTotalDamage))
                                    .dealtDamage(String.format("%,d", memberDamage))
                                    .totalDamageRate((int) ((double) memberDamage / roomTotalDamage * 100 * 100) / 100.0)
                                    .totalHonor(String.format("%,d", roomMember.getHonor()))
                                    .leaderActorIconSrc(leaderActorCharacterIconSrc)
                                    .build();
                        }
                ).sorted(Comparator.comparing(BattleResultMemberInfo::getTotalDamageRate).reversed())
                .toList();
        model.addAttribute("memberInfos", resultMemberInfos);

        String findUsername = member.getUser().getUsername();
        BattleResultMemberInfo myInfo = resultMemberInfos.stream().filter(memberInfo -> memberInfo.getUsername().equals(findUsername)).findFirst().orElseThrow(() -> new IllegalArgumentException("there are no member.getUsername, username = " + findUsername));
        model.addAttribute("myInfo", myInfo);


        // 클리어 포인트 상승
        MemberService.RoomResultCheckResult roomResultCheckResult = memberService.checkRoomResult(member);// 클리어 포인트 상승
        model.addAttribute("additionalInfoMessage", roomResultCheckResult.message()); // 관련 메시지

        return "battle/result";
    }

    @GetMapping("/api/enemy-src")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEnemySrcMap(@RequestParam Long memberId) {
        log.info("[getEnemySrcMap] memberId = {}", memberId);
        Member member = memberService.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        Actor enemy = member.getActors().stream().filter(Actor::isEnemy).findFirst().orElseThrow(() -> new IllegalArgumentException("적 정보가 없습니다."));
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
        List<MemberResponse> memberResponses = roomService.findById(roomId).map(room ->
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


    /**
     * 초기 SSR 시 필요한 정보 model 에 set
     *
     * @param model
     * @param member
     */
    protected void setInfoAttributes(Model model, Member member) {

        if (battleContext.getMember() == null) {
            battleContext.init(member, null); // 보험
        }

        List<Actor> partyMembers = battleContext.getFrontCharacters();
        Actor enemyActor = battleContext.getEnemy();

        // 보험
        if (battleContext.getAllActors().stream().anyMatch(actor -> actor.getStatusDetails() == null)) {
            battleContext.getAllActors().forEach(statusService::syncStatus);
        }

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

        // 적 정보
        Enemy enemy = (Enemy) enemyActor;
        EnemyInfo enemyInfo = toEnemyInfo(enemy);
        model.addAttribute("enemyInfo", enemyInfo);

        // 페이탈 체인 정보
        Long fatalChainMoveId = member.getFatalChainMoveId();
        BaseMove fatalChainMove = baseMoveService.findById(fatalChainMoveId).orElseThrow(() -> new IllegalArgumentException("페이탈 체인 없음"));
        MoveInfo fatalChainInfo = toFatalChainInfo(fatalChainMove);
        model.addAttribute("fatalChainInfo", fatalChainInfo);
        model.addAttribute("fatalChainGauge", member.getFatalChainGauge()); // 페이탈 체인 게이지

        // 가드상태
        List<Boolean> guardStates = new ArrayList<>(Collections.nCopies(5, false));
        partyMembers.forEach(partyMember -> guardStates.set(partyMember.getCurrentOrder(), partyMember.isGuardOn()));
        model.addAttribute("guardStates", guardStates);

        // 포션
        PotionInfo potionInfo = PotionInfo.builder()
                .potionCount(member.getPotionCount())
                .allPotionCount(member.getAllPotionCount())
                .elixirCount(member.getElixirCount())
                .build();
        model.addAttribute("potionInfo", potionInfo);
    }


}
