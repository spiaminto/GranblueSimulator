package com.gbf.granblue_simulator.domain.move.prop.status;

public enum StatusTargetType {
    SELF,   // 자기자신
    SELF_AND_MAIN_CHARACTER, // 자신과 주인공
    SELF_AND_NEXT_CHARACTER,  // 자신과 다음 캐릭터

    MAIN_CHARACTER, // 주인공

    PARTY_MEMBERS,  // 아군 전체
    PARTY_MEMBERS_NOT_SELF, // 자신을 제외한 아군전체

    ENEMY,  // 적

    ALL_PARTY_MEMBERS,    // 참전자 아군 전체
    ALL_ENEMIES // 참전자 적 전체
    ;

    public boolean isAllTarget() {
        return this == ALL_PARTY_MEMBERS || this == ALL_ENEMIES;
    }

}
