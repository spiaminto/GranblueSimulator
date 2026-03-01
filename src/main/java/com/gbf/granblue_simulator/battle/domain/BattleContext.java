package com.gbf.granblue_simulator.battle.domain;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
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
@Getter
@ToString
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS) // 싱글톤에서 프록시로 접근, 자동 GC
public class BattleContext {

    private Member member;

    private Actor requestMainActor; // 요청시 지정한 actor (로직에서 변경안됨)
    private Actor mainActor; // '현재' 행동 주체 (로직에서 변경됨)

    private Actor enemy;

    private List<Actor> allCharacters;
    private List<Actor> frontCharacters; // valid front
    private List<Actor> subCharacters; // valid sub

    private List<Actor> allActors; // with non-valid front and back

    /**
     * 현재 필드에 존재하는 적:0, 아군1 ~ (프론트멤버만)
     */
    private List<Actor> currentFieldActors; // enemy and valid front

    private Long commandAbilityId; // 요청 커맨드 어빌리티 id, 없으면 -1

    /**
     * mainCharacter (행동주체, 캐릭터) 설정 <br>
     * 모든 로직 구간에서 사용하는 상태를 직접 관리하므로, 최소한 변경구간을 통일 <br>
     * 현재 MoveDefaultLogic, summonLogic, syncLogic, TurnEndStatusLogic 에서 사용중
     *
     * @param mainActor
     */
    public void setCurrentMainActor(Actor mainActor) {
        this.mainActor = mainActor;
    }

    public void init(Member member, Long mainCharacterId) {
        init(member, mainCharacterId, null);
    }

    public void init(Member member, Long requestMainActorId, Long commandAbilityId) {
        this.member = member;
        this.commandAbilityId = commandAbilityId != null ? commandAbilityId : -1L;
        this.allActors = member.getActors().stream()
                .sorted(Comparator.comparing(Actor::getCurrentOrder))
                .toList();
        if (allActors.isEmpty()) throw new IllegalArgumentException("[init] member.battleActors is empty");

        List<Actor> allCharacters = new ArrayList<>();
        List<Actor> frontCharacters = new ArrayList<>();
        List<Actor> subCharacters = new ArrayList<>();
        allActors.forEach(actor -> actor.getStatus().syncStatus());
        for (Actor actor : allActors) {
            if (actor.getCurrentOrder() == 0) {
                this.enemy = actor;
            } else {
                allCharacters.add(actor);
                if (actor.getCurrentOrder() > 0 && actor.getCurrentOrder() <= 4) frontCharacters.add(actor);
                else subCharacters.add(actor);

                if (actor.getId().equals(requestMainActorId)) {
                    this.requestMainActor = actor;
                }
            }
        }
        this.allCharacters = List.copyOf(allCharacters);
        this.frontCharacters = List.copyOf(frontCharacters);
        this.subCharacters = List.copyOf(subCharacters);

        if (requestMainActorId == null) {
            // requestMainActorId 가 지정되지 않는 경우, 첫번째 캐릭터, 혹은 적 을 임시로 set (SYNC)
            this.mainActor = !frontCharacters.isEmpty() ? frontCharacters.getFirst() : enemy;
        } else if (this.requestMainActor == null) {
            // requestMainActorId는 있는데 못찾음
            throw new IllegalArgumentException("[init] requestMainActorId provided but not found, requestMainActorId = " + requestMainActorId);
        } else {
            this.mainActor = this.requestMainActor;
        }

        syncCurrentFieldActors();
//        this.print();
    }

    /**
     * 주인공을 반환 <br>
     * 프론트가 아니라 전체캐릭터 중에서 반환하므로, null 인경우 없음. 죽었는지 여부는 확인 필요
     *
     * @return leaderActor, not null
     */
    public Actor getLeaderCharacter() {
        return allActors.stream().filter(actor -> actor.getBaseActor().isLeaderCharacter()).findFirst().orElse(null);
    }

    /**
     * 프론트 사망 처리, 기본적으로 battleContext 의 멤버들은 불변이기 떄문에 외부에서 수정 시도시 오류남. 이걸로 직접 수정
     *
     * @param deadActor
     */
    public void frontCharacterDead(Actor deadActor) {
        this.frontCharacters = frontCharacters.stream()
                .filter(actor -> actor != deadActor)
                .toList();

        // 서브 캐릭터는 즉시 집어넣는게 아니고, 턴 종료 처리 직후 별도의 타이밍에 집어넣어야함.
//        Actor firstSubCharacter = this.getSubCharacters().stream().findFirst()
//                .map(firstSubMember -> {
//                    firstSubMember.updateCurrentOrder(deadActor.getCurrentOrder());
//                    return firstSubMember;
//                }).orElseGet(() -> null);
//        if (firstSubCharacter != null) {
//            this.subCharacters.remove(firstSubCharacter);
//            this.frontCharacters.set(firstSubCharacter.getCurrentOrder() - 1, firstSubCharacter);
//        }

        syncCurrentFieldActors();
    }

    /**
     * 현재 필드위의 캐릭터, 적을 갱신
     */
    protected void syncCurrentFieldActors() {
        List<Actor> tempCurrentFieldActors = new ArrayList<>(this.frontCharacters);
        tempCurrentFieldActors.addFirst(this.enemy);
        this.currentFieldActors = List.copyOf(tempCurrentFieldActors);
    }

    public void print() {
        if (this.member == null) {
            log.warn("[print] \nbattleContext.print \n member is null");
        } else if (this.allActors == null) {
            log.warn("[print] \nbattleContext.print \n member = {} \n allActors is null", member);
        } else {
            log.info("[print] \nbattleContext.print \n member = {} \n requestMainActor = {} \n mainActor = {} \n allActors = \n  {}", member, requestMainActor, mainActor, allActors.stream().map(Actor::toString).collect(Collectors.joining("\n  ")));
        }
    }

    public int getCurrentTurn() {
        return this.member.getCurrentTurn();
    }

}
