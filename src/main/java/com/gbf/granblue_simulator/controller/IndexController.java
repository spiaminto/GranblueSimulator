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

    @GetMapping("/room/{roomId}")
    @Transactional
    public String getRoom(@PathVariable Long roomId,
                          @AuthenticationPrincipal PrincipalDetails principal, Model model) {
        if (principal == null) { return "redirect:/"; }
        Member member = memberRepository.findByRoomIdAndUserId(roomId, principal.getId()).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));

        List<BattleActor> battleActors = member.getBattleActors();
        if (battleActors.isEmpty()) {
            return "redirect:/";
        }

        // 인포 추가 시작
        battleActors.sort(Comparator.comparing(BattleActor::getCurrentOrder));
        BattleActor enemyActor = battleActors.getFirst();
        List<BattleActor> partyMembers = battleActors.subList(1, battleActors.size());
        BattleActor mainCharacter = partyMembers.stream().filter(battleActor -> battleActor.getActor().isMainCharacter()).findFirst().orElseThrow(() -> new IllegalArgumentException("메인 캐릭터 찾을수 없음"));

        // 캐릭터 인포
        List<BattleCharacterInfo> battleCharacterInfos = partyMembers.stream().map(this::toCharacterInfo).toList();
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
        FatalChainInfo fatalChainInfo = toFatalChainInfo(fatalChainMoveId, mainCharacter);
        model.addAttribute("fatalChainInfo", fatalChainInfo);

        // 에셋 추가 시작
        // 캐릭터 에셋 추가
        List<Map<String, String>> characterVideoSrcMaps = new ArrayList<>();
        List<Map<String, String>> characterAudioSrcMaps = new ArrayList<>();
        partyMembers.forEach(partyMember -> {
            List<Move> characterMoves = partyMember.getActor().getMoves().values().stream().toList();
            Map<String, String> characterVideoSrcMap = getVideoSrcMap(characterMoves);
            Map<String, String> characterAudioSrcMap = getAudioSrcMap(characterMoves);
            characterVideoSrcMaps.add(characterVideoSrcMap);
            characterAudioSrcMaps.add(characterAudioSrcMap);
        });
        model.addAttribute("characterVideoSrcMaps", characterVideoSrcMaps);
        model.addAttribute("characterAudioSrcMaps", characterAudioSrcMaps);

        // 적 에셋 추가
        Map<String, EnemyVideoInfo> enemyVideoSrcMap = getEnemyVideoSrcMap(enemy);
        model.addAttribute("enemyVideoSrcMap", enemyVideoSrcMap);
        Map<String, List<String>> enemyAudioSrcMap = getEnemyAudioSrcMap(enemy);
        model.addAttribute("enemyAudioSrcMap", enemyAudioSrcMap);

        // 소환석 에셋 추가
        Map<String, String> summonVideoSrcMap = getVideoSrcMap(summonMoves);
        model.addAttribute("summonVideoSrcMap", summonVideoSrcMap);
        Map<String, String> summonAudioSrcMap = getAudioSrcMap(summonMoves);
        model.addAttribute("summonAudioSrcMap", summonAudioSrcMap);

        // 멤버 추가
        model.addAttribute("member", member);

        return "battle";
    }

    @PostMapping("/room/join")
    @Transactional
    public String joinRoom(@ModelAttribute EnterRoomForm form,
                           @AuthenticationPrincipal PrincipalDetails principal, Model model) {
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

    private BattleCharacterInfo toCharacterInfo(BattleActor partyMember) {
        return BattleCharacterInfo.builder()
                .id(partyMember.getId())
                .name(partyMember.getName())
                .portraitSrc(partyMember.getActor().getBattlePortraitSrc())
                .statuses(partyMember.getBattleStatuses().stream().filter(battleStatus -> battleStatus.getStatus().getType().isPresentable()).toList())
                .hp(partyMember.getHp())
                .maxHp(partyMember.getMaxHp())
                .hpRate(partyMember.calcHpRate())
                .chargeGauge(partyMember.getChargeGauge())
                .maxChargeGauge(partyMember.getMaxChargeGauge())
                .abilities(partyMember.getActor().getMoves().values().stream()
                        .filter(move -> move.getType().getParentType() == MoveType.ABILITY)
                        .sorted(Comparator.comparing(Move::getType))
                        .map(move -> AbilityInfo.builder()
                                .id(move.getId())
                                .name(move.getName())
                                .info(move.getInfo())
                                .cooldown(move.getCoolDown())
                                .iconImageSrc(move.getAsset().getIconImageSrc())
                                .build())
                        .toList())
                .chargeAttack(partyMember.getActor().getMoves().get(MoveType.CHARGE_ATTACK_DEFAULT))
                .abilityCoolDowns(List.of(partyMember.getFirstAbilityCoolDown(), partyMember.getSecondAbilityCoolDown(), partyMember.getThirdAbilityCoolDown()))
                .build();
    }


    private BattleEnemyInfo toEnemyInfo(BattleEnemy enemy) {
        String omenPrefix = null;
        Integer omenValue = null;
        OmenType omenType = null;
        String omenName = null;
        String omenInfo = null;
        if (enemy.getCurrentStandbyType() != null) { // TODO 나중에 리팩토링 해야될듯
            Omen omen = enemy.getActor().getMoves().get(enemy.getCurrentStandbyType()).getOmen();
            omenPrefix = omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex()).getInfo();
            omenValue = enemy.getOmenValue();
            omenType = omen.getOmenType();
            omenName = omen.getName();
            omenInfo = omen.getInfo();
        }

        return BattleEnemyInfo.builder()
                .id(enemy.getId())
                .name(enemy.getName())
                .phase(enemy.getCurrentForm())
                .statuses(enemy.getBattleStatuses().stream().filter(battleStatus -> battleStatus.getStatus().getType().isPresentable()).toList())
                .hpRate(enemy.calcHpRate())
                .currentChargeGauge(enemy.getChargeGauge())
                .maxChargeGauge(Collections.nCopies(enemy.getMaxChargeGauge(), 1)) // 타임리프로 순회돌리려고 리스트로 넘김
                .initialMoveType(enemy.getCurrentStandbyType() == null ? MoveType.IDLE_DEFAULT : enemy.getCurrentStandbyType()) // 동적으로
                .omenActivated(enemy.getCurrentStandbyType() != null)

                .omenPrefix(omenPrefix)
                .omenValue(omenValue)
                .omenType(omenType)
                .omenName(omenName)
                .omenInfo(omenInfo)

                .build();
    }

    private FatalChainInfo toFatalChainInfo(Long fatalChainMoveId, BattleActor mainCharacter) {
        return moveRepository.findById(fatalChainMoveId).map(move ->
                FatalChainInfo.builder()
                        .id(move.getId())
                        .name(move.getName())
                        .info(move.getInfo())
                        .gaugeValue(mainCharacter.getFatalChainGauge())
                        .effectVideoSrc(move.getAsset().getEffectVideoSrc())
                        .seAudioSrc(move.getAsset().getSeAudioSrc())
                        .build()
        ).orElseGet(() -> FatalChainInfo.builder().name("없음").build());
    }

    private SummonInfo toSummonInfo(Move move, BattleActor mainCharacter) {
        return SummonInfo.builder()
                .id(move.getId())
                .name(move.getName())
                .info(move.getInfo())
                .iconImageSrc(move.getAsset().getIconImageSrc())
                .cooldown(mainCharacter.getSummonCoolDowns().get(mainCharacter.getSummonMoveIds().indexOf(move.getId())))
                .build();
    }


    private Map<String, String> getVideoSrcMap(List<Move> moves) {
        Map<String, String> videoSrcMap = new HashMap<>();
        moves.forEach(move -> {
            // 이펙트
            String effectVideoSrc = move.getAsset().getEffectVideoSrc();
            if (effectVideoSrc != null && !effectVideoSrc.isEmpty()) {
                videoSrcMap.put(effectVideoSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " effect");
            }
            // 모션
            String motionVideoSrc = move.getAsset().getMotionVideoSrc();
            if (motionVideoSrc != null && !motionVideoSrc.isEmpty()) {
                String fullSizeClassName = move.getAsset().isMotionVideoFull() ? "full-size" : "";
                videoSrcMap.put(motionVideoSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " motion " + fullSizeClassName);
            }
        });
        return videoSrcMap;
    }

    private Map<String, String> getAudioSrcMap(List<Move> moves) {
        Map<String, String> audioSrcMap = new HashMap<>();
        moves.forEach(move -> {
            // 이펙트
            String seAudioSrc = move.getAsset().getSeAudioSrc();
            if (seAudioSrc != null && !seAudioSrc.isEmpty()) {
                audioSrcMap.put(move.getAsset().getSeAudioSrc(), move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " effect");
            }
            // 보이스
            String voiceAudioSrc = move.getAsset().getVoiceAudioSrc();
            if (voiceAudioSrc != null && !voiceAudioSrc.isEmpty()) {
                audioSrcMap.put(voiceAudioSrc, move.getType().getClassName() + " " + move.getType().getParentType().getClassName() + " voice");
            }
        });
        return audioSrcMap;
    }


    /**
     * 적의 effectVideo 를 key = effectVideoSrc, value = [MoveType.parentType.className, MoveType.className1 , ...] 인 Map 으로 변환
     * ex) 같은 effectVideoSrc (standby-1.webm) 을 가지는 STAND_BY_A, STAND_BY_D 의 경우 value 를 [standby, standby-a, standby-d] 로 묶는다.
     *
     * @param enemy
     * @return
     */
    private Map<String, EnemyVideoInfo> getEnemyVideoSrcMap(BattleEnemy enemy) {
        Map<String, EnemyVideoInfo> enemyVideoSrcMap = new HashMap<>();
        enemyVideoSrcMap.putAll(enemy.getActor().getMoves().values().stream()
                .collect(Collectors.groupingBy(
                        move -> move.getAsset().getEffectVideoSrc() != null ? move.getAsset().getEffectVideoSrc() : "",
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                moves -> {
                                    List<String> classNames = new ArrayList<>(moves.stream()
                                            .map(move -> move.getType().getClassName())
                                            .toList());
                                    String parentClassName = moves.getFirst().getType().getParentType().getClassName();
                                    Integer hitEffectDelay = moves.getFirst().getAsset().getEffectHitDelay();
                                    classNames.add(parentClassName);
                                    classNames.add("effect"); // 적 이펙트
                                    return EnemyVideoInfo.builder()
                                            .effectHitDelay(hitEffectDelay)
                                            .classNames(classNames)
                                            .build();
                                }
                        )
                )));
        return enemyVideoSrcMap;
    }

    private Map<String, List<String>> getEnemyAudioSrcMap(BattleEnemy enemy) {
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
        return enemyAudioSrcMap;
    }

    @GetMapping("/insert")
    public String insert() {
        return "insert";
    }

    @GetMapping("/insert-enemy")
    public String enemyInsert() {
        return "insertEnemy";
    }

    @GetMapping("/battle")
    @Transactional
    public String battle(Model model) {

        Room findRoom = roomRepository.findById(1L).orElseThrow(() -> new IllegalArgumentException("방을 찾을수 없음"));
        Member findMember = memberRepository.findByRoomIdAndUserId(133L, 1L).orElseThrow(() -> new IllegalArgumentException("멤버를 찾을수 없음"));
        List<BattleActor> battleActors = findMember.getBattleActors();

        if (battleActors.isEmpty()) {
            return "redirect:/";
        }

        // 인포 추가 시작
        battleActors.sort(Comparator.comparing(BattleActor::getCurrentOrder));
        BattleActor enemyActor = battleActors.getFirst();
        List<BattleActor> partyMembers = battleActors.subList(1, battleActors.size());
        BattleActor mainCharacter = partyMembers.stream().filter(battleActor -> battleActor.getActor().isMainCharacter()).findFirst().orElseThrow(() -> new IllegalArgumentException("메인 캐릭터 찾을수 없음"));

        // 캐릭터 인포
        List<BattleCharacterInfo> battleCharacterInfos = partyMembers.stream().map(this::toCharacterInfo).toList();
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
        FatalChainInfo fatalChainInfo = toFatalChainInfo(fatalChainMoveId, mainCharacter);
        model.addAttribute("fatalChainInfo", fatalChainInfo);

        // 에셋 추가 시작
        // 캐릭터 에셋 추가
        List<Map<String, String>> characterVideoSrcMaps = new ArrayList<>();
        List<Map<String, String>> characterAudioSrcMaps = new ArrayList<>();
        partyMembers.forEach(partyMember -> {
            List<Move> characterMoves = partyMember.getActor().getMoves().values().stream().toList();
            Map<String, String> characterVideoSrcMap = getVideoSrcMap(characterMoves);
            Map<String, String> characterAudioSrcMap = getAudioSrcMap(characterMoves);
            characterVideoSrcMaps.add(characterVideoSrcMap);
            characterAudioSrcMaps.add(characterAudioSrcMap);
        });
        model.addAttribute("characterVideoSrcMaps", characterVideoSrcMaps);
        model.addAttribute("characterAudioSrcMaps", characterAudioSrcMaps);

        // 적 에셋 추가
        Map<String, EnemyVideoInfo> enemyVideoSrcMap = getEnemyVideoSrcMap(enemy);
        model.addAttribute("enemyVideoSrcMap", enemyVideoSrcMap);
        Map<String, List<String>> enemyAudioSrcMap = getEnemyAudioSrcMap(enemy);
        model.addAttribute("enemyAudioSrcMap", enemyAudioSrcMap);

        // 소환석 에셋 추가
        Map<String, String> summonVideoSrcMap = getVideoSrcMap(summonMoves);
        model.addAttribute("summonVideoSrcMap", summonVideoSrcMap);
        Map<String, String> summonAudioSrcMap = getAudioSrcMap(summonMoves);
        model.addAttribute("summonAudioSrcMap", summonAudioSrcMap);

        // 멤버 추가
        model.addAttribute("member", findMember);

        model.asMap().entrySet().forEach(entry -> {
            log.info("k = {}", entry.getKey());
            log.info("v = {}", entry.getValue());
        });


        return "battle";
    }
}
