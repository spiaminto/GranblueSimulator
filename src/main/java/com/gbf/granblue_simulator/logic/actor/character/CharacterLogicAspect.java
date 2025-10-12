package com.gbf.granblue_simulator.logic.actor.character;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.List;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class CharacterLogicAspect {

    private final CharacterLogicResultMapper characterLogicResultMapper;

    @Pointcut("execution(* com.gbf.granblue_simulator.logic.actor.character.*.postProcessToPartyMove(..)) " +
            "&& target(com.gbf.granblue_simulator.logic.actor.character.CharacterLogic)")
    public void postProcessPartyMove() {}


    @Pointcut("execution(* com.gbf.granblue_simulator.logic.actor.character.*.postProcessToEnemyMove(..)) " +
            "&& target(com.gbf.granblue_simulator.logic.actor.character.CharacterLogic)")
    public void postProcessEnemyMove() {}

    /**
     * 캐릭터의 반응 처리 이전에 사망여부 확인후 사망처리하는 Aspect
     * 사망시 반응처리 없이 사망결과를 반환
     * @param joinPoint
     * @return
     * @throws Throwable
     */
    @Around("(postProcessPartyMove() || postProcessEnemyMove())")
    public Object checkDeath(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        BattleActor mainActor = (BattleActor) args[0];

        if (mainActor.getCurrentOrder() >= 100) { // 이미 사망한 멤버인지 확인
            log.info("[checkDeath] alreadyDead, actor = {}", mainActor);
            // battleLogic.postProcessToMove 내부 재귀반응에서, 캐릭터 사망시 기존의 partyMembers 를 갱신하는것이 어렵다고 판단,
            // '이미 사망한' 멤버의 경우 전부 빈 결과를 반환하도록 한다.
            // '사망한 시점' 에는 DEAD 결과를 반환하여 '파티멤버 사망시~' 효과를 발동시킬수 있도록 한다 (아래에서 처리)
            return characterLogicResultMapper.emptyResult();
        }

        if (mainActor.isDead() && mainActor.getCurrentOrder() <= 4) { // 사망한 프론트멤버인지 확인
            log.info("[checkDeath] nowDead, actor = {}", mainActor);
            BattleActor enemy = (BattleActor) args[1];
            List<BattleActor> partyMembers = (List<BattleActor>) args[2];
            ActorLogicResult partyMoveResult = (ActorLogicResult) args[3];
            CharacterLogic logic = (CharacterLogic) joinPoint.getTarget();
            ActorLogicResult deathResult = logic.defaultDeath(mainActor, enemy, partyMembers, partyMoveResult);

            if (deathResult.getMoveType() != MoveType.NONE) { // 불사 등으로 버티는 경우 빈 결과 반환
                return deathResult; // 사망시 즉시 반환
            }
        }

        // 사망이 아니면 원래 메서드 실행
        return joinPoint.proceed();
    }

}
