window.app = {};
window.app.webSocket = null;
window.app.requestId = 0;
window.app.requests = {};
window.app.data = {};
window.app.data.labels = [];
window.app.gameState = null;

window.app.webSocketEndpoint = "/api/v1/websocket";

window.app.createWebSocket = function() {
    if (window.app.webSocket) {
        window.app.webSocket.onopen = null;
        window.app.webSocket.onmessage = null;
        window.app.webSocket.onclose = null;

        window.app.webSocket.close();
        window.app.webSocket = null;
    }

    if (window.location.protocol == "http:") {
        window.app.webSocket = new WebSocket("ws://" + window.location.host + window.app.webSocketEndpoint);
    }
    else {
        window.app.webSocket = new WebSocket("wss://" + window.location.host + window.app.webSocketEndpoint);
    }

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
            if (typeof response.gameState != "undefined") {
                window.app.data.gameState = response.gameState;
                window.app.renderGameState(response.gameState);
            }
            if (typeof response.bingoWinners != "undefined") {
                const previousWinnerCount = (window.app.data.bingoWinners || []).length;
                window.app.data.bingoWinners = response.bingoWinners;

                const bingoWinners = window.app.data.bingoWinners.slice(previousWinnerCount);
                window.app.renderBingoWinners(bingoWinners);
            }
            if (typeof response.jackpot != "undefined") {
                window.app.data.jackpot = window.parseInt(response.jackpot);
                window.app.renderJackpot(response.jackpot);
            }

            if (response.ping) {
                const pong = { "pong": response.ping };
                window.app.webSocket.sendJson(pong);
            }
        }

        return false;
    };

    window.app.webSocket.sendJson = function(message) {
        window.app.webSocket.send(JSON.stringify(message));
    };

    window.app.webSocket.onclose = function() {
        console.log("WebSocket closed.");
        window.app.requests = {};
        window.app.webSocket = null;

        window.setTimeout(function() {
            window.app.createWebSocket();
        }, 1000);
    };
};

window.app.send = function(message, callback) {
    if (! window.app.webSocket) { return; }

    const requestId = (window.app.requestId += 1);
    message.requestId = requestId;
    window.app.requests[requestId] = callback;
    window.app.webSocket.sendJson(message);
};

window.app.init = function() {
    window.app.send({
        "query": "getLabels"
    }, function(response) {
        if (response.wasSuccess) {
            window.app.data.labels = response.labels;

            window.app.bind();
            window.app.getBingoWinners();
        }
    });
};

window.app.bind = function() {
    const setUsername = function(username) {
        window.app.setUser(username, function(gameState, jackpot) {
            window.app.render();

            if (gameState) {
                window.app.data.gameState = gameState;
                window.app.renderGameState(gameState);
            }
            if (jackpot) {
                window.app.data.jackpot = window.parseInt(jackpot);
                window.app.renderJackpot(jackpot);
            }
        });
    };

    const setQueryStringUsername = function(username) {
        window.history.replaceState({"name": username}, username, "?name=" + username);
    };

    const winnerContainer = $("#winner-container");
    winnerContainer.bind("click", function() {
        winnerContainer.fadeOut(500, function() {
            winnerContainer.removeAttr("style"); // Allow for winner re-broadcasts.
            winnerContainer.toggleClass("hidden", true);
        });
    });

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
    mainContainer.toggleClass("hidden", false);
};

window.app.renderJackpot = function(jackpot) {
    const container = $("#jackpot");
    container.text((jackpot / 100) + " G");
};

window.app.renderGameState = function(gameState) {
    const container = $("#bingo");
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
        span.toggleClass("noselect", true);

        span.text(label);
        div.append(span);
        container.append(div);
    }

    container.toggle(true);
};

window.app.renderBingoWinners = function(players) {
    const winnerContainer = $("#winner-container");
    if ( (! players) || players.length == 0) {
        winnerContainer.removeAttr("style");
        winnerContainer.toggleClass("hidden", true);
        return;
    }

    let separator = "";
    let usernamesString = "";
    for (let i = 0; i < players.length; i += 1) {
        let player = players[i];

        let username = player.name;
        username = (username.length > 0 ? (username.charAt(0).toUpperCase() + username.substring(1)) : username);

        usernamesString += separator + username;
        separator = ", ";
    }

    const verb = (players.length == 1 ? "is" : "are");
    const noun = (players.length == 1 ? "winner" : "winners");

    $(".username", winnerContainer).text(usernamesString + " " + verb + " the " + noun + "!");
    winnerContainer.toggleClass("hidden", false);
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
                callback(response.gameState, response.jackpot);
            }
        }
    });
};

window.app.getBingoWinners = function(callback) {
    window.app.send({
        "query": "getBingoWinners",
        "parameters": { }
    }, function(response) {
        if (typeof callback == "function") {
            callback(response.wasSuccess ? response.bingoWinners : null);
        }
    });
};
