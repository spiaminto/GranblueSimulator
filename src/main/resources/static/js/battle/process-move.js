async function processResponseMoves(responses, requestType = '') {
    // 파싱
    let moveResponses = parseMoveResponseList(responses);
    console.log('===[processResponseMoves]================================================================== \n moveResponses = \n', moveResponses);
    // moveResponses.forEach(response => response.print());

    // 결과 리스트에 폼체인지 결과가 있을경우 에셋 미리 로드
    if (moveResponses.some(response => response.isEnemyFormChange)) {
        waitingProcess(true);
        await loadNextEnemyActor();
        waitingProcess(false);
    }

    // 멤버 표시 갱신
    requestMembersInfo();
    // 채팅 갱신
    requestChat();

    let isTurnRequest = requestType === 'turn';
    let isMoveRequest = requestType === 'move';
    if (isTurnRequest) {
        // 파티 턴 시작 인디케이터 표시
        $('.turn-playing-indicator.party').fadeIn(100).delay(800).fadeOut(100);
        await wait(500);
    }

    let enemyTurnIndicatorShowed = false;
    for (const response of moveResponses) {
        let actorOrder = response.actorOrder; // mainActorOrder, 0: enemy, 1~ character
        let isEnemyResponse = actorOrder === 0;
        console.log('charOrder = ' + response.actorOrder + '\n response = ', response);

        let parentType = response.moveType.getParentType();
        if (isEnemyResponse && !enemyTurnIndicatorShowed
            && (parentType === MoveType.ATTACK // 적이 카운터 일경우, !== MoveType.ATTACK_COUNTER 추가?
                || parentType === MoveType.CHARGE_ATTACK)) {
            $('.turn-playing-indicator.enemy').fadeIn(100).delay(800).fadeOut(100); // 적 턴 시작 인디케이터 표시
            await wait(500);
            enemyTurnIndicatorShowed = true;
        }

        stage.processing.response = response;
        let nextResponse = moveResponses[moveResponses.indexOf(response) + 1];

        updateBgm(response);

        switch (parentType) {
            case MoveType.SUPPORT_ABILITY:
            case MoveType.ABILITY:
                isEnemyResponse ? await processEnemyAbility(response) : await processAbility(response);
                break;
            case MoveType.ATTACK:
                isEnemyResponse ? await processEnemyAttack(response) : await processAttack(response);
                break;
            case MoveType.CHARGE_ATTACK:
                window.gameStateManager.setState('indicator.moveName', response.moveName);
                isEnemyResponse ? await processEnemyChargeAttack(response) : await processChargeAttack(response)
                break;
            case MoveType.STANDBY:
                await processEnemyStandBy(response);
                break;
            case MoveType.GUARD:
                // 가드 연하게 + se 재생
                $(`#actorContainer > .actor-${response.actorOrder} .guard-status`).removeClass('guard-on').addClass('guard-on-processing');
                playSe(Sounds.global.GUARD_WAIT.src);
                break;
            case MoveType.FATAL_CHAIN:
                await processFatalChain(response);
                break;
            case MoveType.SUMMON:
                window.gameStateManager.setState('indicator.moveName', response.moveName);
                let unionSummonResponse = moveResponses.find(response => response.moveType === MoveType.UNION_SUMMON);
                await processSummon(response, unionSummonResponse);
                break;
            case MoveType.TURN_END:
                await processTurnEndProcess(response);
                break;
            case MoveType.DEAD:
                isEnemyResponse ? await processEnemyDead(response) : await processCharacterDead(response);
                break;
            case MoveType.ETC:
                if (response.moveType === MoveType.STRIKE_SEALED) await processStrikeSealed(response);
                if (response.moveType === MoveType.SYNC) await processSync(response);
                break;
            case MoveType.ROOT:
            default:
                console.log('[processResponseMoves] invalid type]', response.moveType);
        }

        // 후딜레이
        let globalDelay = Constants.Delay.globalMoveDelay;
        if (nextResponse && parentType === MoveType.ATTACK && [MoveType.SUPPORT_ABILITY, MoveType.ABILITY].includes(nextResponse.moveType.getParentType())) {
            globalDelay /= 2;
        }
        await wait(globalDelay);
    }

    // 동기화 '요청' 후처리 (응답 동기화 x)
    if (requestType === 'sync') {
        let response = responses[0];
        let hasEffect = response.addedBuffStatusesList.find(addedBuffStatuses => addedBuffStatuses.length > 0) || response.heals.find(heal => heal > 0); // 참전자 버프로 인한 효과 있음
        if (hasEffect) {
            stage.processing.response.hasEffect = true; // 참전자 효과 있을경우 어빌리티 레일 처리 늦추기 위해
        }
    }

    // 커맨드실행 후처리
    // console.log('[processResponseMoves] isMoveRequest = ', isMoveRequest, ' isTurnRequest = ', isTurnRequest, ' type = ', type);
    let isCommandExecuted = isMoveRequest || isTurnRequest;
    if (isCommandExecuted) { // 공통
        $('.ability-rail-wrapper .rail-item').eq(0).remove(); // 어빌리티 레일 첫번째 제거

        // 데미지 래퍼, 상태효과 래퍼 정리 (미리 캡쳐 후 삭제)
        let $damageWrappers = $('#actorContainer .damage-wrapper');
        let $statusEffectWrappers = $('#actorContainer .status-effect-wrapper');
        setTimeout(() => {
            $damageWrappers.remove();
            $statusEffectWrappers.remove();
        }, Constants.Delay.damageShowToNext);

        if (isTurnRequest) { // 턴 진행 커맨드 추가 후처리
            // 가드 해제
            $('#actorContainer .guard-status').removeClass('guard-on-processing');
            gameStateManager.setState('guardStates', [null, false, false, false, false]);

            // 클리어 또는 실패시가 아닐때
            if (!gameStateManager.getState('isQuestCleared') && !gameStateManager.getState('isQuestFailed')) {
                // 공격 상태 및 플레이어 상태 정상화
                gameStateManager.setState('isAttackClicked', false);
                player.lockPlayer(false);

                // 소환 가능하도록 변경
                gameStateManager.setState('usedSummon', false, {force: true});

                // 턴 갱신
                playSe(Sounds.ui.TURN_INDICATOR.src);
                setTimeout(() => gameStateManager.setState('currentTurn', gameStateManager.getState('currentTurn') + 1), 300);

                // 튜토리얼시 진행
                if (gameStateManager.getState('tutorialIndex') != null) {
                    gameStateManager.setState('tutorialIndex', Math.min(gameStateManager.getState('tutorialIndex') + 1, 5));
                    setTimeout(() => $('#tutorialModalButton').click(), 1000);
                }
            }
        }
    }

    // 상태처리
    let lastProcessedResponse = stage.processing.response;
    gameStateManager.setState('enemyEstimatedAtk', lastProcessedResponse.enemyEstimatedAtk);
}

async function processSync(response) {
    // 데미지 처리 X

    // 공헌도 반영
    window.gameStateManager.setState('indicator.moveResultHonor', response.resultHonor);

    // 합체소환 id 반영,
    if (!!response.unionSummonInfo) // null 이 아닌경우에만 사용하므로 타이밍을 분리하지 않는한 이게 최선인듯
        gameStateManager.setState('unionSummonInfo', response.unionSummonInfo, {force: true}); // 턴 시작시 SYNC 에서 갱신되더라도, 턴 종료후 SYNC 에서 재갱신 하도록 해서 찬스 보이게

    let hasAddedBuffStatusEffects = response.addedBuffStatusesList.find(addedBuffStatuses => addedBuffStatuses.length > 0);
    let hasHeals = response.heals.find(heal => heal > 0);
    let effectDuration = 0;
    if (hasAddedBuffStatusEffects || hasHeals) { // 참전자 버프 있음 or 참전자 힐 있음
        // 참전자 효과 인디케이터 렌더링
        let forMemberAbilityInfo = response.forMemberAbilityInfo;
        let sourceUsername = forMemberAbilityInfo.sourceUsername;
        let moveName = forMemberAbilityInfo.moveName;
        let $forAllMoveIndicatorContainer = $('#battleCanvas .for-all-move-indicator-container');
        let $forAllMoveIndicator = $(`
          <div class="for-all-move-indicator">
            <span class="username"><b>${sourceUsername}</b> 가</span>
            <span class="moveName"><b>${moveName}</b> 사용!</span>
          <div>
        `);
        $forAllMoveIndicatorContainer.append($forAllMoveIndicator);
        $forAllMoveIndicator.addClass('show').one('animationend', () => {
            $forAllMoveIndicator.remove();
        });

        // 이펙트 처리
        effectDuration = await player.play(Player.playRequest(player.getGlobalActor().actorId, Player.c_animations.ABILITY_EFFECT_ONLY, {
            cjsName: response.visualInfo.moveCjsName,
            isTargetedEnemy: response.visualInfo.isTargetedEnemy
        }), true);
    }

    // 스테이터스 처리
    let totalEndTime = await processStatusEffect(response);

    console.log('[processSync] DONE totalTime', totalEndTime, ' effectDuration ', effectDuration);
    return totalEndTime;
}


async function processStrikeSealed(response) {
    // 공격불가 효과 보여주기 위해 임시 스테이터스 생성
    let actorOrder = response.actorOrder;
    let tempAddedDebuffStatusesList = [[], [], [], [], []]; // CHECK 원본 response 수정하지 말것
    tempAddedDebuffStatusesList[actorOrder].push(
        new StatusDto({
            type: 'DEBUFF',
            name: '공격불가',
            imageSrc: '',
            effectText: '공격불가',
            durationType: 'TURN',
            duration: '1',
        })
    );
    // 모션 재생
    player.play(Player.playRequest(`actor-${actorOrder}`, Player.c_animations.ABILITY_MOTION_DAMAGE, {abilityType: BASE_ABILITY.STRIKE_SEALED.name}));
    // 공격불가 디버프 동시 표시
    let debuffDelay = processDebuffEffect(tempAddedDebuffStatusesList);
    await wait(debuffDelay);

    // 상태 효과 처리
    let totalEndTime = await processStatusEffect(response);

    console.log('[processStrikeSealed] DONE totalTime', totalEndTime);
    return totalEndTime;
}

async function processTurnEndProcess(response) {

    // 스테이터스 처리
    let processStatusDuration = await processStatusEffect(response, 0.5);

    console.log('[processTurnEndProcess] DONE type = ', response.moveType.name, ' processStatusDuration = ', processStatusDuration);
    return processStatusDuration;
}

/**
 * 웨이팅 띄우기
 * @param toWaiting true: to waiting, false: to normal
 */
function waitingProcess(toWaiting) {
    if (toWaiting) {
        $('.waiting-video-container').css('visibility', 'visible').find('.waiting-video').get(0).play();
        // $('#container').addClass('deActivated');
    } else {
        $('.waiting-video-container').css('visibility', 'hidden').find('.waiting-video').get(0).pause();
        // $('#container').removeClass('deActivated');
    }
}