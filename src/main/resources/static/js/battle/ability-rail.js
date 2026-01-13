/**
 * 어빌리티 아이콘 클릭시 <br>
 * 모달로 열지 process 할지 구분
 */
function onAbilityIconClicked(event) {
    let $target = $(event.target);
    if ($target.is('.button-click')) return;
    $target.addClass('button-click').one('animationend', () => $target.removeClass('button-click'));
    playSe(Sounds.ui.BUTTON_CLICK.src);

    console.log('[onclickAbilityIcon] this = ', this);
    let abilityId = this.dataset.moveId;
    let ability = stage.gGameStatus.ability[abilityId];

    let isShowAbilityInfoCheck = $('#showAbilityInfoCheck').is(':checked');
    if (isShowAbilityInfoCheck) {
        openAbilityInfoModal(ability);
    } else {
        processMoveClick(ability); // 어빌리티, 페이탈체인
    }
}

/**
 * 기본 커맨드 클릭 수행
 * @param moveInfo {MoveInfo} 어빌리티, 페이탈체인 / 소환석 / 포션
 */
function processMoveClick(moveInfo) {
    console.log('[processMoveClick] moveInfo = ', moveInfo);
    if (player.locked || stage.gGameStatus.isQuestCleared) return;

    let noneUsableClasses = '.not-ready, .none-usable, .on-rail';
    if (moveInfo.type === 'ABILITY') {
        // 쿨타임 검증
        let $abilityOverlay = window.abilityPanels[moveInfo.actorIndex].find(`.ability-icon[data-move-id=${moveInfo.id}] .ability-overlay`);
        let cooldown = gameStateManager.getState('abilityCoolDowns')[moveInfo.actorIndex][moveInfo.order - 1];
        if (cooldown > 0 || $abilityOverlay.is(noneUsableClasses)) return;

        // 레일 재등록 방지
        $abilityOverlay.addClass('on-rail');
    }

    if (moveInfo.type === 'SUMMON') {
        // 쿨타임 검증
        let $summonOverlay = $(`#partyCommandContainer .summon-display-wrapper .summon-list-item[data-move-id=${moveInfo.id}] .summon-overlay`);
        let cooldown = gameStateManager.getState('summonCooldowns')[moveInfo.order - 1];
        if (cooldown > 0 || $summonOverlay.is(noneUsableClasses)) return;
        
        // 레일 재등록 방지
        $summonOverlay.addClass('on-rail');
        $('.summon-display-button').click();
    }

    // 어빌리티 레일에 등록
    appendToAbilityRail(moveInfo);
}

/**
 * 공격 버튼 클릭
 */
function onAttackButtonClicked() {
    let isAttackClicked = stage.gGameStatus.isAttackClicked;
    let isQuestCleared = stage.gGameStatus.isQuestCleared;

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
        appendToAbilityRail(stage.gGameStatus.attack); // 레일에 등록
    }
}

function appendToAbilityRail(moveInfo) {
    let currentAbilityRailLength = $('#abilityRail img').length;
    $(`<div class="rail-item rail-item-${currentAbilityRailLength}"
              data-rail-item-type='${moveInfo.type}' 
              data-actor-index='${moveInfo.actorIndex}' 
              data-move-id='${moveInfo.id}'
              data-actor-id="${moveInfo.actorId}"
              data-additional-type="${moveInfo.additionalType}">
              <img src="${moveInfo.iconSrc}" alt="icon image">
        </div>`)
        .on('click', function () { // 클릭시 어빌리티 레일에서 제거 이벤트
            if ($(this).index() === 1) return; // 자신이 첫번째 어빌리티면 클릭 불가 (더미 = 0)
            // $(this).find('.ability-overlay').removeClass('not-ready'); // 오버레이 해제
            $(this).remove(); // 제거
        })
        .appendTo($('#abilityRail'));
}

function openAbilityInfoModal(moveInfo) {
    let isSummon = moveInfo.type === 'SUMMON';
    let imageSrc = isSummon ? moveInfo.portraitSrc : moveInfo.iconSrc;
    let commandName = isSummon ? ': 소환석'
        : moveInfo.type === 'ABILITY' ? ': 어빌리티' : '';

    let $unionSummonWrapper = $('');
    let unionSummonId = gameStateManager.getState('unionSummonId');
    if (isSummon && unionSummonId) {
        let doUnionSummon = stage.gGameStatus.doUnionSummon;

        let unionSummonConst = Constants.summon[unionSummonId];
        $unionSummonWrapper = $(`       
            <hr>
            <div class="ability-info-wrapper">
              <div class="ability-info-icon-wrapper">
                <img class="ability-info-icon" src="${unionSummonConst.portraitSrc}">
              </div>
              <div class="ability-info-text-wrapper">
                <div class="row">
                  <h5 class="col-7">${unionSummonConst.name}</h5>
                  <div class="col">
                    <div class="form-check form-switch do-union-summon-check-wrapper">
                      <input class="form-check-input" type="checkbox" role="switch" id="doUnionSummonCheck">
                      <label class="form-check-label" for="doUnionSummonCheck">합체 소환</label>
                    </div>
                  </div>
                </div>
                <div class="ability-info-text">
                  ${unionSummonConst.info}
                </div>
              </div>    
            </div>`);
        $unionSummonWrapper.find('#doUnionSummonCheck')
            .attr('checked', doUnionSummon)
            .on('change', function () {
                let isChecked = $(this).is(':checked');
                window.gameStateManager.setState('doUnionSummon', isChecked);
            });
    }

    let $modalContent = $(`
            <div class="modal-content">
              <div class="modal-header">
                <h4 class="modal-title">커맨드 정보 ${commandName}</h4>
              </div>
              <div class="modal-body">
                <div class="ability-info-wrapper">
                  <div class="ability-info-icon-wrapper">
                    <img class="ability-info-icon" src="${imageSrc}">
                  </div>
                  <div class="ability-info-text-wrapper">
                    <h5>${moveInfo.name}</h5>
                    <div class="ability-info-text">
                      ${moveInfo.info}
                    </div>
                  </div>                 
                </div>
              </div>
              <div class="modal-footer">
                <button type="button" class="btn btn-sm btn-secondary me-1 close-ability-info-modal-button"
                        data-bs-dismiss="modal">
                  닫기
                </button>
                <button type="button" class="btn btn-sm btn-primary use-ability-button">사용</button>
              </div>
            </div>
        `)
        .find('.modal-body')
        .append($unionSummonWrapper)
        .end()
        .find('.use-ability-button')
        .one('click', function () {
            $('#abilityInfoModal .close-ability-info-modal-button').click();
            processMoveClick(moveInfo);
        }).end();

    $('#abilityInfoModal .modal-content').replaceWith($modalContent);
    $('.ability-info-modal-button').click();
}

