let connected = false;
let ws;
let container;

function init(fileName, line) {
    container = $("#container")
    let query = 'ws://' + location.host + '/view?file=' + fileName;
    ws = new WebSocket(query);
    ws.onmessage = function (data) {
        showGreeting(data.data);
    };
    ws.onclose = function () {
        setTimeout(init, 1000, fileName, line)
    };
    connected = true;
    container.scroll(handleScrollEvent);
    if (line == null) {
        line = 1
    }
    setLine(line)
}

function handleScrollEvent() {
    if (container.scrollTop() < 20) {
        for (let i = 0; i < 10; i++) {
            prependLine("new " + i);
            removeBottom();
        }
    }

    if (container[0].scrollTop + container[0].clientHeight + 20 >= container[0].scrollHeight) {
        for (let i = 0; i < 10; i++) {
            appendLine("new down " + i);
            removeTop();
        }
        console.log('Event Fired');
    }
}

function removeTop() {
    if (linesLoaded() < 100) {
        return
    }
    let firstLine = $(".log_line:first");
    let nextLine = firstLine.next();
    let curOffset = container.scrollTop() - nextLine.offset().top;
    firstLine.remove();
    container.scrollTop(nextLine.offset().top + curOffset)
}

function removeBottom() {
    if (linesLoaded() < 100) {
        return
    }
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

    let selectedLine = container.append("<p class='log_line'>Line</p>");
    for (let i = 0; i < 20; i++) {
        appendLine("Test after")
    }
    for (let i = 0; i < 20; i++) {
        prependLine("Test")
    }
}

function sendName() {
    ws.send($("#name").val());
}

function showGreeting(message) {
    $("#greetings").append(" " + message + "");
}
