let ws;
let container;

function init(fileName, line) {
    container = $("#container");
    let query = 'ws://' + location.host + '/view?fileName=' + fileName;
    ws = new WebSocket(query);
    ws.onmessage = function (data) {
        handler(data.data);
    };
    ws.onclose = function () {
        setTimeout(init, 1000, fileName, line)
    };
    container.scroll(handleScrollEvent);
    if (line == null) {
        line = 1
    }
    ws.onopen = function() {
        setLine(line)
    };
}

function handleScrollEvent() {
    console.log("scroll event triggered: " + container.scrollTop() + " " + container[0].clientHeight + " " + container[0].scrollHeight);
    if (container.scrollTop() < 100) {
        for (let i = 0; i < 10; i++) {
            sendExtendTop();
            sendRemoveBottom();
        }
    }

    if (container[0].scrollTop + container[0].clientHeight + 100 >= container[0].scrollHeight) {
        for (let i = 0; i < 10; i++) {
            sendExtendBottom();
            sendRemoveTop();
        }
    }
}

function removeTop() {
    console.log("remove top");
    let firstLine = $(".log_line:first");
    let nextLine = firstLine.next();
    let curOffset = container.scrollTop() - nextLine.offset().top;
    console.log("remove top: " + curOffset);
    firstLine.remove();
    container.scrollTop(nextLine.offset().top + curOffset)
}

function removeBottom() {
    let lastLine = $(".log_line:last");
    lastLine.remove();
}

function linesLoaded() {
    return $(".log_line").length
}

function prependLine(line) {
    let firstLine = $(".log_line:first");
    let curOffset = container.scrollTop() - firstLine.offset().top;
    container.prepend('<p class="log_line" >' + line + '</p>');
    container.scrollTop(firstLine.offset().top + curOffset);
}

function appendLine(line) {
    container.append('<p class="log_line" >' + line + '</p>');
}

function setLine(line) {
    container.empty();
    sendSetLine(line);

    for (let i = 0; i < 20; i++) {
        sendExtendBottom()
    }
    for (let i = 0; i < 20; i++) {
        sendExtendTop();
    }
}

function sendSetLine(line) {
    ws.send("1|" + line);
}

function sendExtendTop() {
    ws.send("2");
}

function sendExtendBottom() {
    ws.send("3");
}

function sendRemoveTop() {
    if (linesLoaded() < 100) {
        return
    }
    console.log("sent remove top");
    ws.send("4");
}

function sendRemoveBottom() {
    if (linesLoaded() < 100) {
        return
    }
    ws.send("5");
}


function handler(message) {
    console.log(message);

    let parts = message.split('|');
    if (parts[1] === "0") {
        return
    }
    if (parts[0] === "1" || parts[0] === "3") {
        appendLine(parts[1] + ") " + parts[2]);
    }
    if (parts[0] === "2") {
        prependLine(parts[1] + ") " + parts[2]);
    }
    if (parts[0] === "4" && parts[1] === "true") {
        removeTop()
    }
    if (parts[0] === "5" && parts[1] === "true") {
        removeBottom()
    }
}
