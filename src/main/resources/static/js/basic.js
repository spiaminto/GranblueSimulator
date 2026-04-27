document.addEventListener('DOMContentLoaded', function () {

    let displayScale = localStorage.getItem('displayScale');
    let displayScaleNumber = Number(displayScale);
    let scale = 1;
    if (displayScaleNumber && displayScaleNumber > 0) {
        scale = displayScaleNumber;
    } else if (displayScale === 'auto') {
        scale = Math.min(window.innerWidth / 330, 2.0);
    } else {
        localStorage.setItem('displayScale', 'auto');
        scale = Math.min(window.innerWidth / 330, 2.0);
    }
    scale = scale > 2.0 ? 2.0 : scale;

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
        alertMessage = alertMessage?.textContent || null;
        if (alertMessage) {
            alert(alertMessage);
        }
    }, 200);

    setTimeout(() => {
        let errorMessage = document.querySelector('#errorMessage');
        errorMessage = errorMessage?.textContent || null;
        if (errorMessage) {
            alert(errorMessage);
        }
    }, 200);

})