function requestMove(characterId, moveId) {
    let memberId = $('#memberInfo').data('member-id');
    console.log('[requestMove] moveId = ', moveId, ' characterId = ', characterId, ' memberId = ', memberId);
    $.ajax({
        url: '/api/move',
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            memberId: memberId,
            characterId: characterId,
            moveId: moveId
        }),
        async: false,
        success: function (response) {
            // console.log(response);
            processResponseMoves(response);
        },
        error: function (response) {
            console.log(response);
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
            processResponseMoves(responseResults).then(function () {
                // 턴 갱신
                let $turnValue = $('.turn-indicator .turn-value');
                let turnValue = $turnValue.text();
                $turnValue.text(++turnValue);
                // 가드해제
                $('.guard-status-wrapper .guard-status').removeClass('guard-on');
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
    let characterId = $('#partyCommandContainer .battle-portrait').eq(charOrder - 1).data('character-id');
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
            charOrder: charOrder,
            memberId: memberId,
            roomId: roomId,
            targetType: type
        }),
        async: false,
        success: function (response) {
            // console.log(response);
            processGuard(response);
        },
        error: function (response) {
            console.log(response);
        }
    });
}

/**
 * 가드는 ActorLogicResult 가 아니라 별도처리
 * @param response
 */
function processGuard(response) {
    console.log('[processGuard] response = ', response);
    let isGuardActivated = response.guardActivated; //
    response.guardResults.forEach(function (guardResult) {
        if (guardResult.guardOn) {
            $('.guard-status-wrapper .guard-status.party-' + guardResult.currentOrder).addClass('guard-on');
        } else {
            $('.guard-status-wrapper .guard-status.party-' + guardResult.currentOrder).removeClass('guard-on');
        }
    });

    let audioPlayer = new AudioPlayer().init();
    let src = isGuardActivated ? $('.global-audio-container .guard-on').attr('src') : $('.global-audio-container .guard-off').attr('src');
    audioPlayer.loadSound(src).then(() => {
        audioPlayer.playAllSounds();
    })
}

/**
 * 오의 on off
 * @param chargeAttackActiveChecked
 */
function requestToggleChargeAttack(chargeAttackActiveChecked) {
    console.log(`[requestToggleChargeAttack] chargeAttackActiveChecked = ${chargeAttackActiveChecked}`);
    window.effectAudioPlayer.loadSound(GlobalSrc.BEEP.audio).then(() => {
        window.effectAudioPlayer.playAllSounds();
    })
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
            if (response.chargeAttackOn === true) {
                window.effectAudioPlayer.loadSound(GlobalSrc.CHARGE_ATTACK_READY.audio).then(() => {
                    window.effectAudioPlayer.playAllSounds();
                })
            }
        },
        error: function (response) {
            console.log(response);
        }
    });
}