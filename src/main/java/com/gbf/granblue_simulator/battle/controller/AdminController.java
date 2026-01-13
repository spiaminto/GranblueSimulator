package com.gbf.granblue_simulator.battle.controller;

import com.gbf.granblue_simulator.battle.controller.dto.room.RoomAddForm;
import com.gbf.granblue_simulator.battle.controller.dto.room.RoomInfo;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.repository.MemberRepository;
import com.gbf.granblue_simulator.battle.repository.RoomRepository;
import com.gbf.granblue_simulator.battle.service.MemberService;
import com.gbf.granblue_simulator.battle.service.RoomService;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.repository.BaseActorRepository;
import com.gbf.granblue_simulator.metadata.repository.MoveRepository;
import com.gbf.granblue_simulator.party.controller.dto.PartyCharacterInfo;
import com.gbf.granblue_simulator.party.controller.dto.PartyInfo;
import com.gbf.granblue_simulator.party.controller.dto.PartySummonInfo;
import com.gbf.granblue_simulator.party.domain.Party;
import com.gbf.granblue_simulator.party.repository.PartyRepository;
import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final RoomService roomService;
    private final MemberRepository memberRepository;
    private final MoveRepository moveRepository;
    private final PartyRepository partyRepository;
    private final BaseActorRepository baseActorRepository;
    private final MemberService memberService;
    private final RoomRepository roomRepository;

    @RequestMapping("/admin")
    public String index(@ModelAttribute("roomAddForm") RoomAddForm roomAddForm, Model model,
                        @AuthenticationPrincipal PrincipalDetails principal) {
        List<Room> rooms = roomService.findAll();
        rooms.forEach(room -> {
            log.info("[index] room = {}", room);
            room.getMembers().forEach(member -> {
                log.info("[index] member = {}", member);
                member.getActors().forEach(actor -> {
                    log.info("[index] actor = {}", actor);
                });
            });
        });
        List<RoomInfo> roomInfos = rooms.stream()
                .filter(room -> !room.getMembers().isEmpty()) // 멤버 입장 안되서 에러나면 패스
                .map(room -> RoomInfo.builder()
                        .id(room.getId())
                        .info(room.getInfo())
                        .ownerUsername(room.getOwnerUsername())
                        .memberCount(room.getMembers().size())
                        .enemyHpRate(room.getMembers().getFirst().getActors().stream()
                                .filter(Actor::isEnemy)
                                .findFirst().orElseThrow(() -> new IllegalArgumentException("적이 존재하지 않음"))
                                .getHpRate())
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
                            baseActorRepository.findAllById(party.getActorIds()).stream()
                                    .map(actor ->
                                            PartyCharacterInfo.builder()
                                                    .id(actor.getId())
                                                    .name(actor.getName())
                                                    .portraitSrc(actor.getDefaultVisual().getPortraitImageSrc())
                                                    .chargeAttack(actor.getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                                                    .abilities(actor.getMoves().values().stream().filter(move ->
                                                            move.getType().getParentType() == MoveType.ABILITY).toList())
                                                    .supportAbilities(actor.getMoves().values().stream().filter(move ->
                                                            move.getType().getParentType() == MoveType.SUPPORT_ABILITY).toList())
                                                    .build()
                                    ).toList()
                    )
                    .summonInfos(
                            moveRepository.findAllById(party.getSummonIds()).stream()
                                    .map(move ->
                                            PartySummonInfo.builder()
                                                    .id(move.getId())
                                                    .name(move.getName())
                                                    .info(move.getInfo())
                                                    .cooldown(move.getCoolDown())
                                                    .portraitSrc(move.getDefaultVisual().getPortraitImageSrc())
                                                    .build()
                                    ).toList()
                    )
                    .build();
            model.addAttribute("partyInfo", partyInfo);
        }

        return "admin/adminIndex";
    }

    @DeleteMapping("/api/admin/room/{roomId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteRoom(@PathVariable Long roomId) {
        log.info("[deleteRoom] roomId = {}",roomId);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new IllegalArgumentException("없는 방"));
        List<Member> members = new ArrayList<>(room.getMembers());
        for (Member member : members) {
            memberService.exitRoom(member.getId()); // 전원 나가면 알아서 삭제됨
        }

        return ResponseEntity.ok(Map.of("isSuccess", "true"));
    }

    @GetMapping("/insert")
    public String insert() {
        return "insert/character";
    }

    @GetMapping("/insert-enemy")
    public String enemyInsert() {
        return "insert/enemy";
    }


}
