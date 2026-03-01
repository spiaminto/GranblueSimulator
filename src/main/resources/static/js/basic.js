document.addEventListener('DOMContentLoaded', function () {
    const innerWidth = window.innerWidth;
    const scale = Math.min(innerWidth / 330, 2.0);

    const container = document.querySelector('#container');
    container.style.transform = `scale(${scale})`;
    container.style.transformOrigin = 'top left';

    const containerWidth = container.offsetWidth - 15 + 'px';
    const modalContents = document.querySelectorAll('.modal .modal-content');
    modalContents.forEach(modalContent => {
        modalContent.style.cssText += `transform: scale(${scale}); transform-origin: left; width: ${containerWidth};`;
    });

    setTimeout(() => {
        let alertMessage = document.querySelector('#alertMessage');
        alertMessage = alertMessage ? alertMessage.textContent : null;
        if (alertMessage) {
            alert(alertMessage);
        }
    }, 200);

})