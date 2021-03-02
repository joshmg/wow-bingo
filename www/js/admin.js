window.app.admin = {};

window.app.superBind = window.app.bind;
window.app.bind = function() {
    window.app.superBind();
    window.app.admin.bind();
};

window.app.webSocket.superOnmessage = window.app.webSocket.onmessage;
window.app.webSocket.onmessage = function(event) {
    const superReturnValue = window.app.webSocket.superOnmessage(event);

    const response = JSON.parse(event.data);
    if (response.globalGameState) {
        window.app.admin.globalGameState = response.globalGameState;
        window.app.renderGlobalGameState(response.globalGameState);
    }

    return superReturnValue;
};

window.app.admin.bind = function() {
    const setPassword = function(password) {
        window.app.admin.password = password;

        const passwordContainer = $("#password-container");
        passwordContainer.toggle(false);
    };

    const firstRender = function() {
        window.app.getGlobalGameState(function(globalGameState) {
            window.app.renderGlobalGameState(globalGameState);
        });
    };

    const passwordUi = $("#password");
    passwordUi.bind("keyup", function(event) {
        if (event.keyCode == 13) {
            const password = $(this).val();
            setPassword(password);
            firstRender();
        }
    });

    const passwordButtonUi = $("#password-button");
    passwordButtonUi.bind("click", function() {
        const password = passwordUi.val();
        setPassword(password);
        firstRender();
    });

    window.setTimeout(function() {
        passwordUi.focus();
    }, 0);
};

window.app.getGlobalGameState = function(callback) {
    window.app.send({
        "query": "getGlobalGameState",
        "parameters": {
            "password": window.app.admin.password
        }
    }, function(response) {
        if (response.wasSuccess) {
            if (typeof callback == "function") {
                callback(response.globalGameState);
            }
        }
    });
};

window.app.updateGlobalGameState = function(index, isMarked, callback) {
    window.app.send({
        "query": "updateGlobalGameState",
        "parameters": {
            "index": index,
            "isMarked": (isMarked ? 1 : 0),
            "password": window.app.admin.password
        }
    }, function(response) {
        if (response.wasSuccess) {
            if (typeof callback == "function") {
                callback(response.globalGameState);
            }
        }
    });
};

window.app.renderGlobalGameState = function(globalGameState) {
    const container = $("#main");
    container.empty();

    const labels = window.app.data.labels;

    for (let index = 0; index < labels.length; ++index) {
        const label = labels[index];

        const div = $("<div></div>");
        div.toggleClass("marked", globalGameState[index]);
        const span = $("<span></span>");
        span.text(label);
        div.append(span);

        div.bind("click", function() {
            const isMarked = globalGameState[index];
            window.app.updateGlobalGameState(index, isMarked ? 0 : 1);
        });

        container.append(div);
    }

    container.toggle(true);
};

window.setInterval(function() {
    window.app.getGlobalGameState();
}, 5000);