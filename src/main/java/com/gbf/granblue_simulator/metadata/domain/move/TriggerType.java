package com.gbf.granblue_simulator.metadata.domain.move;

import lombok.Getter;

/**
 * Move 의 반응 시점별 트리거, 1개의 행동당 1개만.
 */
public enum TriggerType {

    // 전투 시작
    BATTLE_START,

    // 아군 공격 턴 시작
    CHARACTER_TURN_START,

    // 아군 캐릭터 (1) 공격행동 시작
    SELF_STRIKE_START,
    CHARACTER_STRIKE_START,
    
    // 아군 캐릭터 (1) 공격행동 종료
    SELF_STRIKE_END,
    CHARACTER_STRIKE_END,

    // 아군 캐릭터 (1) 모든 공격행동 종료
    SELF_STRIKE_ALL_END,
    CHARACTER_STRIKE_ALL_END,

    // 아군 공격 턴 종료
     CHARACTER_TURN_END,

    // 적 공격 턴 시작
     ENEMY_TURN_START,

    // 적 공격행동 시작
    ENEMY_STRIKE_START,

    // 적 공격 행동 종료
     ENEMY_STRIKE_END,
    
    // 적 모든 공격 행동 종료
    ENEMY_STRIKE_ALL_END,

    // 적 공격 턴 종료
     // ENEMY_TURN_END, //적 모든 공격 행동 종료로 갈음
    
    // 턴 종료시
    TURN_END_ACT_STATUS, // 즉발 상태효과
    TURN_END, // 기본
    TURN_END_OMEN, // 전조페이즈
    TURN_FINISH, // 턴 진행으로 인한 상태효과 등의 모든 처리 종료시

    // 반응
    REACT_SELF(1), // 캐릭터가, 자신이 XX시
    REACT_ENEMY(5), // 적이 XX 시
    REACT_CHARACTER(10), // 캐릭터가 XX 시 (적 전조 해제시도 이쪽)
    // actor 순서가 0,1,2,3,4 고정이라면
    // 1. '자신의 행동에 대한 반응'이 항상 '적의 캐릭터에 대한 반응' 보다 빠르다.
    // 2. '적의 캐릭터에 대한 반응' 은 항상 '캐릭터의 캐릭터에 대한 반응' 보다 빠르다.
    // 3. '적의 행동에 대한 [적/캐릭터] 의 반응' 은 적 이 항상 빠르다.

    // 기타
    SUMMON, // 소환시

    NONE // 없음, null
    ,

    ;

    @Getter
    private final int reactionOrder;

    TriggerType() {
        this.reactionOrder = 999;
    }

    TriggerType(int order) {
        this.reactionOrder = order;
    }

    public boolean isNone() {
        return this == NONE;
    }
}
