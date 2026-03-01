package com.gbf.granblue_simulator.metadata.domain.move;

/**
 * Move 의 반응 시점 내에서 처리할 순서 페이즈. ReactionLogic 에서 반응 시점별 정렬시 사용
 */
public enum TriggerPhase {
    // 기본적으로 상태효과를 우선, 상태변경후 어빌리티 자동발동

    STATUS_IMMEDIATE(20), // 즉발기, 구현후 확인 필요
    // ㄴ 카운터 효과

    STATUS_PRE(30), 

    ABILITY_PRE(40),
    // ㄴ 적 특수기 사용시 자동발동
    
    STATUS(50),
    // ㄴ 기본 상태효과 부여: XX 일때 자신에게 YY 효과
    
    ABILITY(60),
    // ㄴ 기본 자동발동 어빌리티: 자신이 XX 상태일때 / 효과중 / 효과적용시 자동발동

    STATUS_POST(70),
    // ㄴ 피격된 턴 종료시 효과
    
    ABILITY_POST(80),


    NONE(999) // null
    ;

    final int phase;

    TriggerPhase(int i) {
        this.phase = i;
    }

    public boolean isNone() {
        return this == NONE;
    }

}
