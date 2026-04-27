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
    // console.debug('[processMoveClick] moveId = ', moveId, ' moveInfo = ', moveInfo);

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
        // 소환석 리스트 닫기
        $('.ability-back-button').click();
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
    let isQuestFailed = gameStateManager.getState('isQuestFailed');
    let isQuestTimeout = gameStateManager.getState('isQuestTimeout');

    if (isQuestFailed) {
        $('.potion-modal-button').click();
        return;
    }

    if (isQuestCleared || isQuestTimeout) {
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
              data-target-actor-id="${moveInfo.targetActorId}"
              data-potion-type="${moveInfo.potionType}"
              data-ability-type="${moveInfo.abilityType}">
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
    // console.debug('[#abilityRail.mutationObserver] entries = ', entries);

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
                doSync(true);
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
                if (removedRailItemType === 'SUMMON') {
                    gameStateManager.setState('usedSummon', false, {force: true});
                }
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
}

/**
 * 어빌리티 레일 위의 특정 요소를 (요청)처리 (기본적으로 제일 처음, latest 요소를 처리)
 * @param $railItem
 * @return {boolean} 처리 성공 여부
 */
function executeRailItem($railItem) {
    let actorId = $railItem.attr('data-actor-id');
    let latestRailItemType = $railItem.attr('data-rail-item-type');
    // console.log('[#abilityRail.mutationObserver] railItemType = ', latestRailItemType);

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
            document.querySelectorAll('.modal.show').forEach(el => {
                bootstrap.Modal.getInstance(el)?.hide(); // 열린 모달 전부 닫음
            });
            if ($('.ability-back-button').is(':visible')) {
                $('.ability-back-button').click(); // 어빌리티 슬라이더 or 솬석창 닫음
            }
            requestTurnProgress();
            isExecuted = true;
            break;

        case 'POTION':
            let potionType = $railItem.attr('data-potion-type');
            let targetId = $railItem.attr('data-target-actor-id');
            requestPotion(potionType, targetId);
            isExecuted = true;
            break;

        default:
            // console.log('[handleAbilityRailMutation] invalid railItemType ', latestRailItemType);
            break;
    }

    return isExecuted
}

/**
 * 커맨드 상세 모달 열기 (어빌리티, 페이탈체인, 소환석)
 * @param moveInfo
 */
function openCommandInfoModal(moveInfo) {

    // document.querySelectorAll('[data-bs-toggle="popover"]:not([data-bs-initialized])').forEach(el => {
    //     new bootstrap.Popover(el, {trigger: 'click', html: true});
    //     el.dataset.bsInitialized = 'true'; // 중복 초기화 방지
    // });

    let isSummon = moveInfo.type === 'SUMMON';
    let commandName = '';
    if (moveInfo.type === 'ABILITY') {
        let classname =
            moveInfo.abilityType === 'ATTACK' ? 'text-red'
                : moveInfo.abilityType === 'BUFF' ? 'text-yellow'
                    : moveInfo.abilityType === 'DEBUFF' ? 'text-blue'
                        : moveInfo.abilityType === 'HEAL' ? 'text-green'
                            : '';
        commandName = `<span class="${classname}">${moveInfo.displayAbilityType} </span>어빌리티`;
    } else if (moveInfo.type === 'SUMMON') {
        commandName = '소환석';
    }

    //헤더
    let $modalHeader = $(`
        <div class="modal-header">
          <h4 class="modal-title">커맨드 정보: ${commandName}</h4>
        </div>
    `);

    //바디
    let $modalBody = $(`<div class="modal-body"></div>`);

    // 상태효과
    let $statusEffectWrapper = createStatusWrapperElement(moveInfo.statusEffects, {metadata: true});
    $modalBody.append($statusEffectWrapper);

    // 커맨드 정보
    let $commandInfoWrapper = createCommandInfoWrapperElement(moveInfo);
    $modalBody.append($commandInfoWrapper);

    // 합체 소환
    let unionSummonInfo = gameStateManager.getState('unionSummonInfo');
    if (isSummon && unionSummonInfo) {
        // 합체소환 상태효과
        let $unionSummonStatusEffectWrapper = createStatusWrapperElement(unionSummonInfo.statusEffects, {metadata: true});
        $modalBody.append($('<hr>')).append($unionSummonStatusEffectWrapper);

        // 합체소환 정보
        let $unionSummonWrapper = createCommandInfoWrapperElement(unionSummonInfo);
        $modalBody.append($unionSummonWrapper);
    }

    //돔추가, 이벤트 등록
    let $modalContent = $('#abilityInfoModal .modal-content');
    $modalContent.find('.modal-header').replaceWith($modalHeader);
    $modalContent.find('.modal-body').empty().append($modalBody.children());
    $modalContent.find('#abilityStatusEffectInfoCheck')
        .prop('checked', localStorage.getItem('abilityStatusEffectInfoCheck') === 'true')
        .trigger('change');

    //미리 '사용' 버튼 활성화 여부 결정
    let isDisabled;
    if (player.locked) {
        isDisabled = true; // 기본적으로 플레이어 잠기면 잠김
    } else {
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
    }
    $('#abilityInfoModal .use-ability-button').prop('disabled', isDisabled);

    $('#abilityInfoModal .modal-footer').attr('data-move-id', moveInfo.id);

    // 변화
    $('#abilityInfoModal .next-move-info-modal-button').remove();
    if (moveInfo.nextMoveId) {
        let $nextMoveInfoButton = $(`
            <button type="button" class="btn btn-outline-warning btn-sm next-move-info-modal-button"
              data-bs-toggle="modal" data-bs-target="#commandMetadataInfoModal">
              변화 후
            </button>
        `).attr('data-next-move-id', moveInfo.nextMoveId);
        $('#abilityInfoModal .close-ability-info-modal-button').before($nextMoveInfoButton);
    }

    $('.ability-info-modal-button').click();
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
    let cooldownString = cooldown >= 0 && cooldown < 999 ? cooldown + ' 턴' : '재사용 불가';

    // 커맨드 정보
    let $commandInfoWrapper = null;

    if (isSummon && isUnionSummon) {
        // 소환(합체소환)
        let doUnionSummon = gameStateManager.getState('doUnionSummon');
        let summonName = moveInfo.name.slice(0, moveInfo.name.indexOf('('));
        let memberName = moveInfo.name.slice(moveInfo.name.indexOf('('));
        let $unionSummonWrapper = $(`       
          <div class="ability-info-wrapper">
            <div class="ability-info-icon-wrapper">
              <img class="ability-info-icon" src="${imageSrc}">
            </div>
            <div class="ability-info-text-wrapper">
              <div class="ability-info-name">${summonName} <span class="font-lg">${memberName}</span>
              </div>
              <div class="ability-info-text">
                  ${TooltipParser.parse(moveInfo.info)}
              </div>
              <div class="do-union-summon-check-wrapper">
                <div class="form-check form-switch">
                      <input class="form-check-input" type="checkbox" role="switch" id="doUnionSummonCheck">
                      <label class="form-check-label" for="doUnionSummonCheck">합체 소환</label>
                </div>
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
        let moveName = moveInfo.type === 'CHARGE_ATTACK' ? '오의: ' + moveInfo.name : moveInfo.name;
        $commandInfoWrapper = $(`
            <div class="ability-info-wrapper">
              <div class="ability-info-icon-wrapper">
                <img class="ability-info-icon" src="${imageSrc}">
              </div>
              <div class="ability-info-text-wrapper">
                <div class="ability-info-name">${moveName}</div>
                <div class="ability-info-text">
                  ${TooltipParser.parse(moveInfo.info)}
                </div>
              </div>                 
            </div>
        `);
        if (moveInfo.damageRate > 0) {
            $commandInfoWrapper.find('.ability-info-text').prepend($(`
                <div class="damage-info fw-semibold">
                  <i class="bi bi-claude text-pink"></i> 데미지: ${moveInfo.damageRate.toFixed(1)}배
                  <span>${moveInfo.hitCount > 0 ? ` X ${moveInfo.hitCount}회` : ''}</span>
                </div>
            `));
        }
        if (moveInfo.type === 'ABILITY' || moveInfo.type === 'SUMMON') {
            $commandInfoWrapper.find('.ability-info-text-wrapper').append($(`
            <div class="ability-info-text cooldown">
              <i class="bi bi-clock-fill text-dark-yellow"></i> 쿨타임: ${cooldownString} 
            </div>`));
        }
    }

    return $commandInfoWrapper;
}

function initChargeAttackPopovers() {
    $('.open-move-popover').each(function () {
        // 기존 popover 전부 제거
        bootstrap.Popover.getInstance(this)?.dispose();
    });

    // 등록 시작
    $('.open-move-popover').each(function () {
        const $btn = $(this);
        const actorId = Number($btn.attr('data-actor-id'));
        if (!actorId) return; // slick-cloned 는 id 설정 안됨

        const moveType = MoveType.byName($btn.attr('data-move-type'));
        const moveOrder = Number($btn.attr('data-move-order'));

        const moveInfo = moveType === MoveType.CHARGE_ATTACK
            ? Object.values(gameStateManager.getState('chargeAttack')).find(ca => ca.actorId === actorId)
            : gameStateManager.getState(`supportAbility.${actorId}`)[moveOrder - 1];
        if (!moveInfo) return;

        let $container = $(`<div class="command-info-container no-icon">`);

        // 상태효과
        if (moveInfo.statusEffects.length > 0) {
            let $statusEffectWrapper = createStatusWrapperElement(moveInfo.statusEffects, {metadata: true});
            $container.append($statusEffectWrapper);
        }

        // 정보
        let $commandInfoWrapperElement = createCommandInfoWrapperElement(moveInfo);
        let $popoverCloseButton = $('<div class="move-popover-close"><i class="bi bi-x-circle-fill"></i></div>')
        $commandInfoWrapperElement.append($popoverCloseButton);
        $container.append($commandInfoWrapperElement);

        let popoverOffsetClass = 'popover-' + moveOrder;
        if (moveType === MoveType.CHARGE_ATTACK && moveInfo.actorIndex > 1) {
            popoverOffsetClass = 'popover-1'; // 오의 popover 는 두번째캐릭터 부터 왼쪽으로 틀어짐
        }

        const popover = new bootstrap.Popover($btn[0], {
            html: true,
            customClass: `command-popover ${popoverOffsetClass}`,
            trigger: 'manual',
            placement: 'top',
            container: '#container',
            content: $container.get(0).outerHTML,
            popperConfig: function(defaultConfig) {
                defaultConfig.modifiers = defaultConfig.modifiers.map(modifier => {
                    if (modifier.name === 'flip') {
                        return { ...modifier, options: { fallbackPlacements: [] } }; // data-popper-placement 를 top 으로 고정, 나머지 설정 기본값 보존
                    }
                    return modifier;
                });
                return defaultConfig;
            }
        });

        $btn[0].addEventListener('inserted.bs.popover', function () {
            let $commandPopover = $('#container .command-popover').last();
            let $popoverArrow = $commandPopover.find('.popover-arrow');
            let arrowMatrixOrigin = new DOMMatrix($popoverArrow.css('transform'));

            // 팝오버 위치고정
            requestAnimationFrame(() => {
                const popoverMatrix = new DOMMatrix($commandPopover.css('transform'));
                let popoverMatrixDelta = 20 - popoverMatrix.e;
                popoverMatrix.e = 20; // x축 20 고정
                $commandPopover.css('transform', popoverMatrix.toString());

                const arrowMatrix = new DOMMatrix($popoverArrow.css('transform'));
                arrowMatrix.e = arrowMatrix.e - popoverMatrixDelta;
                $popoverArrow.css('transform', arrowMatrix.toString());

                requestAnimationFrame(() => {
                    // 보험
                    const popoverMatrix = new DOMMatrix($commandPopover.css('transform'));
                    let popoverMatrixDelta = 20 - popoverMatrix.e;
                    popoverMatrix.e = 20; // x축 20 고정
                    $commandPopover.css('transform', popoverMatrix.toString());

                    const arrowMatrix = new DOMMatrix($popoverArrow.css('transform'));
                    if (arrowMatrixOrigin.e === arrowMatrix.e) {
                        arrowMatrix.e = arrowMatrix.e - popoverMatrixDelta;
                        $popoverArrow.css('transform', arrowMatrix.toString());
                    }
                })
            })
        });

        $btn[0].addEventListener('shown.bs.popover', function () {
            // 팝오버 위치 고정
            let $commandPopover = $('#container .command-popover');
            const popoverMatrix = new DOMMatrix($commandPopover.css('transform'));
            popoverMatrix.e = 20; // x축 20 고정
            $commandPopover.css('transform', popoverMatrix.toString());
            // arrow 는 안건드려도 됨

            // 닫기버튼
            document.querySelector('.move-popover-close')?.addEventListener('click', () => popover.hide(), {once: true});
        });

        $btn.on('click', function (e) {
            e.stopPropagation();
            // 다른 팝오버 전부 닫기
            $('.open-move-popover').not($btn).each(function () {
                bootstrap.Popover.getInstance(this)?.hide();
            });
            popover.toggle();
        });
    });

    // 외부 클릭 dismiss
    $(document).off('click.movePopoverDismiss').on('click.movePopoverDismiss', function (e) {
        if (!$(e.target).closest('.popover, .open-move-popover').length) {
            $('.open-move-popover').each(function () {
                bootstrap.Popover.getInstance(this)?.hide();
            });
        }
    });
}


