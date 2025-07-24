var frame_interval = null;

class PlayerUI {
    constructor(parent) {
        // the player instance
        this.player = parent;
        // the top containers, canvas and background image
        this.m_html = document.getElementById("canvasContainer");
        this.m_canvas_container = null;
        this.m_canvas = null;
        this.m_background = null;
    }

    set_html() {
        // check if html is already populated
        if (this.m_html.innerHTML.trim() != "")
            throw new Error("Element player-container is already populated.");
        // ref for callbacks
        const _player_ = this.player;
        const _ui_ = this;
        // create fragment
        let fragment = document.createDocumentFragment();
        // Canvas container
        // this.m_canvas_container = add_to(fragment, "div", {id: "canvas-container"});
        this.m_canvas = add_to(fragment, "canvas", {id: "canvasPlayer"});
        // this.set_canvas_container_size(this.player.width, this.player.height); // 640 x 640
        // canvas
        this.m_canvas.width = Player.c_canvas_size; // canvas size default is part of Player
        this.m_canvas.height = Player.c_canvas_size; //
        // this.m_canvas.style.maxWidth = "" + Player.c_canvas_size + "px";
        // this.m_canvas.style.maxHeight = "" + Player.c_canvas_size + "px";
        // background
        let bg = add_to(fragment, "div", {id: ["canvasBackground"]});
        this.m_background = add_to(bg, "img", {id: ["canvasBackgroundImage"]});

        _player_.ui.m_html.appendChild(fragment);
    }

    // set the canvas container size (the visible window)
    set_canvas_container_size(w, h) {
        this.m_canvas_container.style.minWidth = "" + w + "px";
        this.m_canvas_container.style.minHeight = "" + h + "px";
        this.m_canvas_container.style.maxWidth = "" + w + "px";
        this.m_canvas_container.style.maxHeight = "" + h + "px";
        // purely for any css styling
        this.m_canvas_container.classList.add("player-container-normal");
    }

    setBackgroundImage(url) {
        this.m_background.src = url;
    }

}