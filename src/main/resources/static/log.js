const SET_LINE_COMMAND = "1";
const EXTEND_TOP_COMMAND = "2";
const EXTEND_BOTTOM_COMMAND = "3";
const ERROR_COMMAND = "4";

let ws;
let container;
let table;

let error = false;
let extendTopProcessed = true;
let extendBottomProcessed = true;

function init(fileName, line) {
    container = $("#container");
    table = $("#container table");
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

function makeLine(line, data) {
    return '<tr class="log_line"> <td class="line_num">' + line + '</td><td>' + data + '</td></tr>'
}

function prependData(data) {
    let firstLine = $(".log_line:first");
    let curOffset = container.scrollTop() - firstLine.offset().top;
    table.prepend(data);
    container.scrollTop(firstLine.offset().top + curOffset);
}

function appendData(data) {
    table.append(data);
}

function setLine(line) {
    table.empty();
    sendSetLine(line);
    handleScrollEvent();
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
    if (extendTopProcessed) {
        extendTopProcessed = false;
        ws.send(EXTEND_TOP_COMMAND);
    }
}

function sendExtendBottom() {
    if (extendBottomProcessed) {
        extendBottomProcessed = false;
        ws.send(EXTEND_BOTTOM_COMMAND);
    }
}

function handler(message) {
    console.log(message);

    let parts = message.split('|');
    switch (parts[0]) {
        case EXTEND_TOP_COMMAND :
            processExtendCommand(removeBottom, prependData, parts);
            extendTopProcessed = true;
            break;
        case EXTEND_BOTTOM_COMMAND:
            processExtendCommand(removeTop, appendData, parts);
            extendBottomProcessed = true;
            break;
        case ERROR_COMMAND:
            handleError(parts[1]);
            break;
        default:
            handleError("Unexpected response from the server");
            break
    }
}

function processExtendCommand(removeDataFunction, addDataFunction, parts) {
    for (let i = 0; i < parts[1]; i++) {
        removeDataFunction()
    }
    let data = "";
    if (parts[2] !== "0") {
        for (let i = 3; i < parts.length; i += 2) {
            data += makeLine(parts[i], decodeURIComponent(parts[i + 1].replace(/\+/g, ' ')));
        }
        addDataFunction(data);
    }
}