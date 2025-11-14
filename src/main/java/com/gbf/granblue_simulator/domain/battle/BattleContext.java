package com.gbf.granblue_simulator.domain.battle;

import com.gbf.granblue_simulator.domain.Member;
import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 현재 전투 상태를 표현하는 컨텍스트
 * 이곳에서 각 엔티티의 값을 직접 수정하는 일이 없도록 할 것
 * 이 컨텍스트는 외부에서 읽기 전용으로만 사용하며, 
 * 액터 로직에 따른 전투 상황의 변화 발생시 최소한의 수정을 통해 현재 상태를 정확히 나타내는것을 목표로 함
 */
@Slf4j
@Getter @ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Component @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS) // 싱글톤에서 프록시로 접근, 자동 GC
public class BattleContext {

    private Member member;

    private Actor mainCharacter;
    private Actor enemy;

    private List<Actor> frontCharacters; // valid front
    private List<Actor> subCharacters; // valid sub

    private List<Actor> allActors; // with non-valid front and back
    /**
     * 현재 필드에 존재하는 적:0, 아군1 ~ (프론트멤버만)
     */
    private List<Actor> currentFieldActors; // enemy and valid front

    /**
     * mainCharacter (행동주체, 캐릭터) 설정
     * @param mainCharacter
     */
    public void setMainCharacter(Actor mainCharacter) {
        this.mainCharacter = mainCharacter;
    }

    public void init(Member member, Long mainCharacterId) {
        this.member = member;
        this.allActors = member.getActors().stream()
                .sorted(Comparator.comparing(Actor::getCurrentOrder))
                .toList();
        if (allActors.isEmpty()) throw new IllegalArgumentException("[init] member.battleActors is empty");

        allActors.forEach(actor -> actor.getStatus().syncStatus());

        this.enemy = allActors.get(0);
        if (enemy.getCurrentOrder() != 0) throw new IllegalArgumentException("[init] enemy.getCurrentOrder() is not 0, enemy.currentOrder = " + enemy.getCurrentOrder());

        this.frontCharacters = allActors.stream()
                .filter(battleActor -> battleActor.getCurrentOrder() > 0 && battleActor.getCurrentOrder() <= 4)
                .collect(Collectors.toList());// 1, 2, 3, 4
        //CHECK 나중에 이거 별도로 엔티티로 관리해야할듯. 캐릭터 사망시 실시간으로 갱신해줘야댐.

        this.subCharacters = allActors.stream()
                .filter(battleActor -> battleActor.getCurrentOrder() > 4 && battleActor.getCurrentOrder() <= 6)
                .toList();

        // 행동주체 확인 (다른 방법도 고려)
        if (mainCharacterId != null) {
            this.mainCharacter = frontCharacters.stream()
                    .filter(battleActor -> battleActor.getId().equals(mainCharacterId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("[setActorFieldByMember] mainCharacterId provided but not found, mainCharacterId = " + mainCharacterId));
        }

        syncCurrentFieldActors();
        log.info("[init] this = {}", this);
    }

    /**
     * 주인공을 반환
     *
     * @return
     */
    public Actor getLeaderCharacter() {
        return allActors.stream().filter(actor -> actor.getBaseActor().isLeaderCharacter()).findFirst().orElse(null);
    }

    /**
     * 프론트 사망 처리
     * @param deadActor
     * @param firstSubCharacter  프론트로 나올 첫 서브멤버. 없으면 null
     */
    public void frontCharacterDead(Actor deadActor, Actor firstSubCharacter) {
        this.frontCharacters.remove(deadActor); // allActors 에는 남아있음

        if (firstSubCharacter != null) {
            this.subCharacters.remove(firstSubCharacter);
            this.frontCharacters.set(firstSubCharacter.getCurrentOrder() - 1, firstSubCharacter);
        }

        syncCurrentFieldActors();
    }

    /**
     * 현재 필드위의 캐릭터, 적을 갱신
     */
    protected void syncCurrentFieldActors() {
        this.currentFieldActors = new ArrayList<>(this.frontCharacters);
        this.currentFieldActors.addFirst(this.enemy);
    }

}
