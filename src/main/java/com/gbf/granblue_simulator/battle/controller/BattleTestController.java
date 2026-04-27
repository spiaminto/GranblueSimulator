package com.gbf.granblue_simulator.battle.controller;


import com.gbf.granblue_simulator.battle.service.BattleCommandRequest;
import com.gbf.granblue_simulator.battle.service.MemberService;
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
import java.util.Map;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BattleTestController {

    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final BattleContext battleContext;
    private final BattleCommandService battleCommandService;
    private final BattleResponseMapper responseMapper;

    private final BattleController battleController;

    /**
     * 테스트 페이지용 메서드
     */
    // @GetMapping("/battle")
    @Transactional
    public String battlePage(Model model) {

        Long roomId = 211L;
        Member findMember = memberService.findByRoomIdAndUserId(roomId, 2L).orElseThrow(() -> new IllegalArgumentException("멤버를 찾을수 없음"));

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
                                                               @RequestBody Map<String, String> body) {
        log.info("body = {} ", body);
        Long memberId = Long.parseLong(body.get("memberId"));
        Member member = memberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("없는 멤버"));
        if (!member.getUser().getRole().equals("ROLE_ADMIN")) throw new IllegalArgumentException("권한이 없습니다.");

        battleCommandService.resetCooldowns(BattleCommandRequest.of(memberId));
        List<MoveLogicResult> syncResults = battleCommandService.sync(BattleCommandRequest.of(memberId));
        List<BattleResponse> syncResponse = responseMapper.toBattleResponse(syncResults);

        log.debug("syncResponse: {}", syncResponse);

        return ResponseEntity.ok(syncResponse);

    }
}
