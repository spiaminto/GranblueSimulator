package com.gbf.granblue_simulator.battle.controller;


import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import com.gbf.granblue_simulator.battle.controller.dto.request.MoveRequest;
import com.gbf.granblue_simulator.battle.controller.dto.response.BattleResponse;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.repository.MemberRepository;
import com.gbf.granblue_simulator.battle.service.BattleCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BattleTestController {

    private final MemberRepository memberRepository;
    private final BattleContext battleContext;
    private final BattleCommandService battleCommandService;
    private final BattleResponseMapper responseMapper;

    private final BattleController battleController;

    /**
     * 테스트 페이지용 메서드
     */
    @GetMapping("/battle")
    @Transactional
    public String battlePage(Model model) {

        Long roomId = 211L;
        Member findMember = memberRepository.findByRoomIdAndUserId(roomId, 2L).orElseThrow(() -> new IllegalArgumentException("멤버를 찾을수 없음"));
        battleContext.init(findMember, null);

        // model 에 정보추가
        battleController.setInfoAttributes(model, findMember);

        model.asMap().entrySet().forEach(entry -> {
//            log.info("k = {}", entry.getKey());
//            log.info("v = {}", entry.getValue());
        });

        return "battle/battle";
    }

    @PostMapping("/test/reset-cooldowns")
    public ResponseEntity<List<BattleResponse>> resetCooldowns(@AuthenticationPrincipal PrincipalDetails principalDetails,
                                                               @RequestBody MoveRequest request) {
        log.info("request: {}", request);
        long memberId = request.getMemberId();
        Member findMember = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
//        if (!Objects.equals(findMember.getUser().getId(), principalDetails.getId())) throw new IllegalArgumentException("잘못된 요청");

        battleContext.init(findMember, null);
        battleContext.getFrontCharacters().forEach(partyMember -> partyMember.updateAbilityCooldowns(0, MoveType.FIRST_ABILITY, MoveType.SECOND_ABILITY, MoveType.THIRD_ABILITY, MoveType.FOURTH_ABILITY));
        battleContext.getFrontCharacters().forEach(Actor::resetAbilityUseCount);
        Actor leaderCharacter = battleContext.getLeaderCharacter();
        leaderCharacter.getSummons().forEach(summonMove -> {
            summonMove.updateCooldown(0);
        });

        List<MoveLogicResult> syncResults = battleCommandService.sync();
        List<BattleResponse> syncResponse = responseMapper.toBattleResponse(syncResults);

        log.info("syncResponse: {}", syncResponse);

        return ResponseEntity.ok(syncResponse);

    }
}
