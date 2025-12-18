/**
 * 어빌리티 아이콘 클릭시 <br>
 * 모달로 열지 process 할지 구분
 */
function onAbilityIconClicked(event) {
    let $target = $(event.target);
    if ($target.is('.button-click')) return;
    $target.addClass('button-click').one('animationend', () => $target.removeClass('button-click'));
    window.effectAudioPlayer.loadAndPlay(Sounds.global.BUTTON_CLICK.src);

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

    if (moveInfo.type === 'ABILITY') {
        // 쿨타임 검증
        let $abilityOverlay = window.abilityPanels[moveInfo.actorIndex].find(`.ability-icon[data-move-id=${moveInfo.id}] .ability-overlay`);
        let cooldown = stage.gGameStatus.abilityCoolDowns[moveInfo.actorIndex][moveInfo.order - 1];
        if (cooldown > 0 || $abilityOverlay.is('.not-ready') || $abilityOverlay.is('.none-usable')) return;

        // 클릭시 오버레이 설정 : Modal 에서 사용 클릭시에도 지정해야되서, icon onclick 대신 여기서 함
        // gameStateManager.abilityCooldowns 로 상태변경시 renderer 에서 재랜더링함. (여기서는 상태 값 변경 X)
        // 여기서 즉시 오버레이를 띄워야 레일위에서 처리 대기중에 재등록 방지가능
        $abilityOverlay.addClass('not-ready');
    }

    if (moveInfo.type === 'SUMMON') {
        // 쿨타임 검증
        let $summonOverlay = $(`#partyCommandContainer .summon-display-wrapper .summon-list-item[data-move-id=${moveInfo.id}] .summon-overlay`);
        let cooldown = gameStateManager.getState('summonCooldowns')[moveInfo.order - 1];
        if (cooldown > 0 || $summonOverlay.is('.not-ready') || $summonOverlay.is('.none-usable')) return;
        $summonOverlay.addClass('not-ready');
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

    if (player.locked) return;

    if (isAttackClicked) { // attack button 클릭되어있음 -> attack cancel
        player.lockPlayer(false); // 공격 취소시 락 해제
        effectAudioPlayer.loadAndPlay(Sounds.global.CANCEL_ATTACK.src);
        window.gameStateManager.setState('isAttackClicked', false);
        $('#abilityRail .rail-item-attack').remove(); // 레일에서 삭제
    } else {
        player.lockPlayer(true); // 플레이어 잠금
        effectAudioPlayer.loadAndPlay(Sounds.global.REQUEST_ATTACK.src);
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

    let $unionSummonWrapper = $('');
    let unionSummonId = gameStateManager.getState('unionSummonId');
    if (isSummon && unionSummonId) {
        let doUnionSummon = stage.gGameStatus.doUnionSummon;

        let unionSummonConst = Constants.summon[unionSummonId];
        $unionSummonWrapper = $(`
            <br>            
            <hr>
            <br>
            <div class="ability-info-wrapper">
              <div class="ability-info-icon-wrapper">
                <img class="ability-info-icon" src="${unionSummonConst.portraitSrc}">
              </div>
              <div class="ability-info-text-wrapper">
                <div class="row">
                  <h5 class="col-8">${unionSummonConst.name}</h5>
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
                <h4 class="modal-title">커맨드 정보</h4>
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
                <button type="button" class="btn btn-secondary me-4 close-ability-info-modal-button"
                        data-bs-dismiss="modal">
                  닫기
                </button>
                <button type="button" class="btn btn-primary use-ability-button">사용</button>
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

