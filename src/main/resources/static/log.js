let ws;
let container;
let error = false;

const SET_LINE_COMMAND = "1";
const EXTEND_TOP_COMMAND = "2";
const EXTEND_BOTTOM_COMMAND = "3";
const SERVER_APPEND_COMMAND = "4";
const ERROR_COMMAND = "5";
let extendProcessed = true;

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
            // setTimeout(init, 5000, fileName, line)
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
        sendExtendTop();
    }

    if (container[0].scrollTop + container[0].clientHeight + 400 >= container[0].scrollHeight) {
        sendExtendBottom();
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

function makeLine(line) {
    return '<p class="log_line" >' + line + '</p>'
}

function prependData(data) {
    let firstLine = $(".log_line:first");
    let curOffset = container.scrollTop() - firstLine.offset().top;
    container.prepend(data);
    container.scrollTop(firstLine.offset().top + curOffset);
}

function appendData(data) {
    container.append(data);
}

function setLine(line) {
    container.empty();
    sendSetLine(line);
    sendExtendTop()
}

function handleError(error) {
    error = true;
    container.empty();
    container.append('<p class="error">' + error + '</p>');
    ws.close();
}

function sendSetLine(line) {
    ws.send(SET_LINE_COMMAND + "|" + line);
}

function sendExtendTop() {
    if (extendProcessed) {
        extendProcessed = false;
        ws.send(EXTEND_TOP_COMMAND);
    }
}

function sendExtendBottom() {
    if (extendProcessed) {
        extendProcessed = false;
        ws.send(EXTEND_BOTTOM_COMMAND);
    }
}

function handler(message) {
    console.log(message);

    let parts = message.split('|');
    let data = "";
    switch (parts[0]) {
        case EXTEND_TOP_COMMAND :
            for (let i = 0; i < parts[1]; i++) {
                removeBottom()
            }
            if (parts[2] !== "0") {
                for (let i = 3; i < parts.length; i += 2) {
                    data += makeLine(parts[i] + ") " + parts[i + 1]);
                }
                prependData(data);
            }
            extendProcessed = true;
            break;
        case EXTEND_BOTTOM_COMMAND:
            for (let i = 0; i < parts[1]; i++) {
                removeTop()
            }
            if (parts[2] !== "0") {
                for (let i = 3; i < parts.length; i += 2) {
                    data += makeLine(parts[i] + ") " + parts[i + 1]);
                }
                appendData(data);
            }
            extendProcessed = true;
            break;
        case SERVER_APPEND_COMMAND:
            // TODO: scroll to bottom and command
            break;
        case ERROR_COMMAND:
            handleError(parts[1]);
            break;
        default:
            handleError("Unexpected response from the server");
            break
    }
}
