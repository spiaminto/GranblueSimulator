function requestSync() {
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
            processResponseMoves(response, 'move');
        },
        error: function (response) {
            console.log(response);
            let responseObj = response.responseJSON;
            if (response.status === 400) {
                alert(responseObj.message);
                $('.ability-rail-wrapper .rail-item').remove();
                // TODO 해당하는 모든 어빌리티 오버레이 취소
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
            console.log('[requestGuard]', response);
            processGuard(response);
        },
        error: function (response) {
            console.log(response);
        }
    });
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
                player.actors.values()
                    .filter(actor => actor.isCharacter() && Number($(`#partyCommandContainer .battle-portrait.actor-${actor.actorIndex} .charge-gauge-value .value`).text()) === 100)
                    .forEach(actor => player.play(Player.playRequest(`actor-${actor.actorIndex}`, Player.c_animations.ABILITY)));
            } else {
                player.actors.values()
                    .filter(actor => actor.isCharacter())
                    .forEach(actor => player.play(Player.playRequest(`actor-${actor.actorIndex}`, Player.c_animations.STB_WAIT)));
                if ($('#abilitySlider').css('z-index') >= 0) { // 어빌리티 슬라이더 열려있음
                    let currentSlide = $('#abilitySlider').slick('getSlick').currentSlide;
                    player.play(Player.playRequest(`actor-${currentSlide + 1}`, Player.c_animations.ABILITY));
                }
            }
        },
        error: function (response) {
            console.log(response);
        }
    });
}

/**
 * 포션 요청
 * @param usePotionButtonElement
 */
function requestPotion(usePotionButtonElement) {
    let potionType = $(usePotionButtonElement).attr('data-potion-type');
    let potionTargetCharOrder = potionType === 'single' ? $('.potion-target-radio-container input[name="potionTarget"]:checked').val() : -1;
    let characterId = $('#partyCommandContainer .battle-portrait').eq(potionTargetCharOrder - 1).data('character-id');
    let memberId = $('#memberInfo').data('member-id');
    console.log('[requestPotion] potionTargetCharOrder = ', potionTargetCharOrder, ' potionType = ', potionType);
    $.ajax({
        url: '/api/use-potion',
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            characterId: characterId,
            memberId: memberId,
            potionType: potionType // 영원히 바뀔일 없으니 single, all, elixir 로 고정
        }),
        async: false,
        success: function (response) {
            $(usePotionButtonElement)
                .attr('data-potion-type', '')
                .prop('disabled', true);
            $('#potionModal .close-button').click();

            setTimeout(() => processPotion(response), 500); // 모달 닫히는 시간 고려
        },
        error: function (response) {
            console.error(response);
        }
    });
}