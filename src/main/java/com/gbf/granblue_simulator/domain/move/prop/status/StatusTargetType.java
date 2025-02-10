package com.gbf.granblue_simulator.domain.move.prop.status;

public enum StatusTargetType {
    SELF,   // 자기자신
    PARTY_MEMBERS,  // 아군 전체

    ENEMY,  // 적
    
    ALL_PLAYERS,    // 참전자 전체
    
    NEXT_CHARACTER  // 다음 캐릭터
}
