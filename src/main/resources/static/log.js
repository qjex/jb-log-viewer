let ws;
let container;
let error = false;

// TODO add command constants

function init(fileName, line) {
    container = $("#container");
    let query = 'http://' + location.host + '/view?fileName=' + fileName;
    ws = new SockJS(query);
    ws.onmessage = function (data) {
        handler(data.data);
    };
    ws.onclose = function () {
        if (!error) {
            setTimeout(init, 5000, fileName, line)
        }
    };
    container.scroll(handleScrollEvent);
    if (line == null) {
        line = 1
    }
    ws.onopen = function () {
        setLine(line)
    };
}

function handleScrollEvent() {
    if (container.scrollTop() < 400) {
        for (let i = 0; i < 10; i++) {
            sendExtendTop();
            sendRemoveBottom();
        }
    }

    if (container[0].scrollTop + container[0].clientHeight + 400 >= container[0].scrollHeight) {
        for (let i = 0; i < 10; i++) {
            sendExtendBottom();
            sendRemoveTop();
        }
    }
}

function removeTop() {
    let firstLine = $(".log_line:first");
    let nextLine = firstLine.next();
    let curOffset = container.scrollTop() - nextLine.offset().top;
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

function setError(error) {
    error = true;
    container.empty();
    container.append('<p class="error">' + error + '</p>');
    ws.close();
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
    switch (parts[0]) {
        case "1":
        case "3":
            if (parts[1] === "0") { // no new lines no append
                break
            }
            appendLine(parts[1] + ") " + parts[2]);
            break;
        case "2":
            if (parts[1] === "0") { // no new lines no prepend
                break
            }
            prependLine(parts[1] + ") " + parts[2]);
            break;
        case "4":
            if (parts[1] === "true") {
                removeTop()
            }
            break;
        case "5":
            if (parts[1] === "true") {
                removeBottom()
            }
            break;
        case "6":
            // TODO: scroll to bottom
            appendLine(parts[1] + ") " + parts[2]);
            break;
        case "7":
            setError(parts[1]);
            break;
    }
}
