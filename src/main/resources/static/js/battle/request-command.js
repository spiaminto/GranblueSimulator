function requestMembersInfo() {
    let roomId = $('#roomInfo').data('room-id');
    // console.log('[requestMembersInfo] roomId = ', roomId);
    $.ajax({
        url: `/api/room/${roomId}/members`,
        type: 'GET',
        headers: {
            // 'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        success: function (response) {
            // console.log(`[requestMembersInfo] response = ${response}`);
            let beforeInfos = gameStateManager.getState('memberInfos');
            if (response && beforeInfos && response.length > beforeInfos.length) {
                playSe(Sounds.ui.MEMBER_ENTER.src);
            }
            gameStateManager.setState('memberInfos', response);
        },
        error: function (error) {
            console.error(`[requestMembersInfo] error = ${error}`);
        }
    })
}

function requestChat() {
    let roomId = $('#roomInfo').data('room-id');
    let lastChatId = gameStateManager.getState('lastChatId');
    let chatIdParam = lastChatId ? `?lastId=${lastChatId}` : '';
    $.ajax({
        url: `/api/rooms/${roomId}/chats${chatIdParam}`,
        type: 'GET',
        success: function (response) {
            // console.log('[requestChat] response = ', response);
            if (response.length === 0) {
                if (gameStateManager.getState('chatMessages') === null) {
                    gameStateManager.setState('chatMessages', []); // 첫로드 초기화 null -> []
                }
                return;
            }
            lastChatId = response[response.length - 1].id;
            gameStateManager.setState('lastChatId', lastChatId);
            gameStateManager.setState('chatMessages', response);
        },
        error: function (error) {
            console.error('[requestChat] error', error);
        }
    });
}

function requestSendChat(type, payload) {
    let roomId = $('#roomInfo').data('room-id');

    // 채팅 시간제한 검증
    const now = Date.now();
    const lastChatTime = gameStateManager.getState('lastChatTime') || 0;
    if (now - lastChatTime < 3000) return;
    gameStateManager.setState('lastChatTime', now, {force: true});

    // UI 잠금
    lockChatUIForCooldown();

    $.ajax({
        url: `/api/rooms/${roomId}/chats`,
        type: 'POST',
        contentType: 'application/json',
        headers: {'X-CSRF-TOKEN': $('#csrfToken').val()},
        data: JSON.stringify(
            type === 'TEXT'
                ? {type: 'TEXT', content: payload}
                : {type: 'STAMP', chatStamp: payload}
        ),
        success: function (response) {
            // console.log('[requestSendChat] response = ', response);
            gameStateManager.setState('lastChatId', response.id);
            gameStateManager.setState('chatMessages', [response]); // 단건도 배열로 통일

            $('.chat-modal-button').click(); // 채팅 모달 닫기
        },
        error: function (error) {
            // console.error('[requestSendChat] error', error);
            let errorResp = error.responseJSON;
            if (errorResp.code === 'CHAT_FAILED') {
                alert(errorResp.message);
            }
        }
    });
}

// 채팅 UI 잠금 
function lockChatUIForCooldown() {
    const cooldownMs = 3000;

    // 폼요소
    const $formElements = $('#chatSendBtn, #chatInput, #toggleStampBtn, .short-message-button');
    $formElements.prop('disabled', true);
    // 스탬프
    const $stampItems = $('#stampPanel .stamp-item');
    $stampItems.css({
        'pointer-events': 'none',
        'opacity': '0.4'          // 시각적으로 확인 가능하게
    });

    // placeholder 변경으로 남은 시간 인지
    const $chatInput = $('#chatInput');
    const originalPlaceholder = $chatInput.attr('placeholder');
    $chatInput.attr('placeholder', '잠시 후 전송 가능합니다...');

    // 3초 후 원상복구
    setTimeout(() => {
        $formElements.prop('disabled', false);
        $stampItems.css({
            'pointer-events': 'auto',
            'opacity': '1'
        });
        $chatInput.attr('placeholder', originalPlaceholder);
    }, cooldownMs);
}

window.isSyncing = false;

function requestSync(isInit = false) {
    if (isSyncing) return;  // 진행 중이면 skip
    window.isSyncing = true;

    // console.log('[requestSync] timerId = ', window.syncTimerId);

    let roomId = $('#roomInfo').data('room-id');
    return $.ajax({
        url: `/api/rooms/${roomId}/members/me/sync`,
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({}),
    }).then(function (response) {
        if (!isInit) {
            let syncResponses = response.syncResponses;
            processResponseMoves(syncResponses, 'sync');
        }
        return response;
    }).catch(function (error) {
        let errorObj = error.responseJSON;
        console.error(`[requestSync] error = ${errorObj}`);
        if (!!errorObj.code) {
            alert(errorObj.message);

            if (errorObj.code === 'BATTLE_FINISHED') {
                // 첫동기화때 대응을 위해 페이지 변경
                if (isInit) {
                    let roomId = $('#roomInfo').attr('data-room-id');
                    location.href = `/room/${roomId}/result`;
                    return;
                }
            }

            if (window.tutorialManager?.active) {
                window.tutorialManager.retry(); // 튜토리얼 진행중 복구
            }

            // 어빌리티 레일 전부 취소
            $('.ability-rail-wrapper .rail-item').remove();
            // 공격버튼 취소
            onAttackButtonClicked();

            if (errorObj.code === 'BATTLE_FINISHED') {
                processQuestTimeout();
            }
        } else {
            alert("동기화 처리중 에러가 발생했습니다. 페이지를 새로고침 합니다.");
            location.reload();
        }
    }).always(function () {
        window.isSyncing = false;
    });
}

function requestMove(characterId, moveId, moveType) {
    let command = moveType === 'ABILITY' ? 'ability'
        : moveType === 'FATAL_CHAIN' ? 'fatal-chain'
            : moveType === 'SUMMON' ? 'summon' : null;
    if (!command) {
        alert('커맨드 에러, 새로고침 해주세요. 커맨드 = ' + moveType);
        return;
    }

    let roomId = $('#roomInfo').data('room-id');
    let apiUrl = `/api/rooms/${roomId}/members/me/${command}`;

    // console.log('[requestMove] moveId = ', moveId, ' characterId = ', characterId, ' roomId = ', roomId, ' moveType = ', moveType, ' apiUrl = ', apiUrl);

    waitingProcess(true);
    $.ajax({
        url: apiUrl,
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
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
            console.error(`[requestMove] error = ${error}`);
            let errorObj = error.responseJSON;
            let errorCode = errorObj.code;
            if (errorCode) {
                alert(errorObj.message);

                if (window.tutorialManager?.active) {
                    window.tutorialManager.retry(); // 튜토리얼 진행중 복구
                }

                let doNextProcess = errorCode === 'MOVE_VALIDATION_CONDITION_FAILED'; // 조건 검증 실패
                let $abilityRailItems = $('.ability-rail-wrapper .rail-item');
                if (doNextProcess) {
                    // 다음 처리를 이어가도록 복구
                    $abilityRailItems.eq(0).remove() // 해당 커맨드만 취소, 다음 처리를 위해 executed = true 유지
                } else {
                    // 기타 처리 실패: 어빌리티 레일을 초기화
                    $abilityRailItems.removeClass('executed');
                    $abilityRailItems.remove(); // mutationObserver 에서 오버레이등 필요처리 수행함
                }

                // 솬석 기존상태로 복구
                gameStateManager.setState('usedSummon', gameStateManager.getState('usedSummon'), {force: true});

                if (errorObj.code === 'BATTLE_FINISHED') {
                    processQuestTimeout();
                }
            } else {
                alert("서버 처리중 에러가 발생했습니다. 페이지를 새로고침 합니다.");
                location.reload();
            }
        },
        complete: function () {
            waitingProcess(false);
        }
    });
}

function requestTurnProgress() {
    let roomId = $('#roomInfo').data('room-id');

    waitingProcess(true);
    $.ajax({
        url: `/api/rooms/${roomId}/members/me/turn-progress`,
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        // async: false,
        success: function (response) {
            let responseResults = response;
            // console.log(responseResults);

            if (window.tutorialManager?.active) {
                window.tutorialManager.saveProgress(window.tutorialManager.idx + 1); // 다음스탭으로 미리저장
            }

            processResponseMoves(responseResults, 'turn');
        },
        error: function (error) {
            console.error(`[requestTurnProgress] error = ${error}`);
            let errorObj = error.responseJSON;
            if (!!errorObj.code) {
                alert(errorObj.message);

                if (window.tutorialManager?.active) {
                    window.tutorialManager.retry(); // 튜토리얼 진행중 복구
                }

                // 어빌리티 레일 전부 취소
                $('.ability-rail-wrapper .rail-item').remove();
                // 공격버튼 취소
                onAttackButtonClicked();

                if (errorObj.code === 'BATTLE_FINISHED') {
                    processQuestTimeout();
                }

            } else {
                alert("서버 처리중 에러가 발생했습니다. 페이지를 새로고침 합니다.");
                location.reload();
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
    // console.log('[requestGuard] charOrder = ', charOrder, ' type = ', type);
    let characterId = $('#partyCommandContainer .battle-portrait').eq(charOrder - 1).data('actor-id');
    if (!characterId) {
        alert("가드할 캐릭터가 없습니다.");
        return;
    }

    let roomId = $('#roomInfo').data('room-id');
    $.ajax({
        url: `/api/rooms/${roomId}/members/me/guard`,
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            targetType: type,
            characterId: characterId,
            actorOrder: charOrder,
        }),
        async: false,
        success: function (response) {
            // console.log('[requestGuard] response = ', response);

            stopSync();
            doSync(true); // 즉시 동기화 실행 (가드로 인한 적 데미지등 상태변경분 즉시 반영필요)

            processGuard(response);
        },
        error: function (error) {
            console.error(`[requestGuard] error = ${error}`);
            let errorObj = error.responseJSON;
            let errorCode = errorObj.code;
            if (errorCode) {
                alert(errorObj.message);
            }
        }
    });
}

/**
 * 오의 on off
 * @param chargeAttackActiveChecked
 */
function requestToggleChargeAttack(chargeAttackActiveChecked) {
    // console.log(`[requestToggleChargeAttack] chargeAttackActiveChecked = ${chargeAttackActiveChecked}`);
    playSe(Sounds.ui.BEEP.src);

    let roomId = $('#roomInfo').data('room-id');
    $.ajax({
        url: `/api/rooms/${roomId}/members/me/toggle-charge-attack`,
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            chargeAttackOn: chargeAttackActiveChecked
        }),
        async: false,
        success: function (response) {
            // console.log(`[requestToggleChargeAttack] response = ${response}`);
            $('#chargeAttackActiveCheck').prop('checked', response.chargeAttackOn);
            gameStateManager.setState('canChargeAttacks', response.canChargeAttacks);
            gameStateManager.setState('chargeGauges', gameStateManager.getState('chargeGauges'), {force: true}); // canChargeAttacks 가 렌더러 연결 안되있어서 직접 갱신
            // 대기모션 갱신
            player.renewCharacterWait();

            if (response.chargeAttackOn === true) {
                playSe(Sounds.ui.CHARGE_ATTACK_READY.src);

                // 랜덤 한명 오의 준비 보이스 재생
                let characters = player.actors.values().filter(actor => actor.isCharacter() && !actor.isLeaderCharacter && response.canChargeAttacks[actor.actorIndex]).toArray();
                if (characters.length > 0) {
                    let randomIndex = Math.floor(Math.random() * characters.length);
                    characters[randomIndex].playVoice(Player.c_animations.ABILITY_WAIT);
                }
            }

        },
        error: function (error) {
            console.error(`[requestToggleChargeAttack] error = ${error}`);
            let errorObj = error.responseJSON;
            let errorCode = errorObj.code;
            if (errorCode) {
                alert(errorObj.message);
            }
        }
    });
}

/**
 * 포션 요청
 * @param potionType
 * @param actorId
 * @param targetActorId
 */
function requestPotion(potionType, targetActorId) {
    // console.log('[requestPotion] potionType = ', potionType, ' targetActorId = ', targetActorId);
    let roomId = $('#roomInfo').data('room-id');
    $.ajax({
        url: `/api/rooms/${roomId}/members/me/use-potion`,
        type: 'POST',
        contentType: 'application/json',
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: JSON.stringify({
            targetActorId: targetActorId,
            potionType: potionType.toUpperCase() // POTION, ALL_POTION, ELIXIR
        }),
        async: false,
        success: function (response) {
            // console.log('[requestPotion] response = ', response);
            setTimeout(() => processPotion(response), 500); // 모달 닫히는 시간 고려
        },
        error: function (error) {
            console.error(`[requestPotion] error = ${error}`);
            let errorObj = error.responseJSON;
            if (!!errorObj.code) {
                alert(errorObj.message);

                // 어빌리티 레일 전부 취소
                $('.ability-rail-wrapper .rail-item').remove();
            }
        }
    });
}

// test ==============================================================================================================
function requestResetCooldown() {
    let memberId = $('#memberInfo').data('member-id');
    // console.log('[requestRestCooldown] memberId = ', memberId);
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
            // console.log(error);
        }
    });
}