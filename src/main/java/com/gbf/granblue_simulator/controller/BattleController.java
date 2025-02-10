//package com.gbf.granblue_simulator.controller;
//
//import com.gbf.granblue_simulator.domain.Member;
//import com.gbf.granblue_simulator.domain.User;
//import com.gbf.granblue_simulator.domain.entity.Character;
//import com.gbf.granblue_simulator.domain.entity.battle.BattleCharacter;
//import com.gbf.granblue_simulator.domain.move.Move;
//import com.gbf.granblue_simulator.domain.move.prop.asset.sub.Audio;
//import com.gbf.granblue_simulator.domain.move.prop.status.Status;
//import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
//import com.gbf.granblue_simulator.repository.*;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Controller;
//import org.springframework.ui.Model;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.ResponseBody;
//
//import java.util.List;
//
//@Controller
//@RequiredArgsConstructor
//@Slf4j
//public class BattleController {
//
//    private final UserRepository userRepository;
//    private final RoomRepository roomRepository;
//    private final MemberRepository memberRepository;
//
//    private final BattleCharacterRepository battleCharacterRepository;
//    private final CharacterRepository characterRepository;
//
//
//    @GetMapping("/battle")
//    public String battle(Model model) {
//
//
//        User user = userRepository.findByUsername("test");
//        if (user.getMembers().isEmpty()) {
//            log.info("user.getMembers().isEmpty(), user: {}", user);
//        }
//
//        Member member = user.getMembers().getFirst();
//        model.addAttribute("member", member);
//
//        List<BattleCharacter> characters = member.getCharacters();
//        if (characters.isEmpty()) {
//            log.info("characters.isEmpty(), member: {}", member);
//        }
//
//        characters.forEach(character -> {
//            log.info("character: {}", character);
//        });
//        model.addAttribute("characters", characters);
//
//        model.addAttribute("roomId", member.getRoom().getId());
//
//
//        return "battle";
//    }
//
//    @PostMapping("/api/ability")
//    @ResponseBody
//    public ResponseEntity<AbilityResponse> ability(@RequestBody AbilityRequest abilityRequest) {
//        log.info("abilityRequest: {}", abilityRequest);
//
//        long memberId = abilityRequest.getMemberId();
//        long characterId = abilityRequest.getCharacterId();
//        long roomId = abilityRequest.getRoomId();
//        long abilityId = abilityRequest.getAbilityId();
//        long abilityOrder = abilityRequest.getAbilityOrder();
//        long characterOrder = abilityRequest.getCharacterOrder();
//        List<BattleCharacter> battleCharacters = battleCharacterRepository.findByMemberId(memberId);
//        battleCharacters.forEach(battleCharacter -> {
//            log.info("battleCharacter: {}", battleCharacter);
//        });
//
//        BattleCharacter usedAbilityCharacter = battleCharacters.stream().filter(character -> character.getId().equals(characterId)).findAny().orElse(null);
//        if (usedAbilityCharacter == null) {
//            log.info("usedAbilityCharacter is null");
//            return ResponseEntity.badRequest().body(null);
//        }
//
//        Character character = characterRepository.findById(usedAbilityCharacter.getCharacter().getId()).orElse(null);
//        Move move = character.getMoves().get(abilityId);
//        log.info("move: {}", move);
//
//        Integer[] damages = new Integer[] {1, 2, 3, 4};
//
//        List<Status> buffStatuses = move.getStatuses().stream().filter(status -> status.getType() == StatusType.BUFF || status.getType() == StatusType.BUFF_FOR_ALL).toList();
//        List<StatusDto> buffs = buffStatuses.stream().map(StatusDto::of).toList();
//
//        List<Status> debuffStatues = move.getStatuses().stream().filter(status -> status.getType() == StatusType.DEBUFF || status.getType() == StatusType.DEBUFF_FOR_ALL).toList();
//        List<StatusDto> debuffs = debuffStatues.stream().map(StatusDto::of).toList();
//
//        Integer abilityHitCount = 6;
//        Integer abilityEffectCount = 1;
//
//        AbilityResponse abilityResponse = AbilityResponse.builder()
//                .hasMotion(false)
//                .isMotionFullSize(false)
//                .abilityHitCount(abilityHitCount)
//                .abilityEffectCount(abilityEffectCount)
//                .abilityVideoSrc(move.getAsset().getEffectVideo().getSrc())
//                .abilityAudioSrcs(move.getAsset().getAudios().stream().map(Audio::getSrc).toList())
//                .damages(damages)
//                .buffs(buffs)
//                .deBuffs(debuffs)
//                .build();
//
//        return ResponseEntity.ok(abilityResponse);
//    }
//
//    /*
//    {
//                        hasMotion: true,
//                        isMotionFullSize: false,
//                        abilityHitCount: 0,
//                        abilityEffectCount: 1,
//                        damages: [],
//                        buffs: [
//                            {
//                                targets: [1, 2, 3],
//                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-3-1.png',
//                                effectText: '재공격',
//                                infoText: '턴 진행시 공격행동을 2회 진행하는 상태'
//                            },
//                            {
//                                targets: [4],
//                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-3-2.png',
//                                effectText: '감싸기',
//                                infoText: '적의 공격을 아군 대신 받는 상태'
//                            },
//                            {
//                                targets: [4],
//                                iconSrc: '/static/assets/img/ch/haira/status/status-haira-ability-3-3.png',
//                                effectText: '피해 무시',
//                                infoText: '적의 공격 데미지와 약체효과를 무시하는 상태'
//                            },
//                        ],
//                        deBuffs: []
//                    },
//     */
//}
