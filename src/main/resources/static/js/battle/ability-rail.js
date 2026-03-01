/**
 * 어빌리티 아이콘 클릭시 실행, 모달로 열지 process 할지 구분
 */
function onAbilityIconClicked(event) {
    let $this = $(this);
    if ($this.is('.button-click')) return;
    $this.addClass('button-click').one('animationend', () => $this.removeClass('button-click'));
    playSe(Sounds.ui.BUTTON_CLICK.src);

    let abilityId = $this.attr('data-move-id');
    let ability = gameStateManager.getState(`ability.${abilityId}`);
    // console.log('[onAbilityIconClicked] $this = ', $this, ' abilityId = ', abilityId, 'ability = ', ability);

    let isShowAbilityInfoCheck = $('#showAbilityInfoCheck').is(':checked'); // 어빌리티 설명 체크여부
    if (isShowAbilityInfoCheck) {
        openCommandInfoModal(ability);
    } else {
        processMoveClick(abilityId);
    }
}

/**
 * 기본 커맨드 클릭 전처리 수행
 * @param moveId {Number} 어빌리티, 페이탈체인 / 소환석
 */
function processMoveClick(moveId) {
    if (player.locked || stage.gGameStatus.isQuestCleared) return;

    const allMoves = {
        ...gameStateManager.getState('ability'),
        ...gameStateManager.getState('summon'),
        ...gameStateManager.getState('fatalChain'),
    };
    let moveInfo = allMoves[moveId];
    console.debug('[processMoveClick] moveId = ', moveId, ' moveInfo = ', moveInfo);

    if ($(`.command-overlay[data-move-id="${moveId}"]`).is('.on-rail')) return; // on-rail 클래스 있을때 재등록 방지

    if (moveInfo.type === 'ABILITY') {
        // 쿨타임 검증
        let cooldown = gameStateManager.getState('abilityCoolDowns')[moveInfo.actorIndex][moveInfo.order - 1];
        let sealed = gameStateManager.getState('abilitySealeds')[moveInfo.actorIndex][moveInfo.order - 1];
        if (cooldown > 0 || sealed) return;

        // 레일 재등록 방지
        $(`#commandContainer .ability-icon[data-move-id="${moveId}"] .command-overlay`).addClass('on-rail');

    } else if (moveInfo.type === 'SUMMON') {
        // 쿨타임 검증
        let cooldown = gameStateManager.getState('summonCooldowns')[moveInfo.order - 1];
        if (cooldown > 0) return;

        // 레일 재등록 방지
        $('#partyCommandContainer .summon-display-wrapper .summon-overlay').addClass('on-rail'); // 모든 소환석에 전부 추가

        $('.summon-display-button').click();
    }

    // 어빌리티 레일에 등록
    appendToAbilityRail(moveInfo);
}

/**
 * 공격 버튼 클릭 전처리 수행
 */
function onAttackButtonClicked() {
    let isAttackClicked = gameStateManager.getState('isAttackClicked');
    let isQuestCleared = gameStateManager.getState('isQuestCleared');

    if (isQuestCleared) {
        let roomId = $('#roomInfo').attr('data-room-id');
        location.replace('/room/' + roomId + '/result');
        return;
    }

    if (isAttackClicked) { // attack button 클릭되어있음 -> attack cancel
        let $attackRailItem = $('#abilityRail .rail-item[data-rail-item-type="ATTACK"]');
        if ($attackRailItem.length > 0) {
            $attackRailItem.click(); // 레일에 있을경우, 레일에서 삭제트리거를 통해 현재 함수 재실행
            return;
        }
        player.lockPlayer(false); // 공격 취소시 락 해제
        playSe(Sounds.ui.CANCEL_ATTACK.src);
        window.gameStateManager.setState('isAttackClicked', false);
    } else {
        if (player.locked) return; // 이미 잠겨있을시 종료
        player.lockPlayer(true); // 플레이어 잠금
        playSe(Sounds.ui.REQUEST_ATTACK.src);
        window.gameStateManager.setState('isAttackClicked', true);

        appendToAbilityRail(gameStateManager.getState('attack')); // 어빌리티 레일에 등록
    }
}

/**
 * 어빌리티 레일에 아이콘 만들어서 등록
 * @param {MoveInfo} moveInfo
 */
function appendToAbilityRail(moveInfo) {
    let currentAbilityRailLength = $('#abilityRail img').length;
    $(`<div class="rail-item rail-item-${currentAbilityRailLength}"
              data-rail-item-type='${moveInfo.type}' 
              data-actor-index='${moveInfo.actorIndex}' 
              data-move-id='${moveInfo.id}'
              data-actor-id="${moveInfo.actorId}"
              data-additional-type="${moveInfo.additionalType}">
              <img src="${moveInfo.iconImageSrc}" alt="icon image">
        </div>`)
        .on('click', function () { // 클릭시 사용취소 (어빌리티 레일에서 제거)
            if ($(this).index() === 1) return; // 자신의 index 가 첫번째면 클릭(취소) 불가 (0번은 더미)
            $(this).remove();
        })
        .appendTo($('#abilityRail'));
}

/**
 * 어빌리티 레일 MutationObserver handler
 * @param entries
 * @return {boolean}
 */
function handleAbilityRailMutation(entries) {
    console.debug('[#abilityRail.mutationObserver] entries = ', entries);

    let entry = entries[0];
    let $abilityRail = $(entry.target);
    let $addedRailItem = $(entry.addedNodes);
    let $removedRailItem = $(entry.removedNodes);
    let $latestRailItem = $abilityRail.children('.rail-item').first();

    let isFirstItemAdded = $addedRailItem.hasClass('rail-item-1'); // 첫 아이템 추가
    let isBeforeItemExecuted = $removedRailItem.hasClass('executed'); // 아이템 실행이 완료된 후 삭제됨 -> 다음(latest) 아이템 실행
    let removedRailItemType = $removedRailItem.attr('data-rail-item-type');

    let isExecuted = false;

    if (isFirstItemAdded) { // 첫 아이템으로 추가됨
        stopSync();
        isExecuted = executeRailItem($latestRailItem);
        if (isExecuted) $latestRailItem.addClass('executed');
        return isExecuted;
    }

    if ($removedRailItem.length > 0) { // 아이템이 삭제됨
        if (isBeforeItemExecuted) { // 이전 아이템의 처리가 실행됨 ($latestRailItem 이 조건충족실패로 취소될 경우 이어서 실행을 위해 executed = true 상태로 제거됨)
            restoreIconOverlay($removedRailItem.attr('data-move-id'), removedRailItemType);

            if ($latestRailItem.length > 0) { // 다음 아이템 있으면 이어서 실행
                stopSync();
                isExecuted = executeRailItem($latestRailItem);
                if (isExecuted) $latestRailItem.addClass('executed');
            } else {
                doSync();
                return false;
            }
        } else { // 이전 아이템의 처리가 실행되지 않음 (사용자 클릭으로 취소 또는 에러로 인한 미실행 취소)
            if (removedRailItemType === 'ATTACK') {
                onAttackButtonClicked();
            } else if (removedRailItemType === 'ABILITY' || removedRailItemType === 'SUMMON') {
                entries.forEach(entry => { // 에러로 인한 미실행시 한꺼번에 레일 전체가 취소되는 경우 있음
                    let $removedItem = $(entry.removedNodes);
                    restoreIconOverlay($removedItem.attr('data-move-id'), $removedItem.attr('data-rail-item-type'));
                });
            }
        }
    }

    return isExecuted;
}

/**
 * 어빌리티 레일에서 처리된 어빌리티의 on-rail 오버레이 해제
 * @param moveId
 * @param railItemType
 */
function restoreIconOverlay(moveId, railItemType) {
    if (railItemType === 'ABILITY') {
        $(`#commandContainer .ability-icon[data-move-id="${moveId}"] .command-overlay`).removeClass('on-rail');
    }
    // 소환석은 usedSummon 플래그로 별도로 on-rail 관리
}

/**
 * 어빌리티 레일 위의 특정 요소를 (요청)처리 (기본적으로 제일 처음, latest 요소를 처리)
 * @param $railItem
 * @return {boolean} 처리 성공 여부
 */
function executeRailItem($railItem) {
    let actorId = $railItem.attr('data-actor-id');
    let latestRailItemType = $railItem.attr('data-rail-item-type');
    console.log('[#abilityRail.mutationObserver] railItemType = ', latestRailItemType);

    let isExecuted = false;
    switch (latestRailItemType) {
        case 'ABILITY':
        case 'FATAL_CHAIN':
        case 'SUMMON':
            let moveId = $railItem.attr('data-move-id');
            if (latestRailItemType === 'FATAL_CHAIN') actorId = stage.gGameStatus.actorIds.slice(1).find(id => !!id); // 현재 프론트 캐릭터중 첫번째 캐릭터의 id 로 설정
            requestMove(actorId, moveId, latestRailItemType)
            isExecuted = true;
            break;

        case 'ATTACK':
            $railItem.off('click'); // 취소 불가 처리
            requestTurnProgress();
            isExecuted = true;
            break;

        case 'POTION':
            let potionType = $railItem.attr('data-additional-type');
            requestPotion(potionType, actorId)
            isExecuted = true;
            break;

        default:
            console.log('[handleAbilityRailMutation] invalid railItemType ', latestRailItemType);
            break;
    }

    return isExecuted
}

/**
 * 커맨드 상세 모달 열기 (어빌리티, 페이탈체인, 소환석)
 * @param moveInfo
 */
function openCommandInfoModal(moveInfo) {
    let isSummon = moveInfo.type === 'SUMMON';
    let commandName = isSummon ? ': 소환석' : moveInfo.type === 'ABILITY' ? ': 어빌리티' : '';

    // 헤더
    let $modalHeader = $(`
        <div class="modal-header">
          <h4 class="modal-title">커맨드 정보 ${commandName}</h4>
        </div>
    `);

    // 커맨드 정보
    let $modalBody = $(`<div class="modal-body"></div>`);
    let $commandInfoWrapper = createCommandInfoWrapperElement(moveInfo);
    $modalBody.append($commandInfoWrapper);

    // 상태효과
    let $statusEffectWrapper = createStatusWrapperElement(moveInfo.statusEffects, {metadata: true});
    $modalBody.append($statusEffectWrapper);

    // 합체 소환
    let unionSummonInfo = gameStateManager.getState('unionSummonInfo');
    if (isSummon && unionSummonInfo) {
        let $unionSummonWrapper = createCommandInfoWrapperElement(unionSummonInfo)
        $modalBody.append($('<hr>')).append($unionSummonWrapper);

        // 합체소환 상태효과
        let $unionSummonStatusEffectWrapper = createStatusWrapperElement(unionSummonInfo.statusEffects, {metadata: true});
        $modalBody.append($unionSummonStatusEffectWrapper);
    }

    // 돔추가, 이벤트 등록
    let $modalContent = $('#abilityInfoModal .modal-content');
    $modalContent.find('.modal-header').replaceWith($modalHeader);
    $modalContent.find('.modal-body').empty().append($modalBody.children());
    $modalContent.find('#abilityStatusEffectInfoCheck')
        .prop('checked', localStorage.getItem('abilityStatusEffectInfoCheck') === 'true')
        .trigger('change');

    // 미리 '사용' 버튼 활성화 여부 결정
    let isDisabled;
    switch (moveInfo.type) {
        case 'ABILITY':
            let cooldown = gameStateManager.getState('abilityCoolDowns')[moveInfo.actorIndex][moveInfo.order - 1];
            let sealed = gameStateManager.getState('abilitySealeds')[moveInfo.actorIndex][moveInfo.order - 1];
            isDisabled = cooldown > 0 || sealed;
            break;
        case 'SUMMON':
            let summonCooldown = gameStateManager.getState('summonCooldowns')[moveInfo.order - 1];
            isDisabled = summonCooldown > 0;
            break;
        case 'FATAL_CHAIN':
            isDisabled = gameStateManager.getState('fatalChainGauge') < 100;
            break;
        default:
            isDisabled = false;
    }
    $('#abilityInfoModal .use-ability-button').prop('disabled', isDisabled);

    $('#abilityInfoModal .modal-footer').attr('data-move-id', moveInfo.id);
    $('.ability-info-modal-button').click();
}

/**
 * 기타 캐릭터 행동 상세 모달 열기 (서포트 + 오의)
 * @param actorIndex
 */
function openOtherMoveInfoModal(actorIndex) {
    gameStateManager.getState(`chargeAttack.${actorIndex}`)

    // 헤더
    let $modalHeader = $(`
        <div class="modal-header">
          <h4 class="modal-title">오의, 서포트 어빌리티 정보</h4>
        </div>
    `);

    let chargeAttackInfo = Object.values(gameStateManager.getState('chargeAttack'))[actorIndex - 1];
    let supportAbilityInfos = gameStateManager.getState(`supportAbility.${actorIndex}`); // array
    let toRenderInfos = [chargeAttackInfo, ...supportAbilityInfos];

    // 커맨드 정보
    let $modalBody = $(`<div class="modal-body"></div>`);

    toRenderInfos.forEach(toRenderInfo => {
        let $commandInfoWrapper = createCommandInfoWrapperElement(toRenderInfo);
        if (toRenderInfo.type === 'CHARGE_ATTACK') {
            $commandInfoWrapper.find('h5').prepend('오의: ');
        } else {
            $commandInfoWrapper.find('h5').prepend('서포트: ');
        }
        $modalBody.append($commandInfoWrapper);

        // 상태효과
        let $statusEffectWrapper = createStatusWrapperElement(toRenderInfo.statusEffects, {metadata: true});
        $modalBody.append($statusEffectWrapper);

        $modalBody.append($('<hr>'));
    });

    // 돔추가, 이벤트 등록
    let $modalContent = $('#otherMoveInfoModal .modal-content');
    $modalContent.find('.modal-header').replaceWith($modalHeader);
    $modalContent.find('.modal-body').empty().append($modalBody.children());
    $modalContent.find('.show-status-effect-details-check')
        .prop('checked', localStorage.getItem('abilityStatusEffectInfoCheck') === 'true')
        .trigger('change');

    $('.other-move-info-modal-button').click();
}

/**
 * 커맨드 상세 정보 요소 만들어 반환
 */
function createCommandInfoWrapperElement(moveInfo) {
    let isSummon = moveInfo.type === 'SUMMON';
    let isUnionSummon = isSummon && moveInfo.id === gameStateManager.getState('unionSummonInfo')?.id;
    let imageSrc = isSummon ? moveInfo.portraitImageSrc : moveInfo.iconImageSrc;
    imageSrc = imageSrc || ''; // 없으면 비우기
    let cooldown = moveInfo.maxCooldown; // number, -1: 재사용불가인듯
    let cooldownString = cooldown >= 0 && cooldown < 999 ? cooldown + '턴' : '재사용 불가';

    // 커맨드 정보
    let $commandInfoWrapper = null;

    if (isSummon && isUnionSummon) {
        // 소환(합체소환)
        let doUnionSummon = gameStateManager.getState('doUnionSummon');
        let $unionSummonWrapper = $(`       
          <div class="ability-info-wrapper">
            <div class="ability-info-icon-wrapper">
              <img class="ability-info-icon" src="${imageSrc}">
            </div>
            <div class="ability-info-text-wrapper">
              <div class="row">
                <h5 class="col-7">${moveInfo.name}</h5>
                <div class="col">
                  <div class="form-check form-switch do-union-summon-check-wrapper">
                    <input class="form-check-input" type="checkbox" role="switch" id="doUnionSummonCheck">
                    <label class="form-check-label" for="doUnionSummonCheck">합체 소환</label>
                  </div>
                </div>
              </div>
              <div class="ability-info-text">
                ${moveInfo.info}
              </div>
            </div>
          </div>`);
        $unionSummonWrapper.find('#doUnionSummonCheck')
            .prop('checked', doUnionSummon)
            .on('change', function () {
                let isChecked = $(this).is(':checked');
                gameStateManager.setState('doUnionSummon', isChecked);
            });
        $commandInfoWrapper = $unionSummonWrapper;
    } else {
        // 어빌리티, 페이탈체인, 소환석, [오의, 서포트 어빌리티]
        $commandInfoWrapper = $(`
        <div class="ability-info-wrapper">
          <div class="ability-info-icon-wrapper">
            <img class="ability-info-icon" src="${imageSrc}">
          </div>
          <div class="ability-info-text-wrapper">
            <h5>${moveInfo.name}</h5>
            <div class="ability-info-text">
              ${infoToRenderText(moveInfo.info)}
            </div>
          </div>                 
        </div>
    `);
        if (moveInfo.type === 'ABILITY' || moveInfo.type === 'SUMMON') {
            $commandInfoWrapper.find('.ability-info-text-wrapper').append($(`
            <div class="ability-info-text cooldown">
              [ 쿨타임: ${cooldownString} ]
            </div>`));
        }
    }

    return $commandInfoWrapper;
}


