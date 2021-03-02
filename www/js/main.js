window.app = {};
window.app.webSocket = null;
window.app.requestId = 0;
window.app.requests = {};
window.app.data = {};
window.app.data.labels = [];
window.app.gameState = null;

window.app.webSocketEndpoint = "/api/v1/websocket";
if (window.location.protocol == "http:") {
    window.app.webSocket = new WebSocket("ws://" + window.location.host + window.app.webSocketEndpoint);
}
else {
    window.app.webSocket = new WebSocket("wss://" + window.location.host + window.app.webSocketEndpoint);
}

window.app.send = function(message, callback) {
    const requestId = (window.app.requestId += 1);
    message.requestId = requestId;
    window.app.requests[requestId] = callback;
    window.app.webSocket.send(JSON.stringify(message));
};

window.app.init = function() {
    window.app.send({
        "query": "getLabels"
    }, function(response) {
        if (response.wasSuccess) {
            window.app.data.labels = response.labels;

            window.app.bind();
        }
    });
};

window.app.bind = function() {
    const setUsername = function(username) {
        window.app.setUser(username, function(gameState) {
            window.app.render();

            if (gameState) {
                window.app.data.gameState = gameState;
                window.app.renderGameState(gameState);
            }
        });
    };

    const setQueryStringUsername = function(username) {
        window.history.replaceState({"name": username}, username, "?name=" + username);
    };

    const usernameUi = $("#username");
    usernameUi.bind("keyup", function(event) {
        if (event.keyCode == 13) {
            const username = $(this).val();
            setQueryStringUsername(username);
            setUsername(username);
        }
    });

    const usernameButtonUi = $("#username-button");
    usernameButtonUi.bind("click", function() {
        const username = usernameUi.val();
        setQueryStringUsername(username);
        setUsername(username);
    });

    const url = new URL(window.location.href);
    const username = url.searchParams.get("name");
    if (username && username.length) {
        setUsername(username);
    }

    window.setTimeout(function() {
        usernameUi.focus();
    }, 0);
};

window.app.render = function() {
    const loginContainer = $("#login-container");
    loginContainer.toggle(false);

    const mainContainer = $("#main");
    mainContainer.toggle(true);
};

window.app.renderGameState = function(gameState) {
    const container = $("#main");
    container.empty();

    const labels = window.app.data.labels;
    const layout = window.app.data.gameState.layout;
    const marks = window.app.data.gameState.marks;

    for (let i = 0; i < layout.values.length; ++i) {
        const index = layout.values[i];
        const label = labels[index];

        const div = $("<div></div>");
        div.toggleClass("marked", marks.values[i]);
        const span = $("<span></span>");
        span.text(label);
        div.append(span);
        container.append(div);
    }

    container.toggle(true);
};

window.app.webSocket.onopen = function() {
    window.app.init();
};

window.app.webSocket.onmessage = function(event) {
    const response = JSON.parse(event.data);
    const requestId = response.requestId;

    if (requestId) {
        const callback = window.app.requests[requestId];
        window.app.requests[requestId] = null;
        if (typeof callback == "function") {
            window.setTimeout(callback, 0, response);
        }
    }
    else {
        if (response.gameState) {
            window.app.data.gameState = response.gameState;
            window.app.renderGameState(response.gameState);
        }
    }

    return false;
};

window.app.webSocket.onclose = function() {
    console.log("WebSocket closed.");
    window.app.requests = {};
};

window.app.setUser = function(user, callback) {
    window.app.send({
        "query": "getGameState",
        "parameters": {
            "username": user
        }
    }, function(response) {
        if (response.wasSuccess) {
            if (typeof callback == "function") {
                callback(response.gameState);
            }
        }
    });
};

