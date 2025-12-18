async function processResponseMoves(responses, type = '') {
    // 파싱
    let moveResponses = parseMoveResponseList(responses);
    console.log('===[processResponseMoves]================================================================== \n moveResponses = \n', moveResponses);
    // moveResponses.forEach(response => response.print());

    // 결과 리스트에 폼체인지가 있을경우 에셋 미리 로드
    if (moveResponses.some(response => response.moveType === MoveType.FORM_CHANGE_DEFAULT)) {
        waitingProcess(true);
        await loadNextEnemyActor();
        waitingProcess(false);
    }

    // 멤버 표시 갱신
    requestMembersInfo();

    let isTurnRequest = type === 'turn';
    let isMoveRequest = type === 'move';
    if (isTurnRequest) {
        // 파티 턴 시작 인디케이터 표시
        $('.turn-playing-indicator.party').fadeIn(100).delay(800).fadeOut(100);
    }

    let enemyTurnIndicatorShowed = false;
    for (const response of moveResponses) {
        let actorOrder = response.actorOrder; // mainActorOrder, 0: enemy, 1~ character
        // console.log('charOrder = ' + charOrder + ' response = ' + response);

        if (actorOrder === 0 && !enemyTurnIndicatorShowed
            && (response.moveType.getParentType() === MoveType.ATTACK // 적이 카운터 일경우, !== MoveType.ATTACK_COUNTER 추가?
                || response.moveType.getParentType() === MoveType.CHARGE_ATTACK)) {
            $('.turn-playing-indicator.enemy').fadeIn(100).delay(800).fadeOut(100); // 적 턴 시작 인디케이터 표시
            enemyTurnIndicatorShowed = true;
        }

        stage.processingResponse = response;
        switch (response.moveType.getParentType()) {
            case MoveType.SUPPORT_ABILITY:
            case MoveType.ABILITY:
                window.gameStateManager.setState('indicator.moveName', response.moveName);
                actorOrder !== 0 ? await processAbility(response) : await processEnemyAbility(response);
                break;
            case MoveType.ATTACK:
                actorOrder !== 0 ? await processAttack(response) : await processEnemyAttack(response);
                break;
            case MoveType.CHARGE_ATTACK:
                window.gameStateManager.setState('indicator.moveName', response.moveName);
                actorOrder !== 0 ? await processChargeAttack(response) : await processEnemyChargeAttack(response);
                break;
            case MoveType.STANDBY:
                await processEnemyStandBy(response);
                break;
            case MoveType.BREAK:
                await processEnemyBreak(response);
                break;
            case MoveType.FORM_CHANGE:
                response.moveType === MoveType.FORM_CHANGE_DEFAULT ? await processFormChange(response) : null; // FORM_CHANGE_ENTRY 는 무시
                break;
            case MoveType.GUARD:
                $(`#actorContainer > .actor-${response.actorOrder} .guard-status`).removeClass('guard-on').addClass('guard-on-processing'); // 가드 이펙트 연하게
                break;
            case MoveType.FATAL_CHAIN:
                await processFatalChain(response);
                break;
            case MoveType.SUMMON:
                window.gameStateManager.setState('indicator.moveName', response.moveName);
                await processSummon(response);
                break;
            case MoveType.TURN_END:
                await processTurnEndProcess(response);
                // $('#actorContainer .guard-status').removeClass('guard-on-processing'); // 가드 해제
                break;
            case MoveType.DEAD:
                if (actorOrder === 0) {
                    await processEnemyDead(response);
                    return;
                } else {
                    await processCharacterDead(response);
                }
                break;
            case MoveType.ETC:
                if (response.moveType === MoveType.STRIKE_SEALED) await processStrikeSealed(response);
                if (response.moveType === MoveType.SYNC) await processSync(response);
                break;
            case MoveType.ROOT:
            default:
                console.log('[processResponseMoves] invalid type]', response.moveType);
        }
    }

    // 커맨드 공통 후처리
    $('.ability-rail-wrapper .rail-item').eq(0).remove(); // 어빌리티 레일 첫번째 제거

    // 커맨드별 추가 후처리
    // console.log('[processResponseMoves] isMoveRequest = ', isMoveRequest, ' isTurnRequest = ', isTurnRequest, ' type = ', type);
    if (isMoveRequest) {
        // ...
    } else if (isTurnRequest) {
        // 가드 해제
        $('#actorContainer .guard-status').removeClass('guard-on-processing');
        window.gameStateManager.setState('guardStates', [null, false, false, false, false]);

        // 공격 상태 및 플레이어 상태 정상화
        if (!gameStateManager.getState('isQuestCleared') && !gameStateManager.getState('isQuestFailed')) {
            window.gameStateManager.setState('isAttackClicked', false);
            player.lockPlayer(false);
        }
    }

    // 정리
    // 데미지 래퍼, 상태효과 래퍼 정리 (미리 캡쳐 후 삭제)
    let $damageWrappers = $('#actorContainer .damage-wrapper');
    let $statusEffectWrappers = $('#actorContainer .status-effect-wrapper');
    setTimeout(() => {
        // $damageWrappers.remove();
        // $statusEffectWrappers.remove();
    }, Constants.Delay.damageShowToNext);
    
    // 상태처리
    let lastProcessedResponse = stage.processingResponse;
    gameStateManager.setState('enemyEstimatedAtk', lastProcessedResponse.enemyEstimatedAtk);

    // 멤버표시 갱신
    requestMembersInfo();

}

async function processSync(response) {
    // 데미지 처리 X

    // 공헌도 반영
    window.gameStateManager.setState('indicator.moveResultHonor', response.resultHonor);

    // 합체소환 id 반영,
    if (response.unionSummonId !== null) // null 이 아닌경우에만 사용하므로 타이밍을 분리하지 않는한 이게 최선인듯
        gameStateManager.setState('unionSummonId', response.unionSummonId);

    // 이펙트 처리 - 필요시 공통 이펙트 사용 ( 참전자 버프 있음 or 참전자 힐 있음 )
    let effectDuration = 0;
    if (response.addedBuffStatusesList.find(addedBuffStatuses => addedBuffStatuses.length > 0) || response.heals.find(heal => heal > 0)) {
        await player.play(Player.playRequest('global', Player.c_animations.ABILITY_MOTION, {abilityType: 'BUFF_FOR_ALL'}), true);
    }
    // 스테이터스 처리
    let totalEndTime = await processStatusEffect(response);

    console.log('[processSync] DONE totalTime', totalEndTime, ' effectDuration ', effectDuration);
    return new Promise(resolve => setTimeout(() => resolve(), Constants.Delay.globalMoveDelay));
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
    player.play(Player.playRequest(`actor-${actorOrder}`, Player.c_animations.ABILITY_MOTION_DAMAGE, {abilityType: BASE_ABILITY.ATTACK_SEALED}));
    // 공격불가 디버프 동시 표시
    let debuffDelay = processDebuffEffect(tempAddedDebuffStatusesList);
    await wait(debuffDelay);

    // 상태 효과 처리
    let totalEndTime = await processStatusEffect(response);

    console.log('[processStrikeSealed] DONE totalTime', totalEndTime);
    return new Promise(resolve => setTimeout(() => resolve(), Constants.Delay.globalMoveDelay));
}

async function processTurnEndProcess(response) {

    // 스테이터스 처리
    let processStatusDuration = await processStatusEffect(response, 0.5);

    console.log('[processTurnEndProcess] DONE type = ', response.moveType, ' processStatusDuration = ', processStatusDuration);
    return new Promise(resolve => setTimeout(() => resolve(), Constants.Delay.globalMoveDelay));
}

/**
 * 웨이팅 띄우기
 * @param toWaiting true: to waiting, false: to normal
 */
function waitingProcess(toWaiting) {
    if (toWaiting) {
        $('.waiting-video-container').css('visibility', 'visible').find('.waiting-video').get(0).play();
        $('#container').addClass('deActivated');
    } else {
        $('.waiting-video-container').css('visibility', 'hidden').find('.waiting-video').get(0).pause();
        $('#container').removeClass('deActivated');
    }
}