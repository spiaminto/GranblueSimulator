

function requestResetCoolDown() {
    let memberId = $('#memberInfo').data('member-id');
    $.ajax({
        url: '/test/reset-cool-down',
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
            location.reload();
        },
        error: function (response) {
            console.log(response);
        }
    });
}