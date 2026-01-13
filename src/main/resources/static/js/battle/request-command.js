function requestMembersInfo() {
    let roomId = $('#roomInfo').data('room-id');
    console.log('[requestMembersInfo] roomId = ', roomId);
    $.ajax({
        url: `/api/room/${roomId}/members`,
        type: 'GET',
        headers: {
            // 'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        success: function (response) {
            console.log(response);
            gameStateManager.setState('memberInfos', response);
        },
        error: function (error) {
            console.log(error);
        }
    })
}

function requestSync(isInit = false) {
    stopSync();
    let memberId = $('#memberInfo').data('member-id');
    console.log('[requestSync] memberId = ', memberId);
    return $.ajax({
        url: '/api/sync',
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            memberId: memberId,
        })
    }).then(function (responses) {
        if (!isInit) {
            processResponseMoves(responses);
            doSync();
        }
        return responses;
    }).catch(function (error) {
        console.error(error)
        let errorResponse = error.responseJSON;
    })
}

function requestMove(characterId, moveId, moveType) {
    let memberId = $('#memberInfo').data('member-id');
    let apiUrl =
        moveType === 'ABILITY' ? '/api/ability'
            : moveType === 'FATAL_CHAIN' ? '/api/fatal-chain'
                : moveType === 'SUMMON' ? '/api/summon' : null;
    if (!apiUrl) {
        alert('커맨드 에러, 새로고침 해주세요. 커맨드 = ' + moveType);
        return;
    }

    console.log('[requestMove] moveId = ', moveId, ' characterId = ', characterId, ' memberId = ', memberId, ' moveType = ', moveType, ' apiUrl = ', apiUrl);
    waitingProcess(true);
    $.ajax({
        url: apiUrl,
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            memberId: memberId,
            characterId: characterId,
            moveId: moveId,
            // 옵션들
            doUnionSummon: stage.gGameStatus.doUnionSummon,
        }),
        // async: false,
        success: function (response) {
            // console.log(response);
            processResponseMoves(response, 'move');
        },
        error: function (error) {
            console.log(error);
            let errorObj = error.responseJSON;
            if (!!errorObj.code) {
                alert(errorObj.message);

                let $abilityRailItems = $('.ability-rail-wrapper .rail-item');
                if (errorObj.code === 'MOVE_VALIDATION_CONDITION_FAILED') {
                    $abilityRailItems.eq(0).remove() // 발동 조건 검증 실패한 경우 해당 어빌리티만 취소, 실행은 됫으니 executed = true
                } else {
                    $abilityRailItems.removeClass('executed');
                    $abilityRailItems.remove();
                }
            }
        },
        complete: function () {
            waitingProcess(false);
        }
    });
}

function requestTurnProgress() {
    let memberId = $('#memberInfo').data('member-id');
    let roomId = $('#roomInfo').data('room-id');

    waitingProcess(true);
    $.ajax({
        url: '/api/turn-progress',
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            memberId: memberId,
            roomId: roomId
        }),
        // async: false,
        success: function (response) {
            let responseResults = response;
            // console.log(responseResults);
            processResponseMoves(responseResults, 'turn');
        },
        error: function (error) {
            console.log(error);
            let errorObj = error.responseJSON;
            if (!!errorObj.code) {
                alert(errorObj.message);

                // 어빌리티 레일 전부 취소
                $('.ability-rail-wrapper .rail-item').remove();
                // 공격버튼 취소
                onAttackButtonClicked();
            }
        },
        complete: function () {
            waitingProcess(false);
        }
    });
}

/**
 * 가드 요청
 * @param charOrder 가드 누른 캐릭터
 * @param type 가드타입 (SELF, PARTY_MEMBERS)
 */
function requestGuard(charOrder, type) {
    console.log('[requestGuard] charOrder = ', charOrder, ' type = ', type);
    let characterId = $('#partyCommandContainer .battle-portrait').eq(charOrder - 1).data('actor-id');
    let memberId = $('#memberInfo').data('member-id');
    let roomId = $('#roomInfo').data('room-id');
    $.ajax({
        url: '/api/guard',
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            characterId: characterId,
            actorOrder: charOrder,
            memberId: memberId,
            roomId: roomId,
            targetType: type
        }),
        async: false,
        success: function (response) {
            console.log('[requestGuard]', response);
            processGuard(response);
        },
        error: function (error) {
            console.log(error);
        }
    });
}

/**
 * 오의 on off
 * @param chargeAttackActiveChecked
 */
function requestToggleChargeAttack(chargeAttackActiveChecked) {
    console.log(`[requestToggleChargeAttack] chargeAttackActiveChecked = ${chargeAttackActiveChecked}`);
    playSe(Sounds.ui.BEEP.src);

    let roomId = $('#roomInfo').data('room-id');
    $.ajax({
        url: '/api/toggle-charge-attack',
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            roomId: roomId,
            chargeAttackOn: chargeAttackActiveChecked
        }),
        async: false,
        success: function (response) {
            // console.log(response);
            $('#chargeAttackActiveCheck').prop('checked', response.chargeAttackOn);
            // 대기모션 갱신
            player.getCharacters().forEach(actor => player.play(Player.playRequest(`actor-${actor.actorIndex}`, player.getCharacterWaitMotion(actor.actorIndex))));
            if (response.chargeAttackOn === true)
                playSe(Sounds.ui.CHARGE_ATTACK_READY.src);
        },
        error: function (error) {
            console.log(error);
        }
    });
}

/**
 * 포션 요청
 * @param potionType
 * @param actorId
 */
function requestPotion(potionType, actorId) {
    console.log('[requestPotion] actorId = ', actorId, ' potionType = ', potionType);
    let memberId = $('#memberInfo').data('member-id');
    $.ajax({
        url: '/api/use-potion',
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            actorId: actorId,
            memberId: memberId,
            potionType: potionType // 영원히 바뀔일 없으니 single, all, elixir 로 고정
        }),
        async: false,
        success: function (response) {
            $('#usePotionButton')
                .attr('data-potion-type', '')
                .prop('disabled', true);

            setTimeout(() => processPotion(response), 500); // 모달 닫히는 시간 고려
        },
        error: function (error) {
            console.error(error);
            alert(response.responseText);
        }
    });
}

// test ==============================================================================================================
function requestResetCooldown() {
    let memberId = $('#memberInfo').data('member-id');
    console.log('[requestRestCooldown] memberId = ', memberId);
    $.ajax({
        url: '/test/reset-cooldowns',
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            memberId: memberId,
        }),
        success: function (response) {
            // console.log(response);
            processResponseMoves(response);
        },
        error: function (error) {
            console.log(error);
        }
    });
}