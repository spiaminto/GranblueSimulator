package com.gbf.granblue_simulator.user.domain;

import lombok.Getter;

@Getter
public enum UserCharacterMoveStatus {

    IN_USE("사용중"),        // 사용중
    AVAILABLE("사용가능"),     // 사용 가능 (보유)
    UNAVAILABLE("구매하기"),    // 사용 불가 (미보유)
    
    DEFAULT("기본") // 기본, 변경불가
    ;

    private final String displayName;

    UserCharacterMoveStatus(String displayName) {
        this.displayName = displayName;
    }
}
