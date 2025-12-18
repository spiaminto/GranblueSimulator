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
    let memberId = $('#memberInfo').data('member-id');
    console.log('[requestSync] memberId = ', memberId);
    $.ajax({
        url: '/api/sync',
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
            if (isInit) initGameStatus(response);
            else processResponseMoves(response);
        },
        error: function (error) {
            console.log(error);
        }
    });
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
        async: false,
        success: function (response) {
            // console.log(response);
            processResponseMoves(response, 'move');
        },
        error: function (error) {
            console.log(error);
            let responseObj = error.responseJSON;
            if (error.status === 400) {
                alert(responseObj.message);

                // 실패한 무브 레일에서 제거 (자동으로 다음 무브 이어서 실행)
                $('.ability-rail-wrapper .rail-item').eq(0).remove();
                // 오버레이 정상화
                gameStateManager.setState('abilityCoolDowns', gameStateManager.getState("abilityCoolDowns"), {force: true});

                if (!responseObj.isConditionFailed) {
                    // 발동 조건 검증 실패 외의 경우, 어빌리티 레일 전부 취소
                    $('.ability-rail-wrapper .rail-item').remove();
                }
            }
        }
    });
}

function requestTurnProgress() {
    let memberId = $('#memberInfo').data('member-id');
    let roomId = $('#roomInfo').data('room-id');

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
        async: false,
        success: function (response) {
            let responseResults = response;
            // console.log(responseResults);
            processResponseMoves(responseResults, 'turn').then(function () {
                // 턴 갱신
                let $turnValue = $('.turn-indicator .turn-value');
                let turnValue = $turnValue.text();
                $turnValue.text(++turnValue);
            })
        },
        error: function (error) {
            console.error('Error:', error);
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
    window.effectAudioPlayer.loadAndPlay(Sounds.BEEP.src);

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
                window.effectAudioPlayer.loadAndPlay(Sounds.CHARGE_ATTACK_READY.src);
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