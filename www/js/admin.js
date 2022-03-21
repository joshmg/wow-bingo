window.app.admin = {};
window.app.admin.super = {};

window.app.admin.super.bind = window.app.bind;
window.app.bind = function() {
    window.app.admin.super.bind();
    window.app.admin.bind();
};

window.app.admin.super.createWebSocket = window.app.createWebSocket;
window.app.createWebSocket = function() {
    window.app.admin.super.createWebSocket();

    const superOnMessage = window.app.webSocket.onmessage;
    window.app.webSocket.onmessage = function(event) {
        const superReturnValue = superOnMessage(event);

        const response = JSON.parse(event.data);
        if (response.globalGameState) {
            window.app.admin.globalGameState = response.globalGameState;
            window.app.renderGlobalGameState(response.globalGameState);
        }
        if (response.players) {
            window.app.data.players = response.players;

            const gameState = window.app.admin.globalGameState;
            if (gameState) {
                window.app.renderGlobalGameState(gameState);
            }
        }
        if (response.bingoWinners) {
            const gameState = window.app.admin.globalGameState;
            if (gameState) {
                window.app.renderGlobalGameState(gameState);
            }
        }

        return superReturnValue;
    };
};


window.app.admin.bind = function() {
    const setPassword = function(password) {
        window.app.admin.password = password;

        const passwordContainer = $("#password-container");
        passwordContainer.toggle(false);
    };

    const firstRender = function() {
        window.app.getGlobalGameState(function(globalGameState) {
            if (! globalGameState) {
                console.log("Unable to load game state.");
                return;
            }

            window.app.renderGlobalGameState(globalGameState);
            window.app.getBingoWinners();
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
        if (typeof callback == "function") {
            callback(response.wasSuccess ? response.globalGameState : null);
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
        if (typeof callback == "function") {
            callback(response.wasSuccess ? response.globalGameState : null);
        }
    });
};

window.app.banWinner = function(username, callback) {
    window.app.send({
        "query": "banWinner",
        "parameters": {
            "password": window.app.admin.password,
            "username": username
        }
    }, function(response) {
        if (typeof callback == "function") {
            callback(response.wasSuccess ? response.globalGameState : null);
        }
    });
};

window.app.setHasPaid = function(username, hasPaid, callback) {
    window.app.send({
        "query": "setHasPaid",
        "parameters": {
            "password": window.app.admin.password,
            "username": username,
            "hasPaid": hasPaid,
        }
    }, function(response) {
        if (typeof callback == "function") {
            callback(response.wasSuccess ? response.players : null);
        }
    });
};

window.app.renderGlobalGameState = function(globalGameState) {
    const labels = window.app.data.labels || [];
    const players = window.app.data.players || [];
    const bingoWinners = window.app.data.bingoWinners || [];

    const main = $("#main");
    const bingoContainer = $("#bingo");
    const playersContainer = $("#players");
    const winnersContainer = $("#winners");

    // Render bingo squares...
    (function() {
        bingoContainer.empty();
        for (let index = 0; index < labels.length; ++index) {
            const label = labels[index];

            const div = $("<div></div>");
            div.toggleClass("marked", globalGameState[index]);

            const span = $("<span></span>");
            span.toggleClass("noselect", true);

            span.text(label);
            div.append(span);

            div.bind("click", function() {
                const isMarked = globalGameState[index];
                window.app.updateGlobalGameState(index, isMarked ? 0 : 1);
            });

            bingoContainer.append(div);
        }
    })();

    // Render players...
    (function() {
        const sanitizeName = function(name) {
            const div = $("<div></div>");
            div.text(name);
            return div.html();
        };

        const uncheckedBox = "&#x2610;";
        const checkedBox = "&#x2611;";

        playersContainer.empty();
        for (let index = 0; index < players.length; ++index) {
            const player = players[index];

            const div = $("<div></div>");

            const span = $("<span></span>");
            span.toggleClass("noselect", true);

            span.html((player.hasPaid ? checkedBox : uncheckedBox) + " " + sanitizeName(player.name));
            div.append(span);

            div.bind("click", function() {
                window.app.setHasPaid(player.name, (player.hasPaid ? false : true), function(players) {
                    if (! players) { return; }

                    window.app.data.players = players;
                    window.app.renderGlobalGameState(window.app.admin.globalGameState);
                });
            });

            playersContainer.append(div);
        }
    })();


    // Render winners...
    (function() {
        winnersContainer.empty();
        for (let index = 0; index < bingoWinners.length; ++index) {
            const player = bingoWinners[index];

            const div = $("<div></div>");

            const span = $("<span></span>");
            span.toggleClass("noselect", true);

            span.text(player.name + ": " + (player.amount / 100) + " G");
            div.append(span);

            winnersContainer.append(div);
        }
    })();

    main.toggleClass("hidden", false);
};

// Override
window.app.init = function() {
    window.app.send({"query": "getLabels"}, function(response) {
        if (! response.wasSuccess) {
            console.log(response);
            return;
        }

        window.app.data.labels = response.labels;

        window.app.send({"query": "getPlayers"}, function(response) {
            if (! response.wasSuccess) {
                console.log(response);
                return;
            }

            window.app.data.players = response.players;
            window.app.bind();
        });
    });
};

window.setInterval(function() {
    window.app.getGlobalGameState();
}, 30000);
