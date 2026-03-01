package com.gbf.granblue_simulator.battle.logic.move.summon;

import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Component
@Slf4j
public class SummonMoveLogic extends SummonDefaultLogic {


    public SummonMoveLogic(CharacterMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        logicRegistry.register("summon_2040425000", this::zeus);
        logicRegistry.register("summon_2040100000", this::varuna);
        logicRegistry.register("summon_2040056000", this::lucifel);
        logicRegistry.register("summon_2040413000", this::wamdus);
    }

    // 제우스: 적에게 8배 데미지 2회, 장악 효과. 아군 전체에 오의 게이지 상승률 증가 효과
    protected MoveLogicResult zeus(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 바루나: 적에게 12배 데미지, 부패효과. 아군 전체에 트리플 어택 확률 증가, 화속성 피격 데미지 경감 효과
    protected MoveLogicResult varuna(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 바루나: 적에게 12배 데미지, 부패효과. 아군 전체에 트리플 어택 확률 증가, 화속성 피격 데미지 경감 효과
    protected MoveLogicResult lucifel(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }

    // 바루나: 적에게 12배 데미지, 부패효과. 아군 전체에 트리플 어택 확률 증가, 화속성 피격 데미지 경감 효과
    protected MoveLogicResult wamdus(MoveLogicRequest request) {
        return defaultSummon(request.getMove());
    }
}
