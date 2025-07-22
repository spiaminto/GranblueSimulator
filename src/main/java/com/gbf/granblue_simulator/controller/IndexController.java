package com.gbf.granblue_simulator.controller;

import com.gbf.granblue_simulator.auth.PrincipalDetails;
import com.gbf.granblue_simulator.controller.response.info.RoomInfo;
import com.gbf.granblue_simulator.controller.response.info.battle.*;
import com.gbf.granblue_simulator.controller.response.info.party.PartyCharacterInfo;
import com.gbf.granblue_simulator.controller.response.info.party.PartyInfo;
import com.gbf.granblue_simulator.controller.response.info.party.PartySummonInfo;
import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.Room;
import com.gbf.granblue_simulator.domain.User;
import com.gbf.granblue_simulator.domain.actor.Party;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.repository.MemberRepository;
import com.gbf.granblue_simulator.repository.PartyRepository;
import com.gbf.granblue_simulator.repository.RoomRepository;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.repository.move.MoveRepository;
import com.gbf.granblue_simulator.service.MemberService;
import com.gbf.granblue_simulator.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IndexController {

    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final MemberRepository memberRepository;
    private final MoveRepository moveRepository;
    private final PartyRepository partyRepository;
    private final ActorRepository actorRepository;
    private final MemberService memberService;

    @RequestMapping("/")
    public String index(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm, Model model,
                        @AuthenticationPrincipal PrincipalDetails principal) {
        List<Room> rooms = roomService.findAll();
        List<RoomInfo> roomInfos = rooms.stream()
                .map(room -> RoomInfo.builder()
                        .id(room.getId())
                        .info(room.getInfo())
                        .ownerUsername(room.getOwnerUsername())
                        .memberCount(room.getMembers().size())
                        .enemyHpRate(room.getMembers().getFirst().getBattleActors().stream()
                                .filter(BattleActor::isEnemy)
                                .findFirst().orElseThrow(() -> new IllegalArgumentException("적이 존재하지 않음"))
                                .calcHpRate())
                        .build()
                ).toList();
        model.addAttribute("roomInfos", roomInfos);

        if (principal != null) {
            Long primaryPartyId = principal.getUser().getPrimaryPartyId();
            Party party = partyRepository.findById(primaryPartyId).orElseThrow(() -> new IllegalArgumentException("선택된 파티 없음"));
            PartyInfo partyInfo = PartyInfo.builder()
                    .id(party.getId())
                    .name(party.getName())
                    .info(party.getInfoText())
                    .characterInfos(
                            actorRepository.findAllById(party.getActorIds()).stream()
                                    .map(actor ->
                                            PartyCharacterInfo.builder()
                                                    .id(actor.getId())
                                                    .name(actor.getName())
                                                    .portraitSrc(actor.getBattlePortraitSrc())
                                                    .chargeAttack(actor.getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                                                    .abilities(actor.getMoves().values().stream().filter(move ->
                                                            move.getType().getParentType() == MoveType.ABILITY).toList())
                                                    .supportAbilities(actor.getMoves().values().stream().filter(move ->
                                                            move.getType().getParentType() == MoveType.SUPPORT_ABILITY).toList())
                                                    .build()
                                    ).toList()
                    )
                    .summonInfos(
                            moveRepository.findAllById(party.getSummonMoveIds()).stream()
                                    .map(move ->
                                            PartySummonInfo.builder()
                                                    .id(move.getId())
                                                    .name(move.getName())
                                                    .info(move.getInfo())
                                                    .cooldown(move.getCoolDown())
                                                    .portraitSrc(move.getAsset().getIconImageSrc())
                                                    .build()
                                    ).toList()
                    )
                    .build();
            model.addAttribute("partyInfo", partyInfo);
        }

        return "index";
    }

    @PostMapping("/room")
    public String addRoom(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm,
                          @AuthenticationPrincipal PrincipalDetails principalDetails) {
        if (principalDetails == null) {
            return "redirect:/";
        }

        Room roomParam = Room.builder()
                .info(roomAddForm.getMessage())
                .build();

        // 방 작성
        Room savedRoom = roomService.addRoom(roomParam, principalDetails.getId());

        // 멤버 추가
        memberService.enterRoom(savedRoom.getId(), principalDetails.getId());

        return "redirect:/room/" + savedRoom.getId();
    }

    @PostMapping("/room/exit")
    @Transactional
    public String exitRoom(@ModelAttribute ExitRoomForm form,
                           @AuthenticationPrincipal PrincipalDetails principal) {
        Long memberId = form.getMemberId();
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        Long userId = principal.getId();
        if (!member.getUser().getId().equals(userId)) {
            return "redirect:/";
        }

        memberService.exitRoom(memberId);

        return "redirect:/";
    }

    @PostMapping("/room/join")
    @Transactional
    public String joinRoom(@ModelAttribute EnterRoomForm form,
                           @AuthenticationPrincipal PrincipalDetails principal, Model model) {
        log.info("[joinRoom] enterRoomForm = {}", form);
        if (principal == null || !principal.getId().equals(form.getUserId())) {
            return "redirect:/";
        }
        Long userId = principal.getId();
        Long roomId = form.getRoomId();

        Member member = memberRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        if (member == null) {
            // 멤버 추가 시작
            memberService.enterRoom(roomId, userId);
        }

        return "redirect:/room/" + roomId;
    }

    @GetMapping("/insert")
    public String insert() {
        return "insert";
    }

    @GetMapping("/insert-enemy")
    public String enemyInsert() {
        return "insertEnemy";
    }


}
