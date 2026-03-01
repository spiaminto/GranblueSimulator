$(function () {
    $('#insertStatusDisplay').html(printStatus(insertStatus));
    // 초기 등록
    copyStatusInputAddCheckWrapper($('.input-container.charge-attack'));
    copyStatusInputAddCheckWrapper($('.input-container.ability'), 8);
    copyStatusInputAddCheckWrapper($('.input-container.summon'));
    // 상태효과 '있음' 체크하면 복사해서추가
    $('.status-input-add-check').change(function () {
        $(this).is(':checked')
            ? copyStatusInputWrapper($(this))
            : $(this).closest('.status-input-add-check-wrapper').next().remove();
    });
    // 비우기 버튼 누르면 해당 폼의 상태효과 래퍼도 추가로 지움
    $('input[type="reset"]').click(function (e) {
        $(e.target).closest('form').find('.status-input-wrapper').remove();
    })
    // onSubmit
    $('form:not(#effectCjsInputForm)').on('submit', function (e) {
        e.preventDefault();
        let form = $(this).get(0);
        let requestData = serializeJson(form);
        if (requestData.statuses && requestData.statuses.length > 0)
            requestData.statuses = requestData.statuses.filter(status => status); // 희소배열 정상화
        if (requestData.type === 'ABILITY' && !requestData.abilityType) {
            alert('어빌리티 타입 지정 바람');
            return;
        }
        requestInsert(form.target, requestData);
    });
    $('.form-save-button').on('click', function () {
        saveForm($(this)); // 폼 저장
    });
    $('.form-load-button').on('click', function () {
        restoreForm($(this)); // 폼 복원
    });

    $('.clear-move-status-button').on('click', function () {
        if (!confirm("정말 초기화 합니까?")) return;
        window.insertStatus.move = {};
        localStorage.setItem('insertStatus', JSON.stringify(insertStatus));
    });


    // 최근 저장한 상태로 자동 필드채우기
    $('.apply-insert-status-button').on('click', function () {
        let insertStatus = window.insertStatus;
        // 기본 id
        $('input[name="actorVisualId"]').val(insertStatus.actorVisualId);
        $('input[name="actorId"]').val(insertStatus.actorId);
        // move 매핑정보
        $('#moveMappingWrapper').empty()
        Object.entries(window.insertStatus.move).forEach(([moveId, moveName]) => {
            $('#moveMappingWrapper').append($(`<div>${moveName} : ${moveId}</div>`));
        });
        // effectCjs 기본값
        let actorCjsName = window.insertStatus.actorCjsName;
        if (!actorCjsName) return;
        let actorCjsId = actorCjsName.substring(actorCjsName.indexOf('_') + 1); // "npc_3040190000_03" -> "3040190000_03"
        let chargeAttackCjsName = `nsp_${actorCjsId}_s2`;
        $('.effect-cjs-input-wrapper.charge-attack .cjs-name').val(chargeAttackCjsName);
        $('.effect-cjs-input-wrapper.attack .cjs-name').get().forEach((element, index) => {
            let value = index === 0 ? `phit_${actorCjsId}` : `phit_${actorCjsId}_${index + 1}`;
            $(element).val(value);
        });
        let abilityActorId = actorCjsId.substring(0, actorCjsId.indexOf('_')); // "3040190000_03" -> "3040190000"
        $('.effect-cjs-input-wrapper.ability .cjs-name').get().forEach((element, index) => {
            $(element).val(`ab_all_${abilityActorId}_0${index + 1}`);
        });
        // 로직 id 기본값
        $('input.logic-id').each(function (index, element) {
            $(element).is('.attack') && $(element).val(`phit_${abilityActorId}`)
            $(element).is('.charge-attack') && $(element).val(`nsp_${abilityActorId}`)
            let abilityMoveType = $(element).is('.ability') && $(element).closest('form').find('select[name="type"]').val();
            abilityMoveType === 'ABILITY' ? $(element).val(`ab_${abilityActorId}_`)
                : abilityMoveType === 'SUPPORT_ABILITY' ? $(element).val(`sa_${abilityActorId}_`)
                    : $(element).val(`tr_${abilityActorId}_`);
        })
    });

})


function saveForm(target) {
    let formName = $(target).attr('data-form');
    let $form = $('#' + formName);
    // input, textarea 값 저장
    let data = {};
    $form.find('input, select, textarea').each(function () {
        let $input = $(this);
        let name = $input.attr('name');
        if (!name) return;
        data[name] = $input.is(':checkbox') ? $input.prop('checked') : $input.val();
    });
    // status-input-wrapper 체크박스 상태 별도로 저장 (show, hide용)
    data.statusChecks = $form.find('.status-input-add-check').toArray().map(inputCheck => $(inputCheck).prop('checked'));
    localStorage.setItem(formName, JSON.stringify(data));
    alert('저장');
}


function restoreForm(target) {
    let formName = $(target).attr('data-form');
    let $form = $('#' + formName);
    let data = JSON.parse(localStorage.getItem(formName));
    if (!data) {
        return;
    }
    // status 체크박스 show/hide 및 입력란 표시
    $form.find('.status-input-add-check').each(function (idx) {
        let checked = data.statusChecks && data.statusChecks[idx];
        if (!$(this).is(':checked') && checked === true)
            $(this).click();
    });
    requestAnimationFrame(() => {
        // input, textarea 값 복원
        $form.find('input, select, textarea').each(function () {
            let $input = $(this);
            let name = $input.attr('name');
            if (!name) return;
            $input.attr('type') === 'checkbox' ? $input.prop('checked', !!data[name]) : $input.val(data[name]);
        });
    });

    alert('복원');
}


/**
 *  무브별 상태효과 래퍼 초기 등록
 */
function copyStatusInputAddCheckWrapper($inputContainer, count = 5) {
    let $statusInputContainer = $inputContainer.find('.status-input-container');
    for (let i = 0; i < count; i++) {
        let $clonedAddCheckWrapper = $('.status-input-add-check-wrapper.for-copy').clone(true);
        $clonedAddCheckWrapper
            .attr('data-index', i)
            .removeClass('for-copy')
            .find('h5').text(`상태효과 ${i + 1}`);
        $statusInputContainer.append($clonedAddCheckWrapper);
    }
}

/**
 * 상태효과 입력용 .status-input-wrapper 복사 후 name 속성이 array 기반인 요소의 name 속성 값 맞게 지정 및 추가
 * @param $statusInputAddCheck
 */
function copyStatusInputWrapper($statusInputAddCheck) {
    let index = $statusInputAddCheck.closest('form').find('.status-input-add-check').index($statusInputAddCheck);
    let $cloned = $('.status-input-wrapper.for-copy').clone();
    $cloned.find('input, select, textarea').attr('name', function (i, name) {
        return name.replace(/\[\d+\]/g, `[${index}]`);  // "name = statues[0].type" 의 '[0]' 을 '[index]' 로 수정
    });
    $statusInputAddCheck.closest('.status-input-add-check-wrapper').after($cloned.removeClass('for-copy'));
}

function requestInsert(url, requestData) {
    console.log('[ajaxRequest] requestData = ', requestData);
    requestData = JSON.stringify(requestData); // requestData 는 JSON string
    $.ajax({
        type: "POST",
        url: url,
        contentType: "application/json",
        headers: {
            'X-CSRF-TOKEN': $('#csrfToken').val()
        },
        data: requestData,
        success: function (data) {
            console.log('[ajaxRequest] response data = ', data);
            alert(data.message);
            if (data.actorId) insertStatus.actorId = data.actorId;
            if (data.actorCjsName) insertStatus.actorCjsName = data.actorCjsName;
            if (data.actorVisualId) insertStatus.actorVisualId = data.actorVisualId;
            if (data.effectVisuals) insertStatus.effectVisuals = data.effectVisuals;
            if (data.moveName) {
                if (!insertStatus.move) insertStatus.move = {};
                insertStatus.move[data.moveName] = data.moveId;
            }

            // 편의를 위해 자동저장
            localStorage.setItem('insertStatus', JSON.stringify(insertStatus));
            $('#insertStatusDisplay').html(printStatus(window.insertStatus));
        },
        error: function (xhr) {
            console.error('[ajaxRequest] response xhr = ', xhr);
        }
    })
}

function printStatus(obj, level = 0) {
    const indent = '  '.repeat(level);
    let html = '';
    if (typeof obj === 'object' && obj !== null) {
        if (Array.isArray(obj)) {
            html += `${indent}<ul style="margin: 5px 0; padding-left: 20px;">\n`;
            obj.forEach((item, index) => {
                html += `${indent}  <li><span style="color: #666;">[${index}]</span>\n`;
                html += printStatus(item, level + 2);
                html += `${indent}  </li>\n`;
            });
            html += `${indent}</ul>\n`;
        } else {
            html += `${indent}<ul style="margin: 5px 0; padding-left: 20px;">\n`;
            for (const [key, value] of Object.entries(obj)) {
                html += `${indent}  <li><strong style="color: #0066cc;">${key}:</strong>\n`;
                html += printStatus(value, level + 2);
                html += `${indent}  </li>\n`;
            }
            html += `${indent}</ul>\n`;
        }
    } else {
        html += `${indent}<span style="color: #d63384;">${obj}</span>\n`;
    }
    return html;
}