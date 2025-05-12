var jContext = Java.type("jbrowse.JsContext");

LISTENERS = {}
function Node(handle) { this.handle = handle; }
function nd(handle) { return new Node(handle); }
document = {
    querySelectorAll: function(s) {
       var handles = Java.from(jsContext.querySelectorAll(s));
       return handles.map(function(h) { return new Node(h) });
    }
}

Node.prototype.addEventListener = function(type, listener) {
    if (!LISTENERS[this.handle]) LISTENERS[this.handle] = {};
    var dict = LISTENERS[this.handle];
    if (!dict[type]) dict[type] = [];
    var list = dict[type];
    list.push(listener);
}

Node.prototype.dispatchEvent = function(evt) {
    var type = evt.type;
    var handle = this.handle;
    var list = (LISTENERS[handle] && LISTENERS[handle][type]) || [];
    for (var i = 0; i < list.length; i++) {
        list[i].call(this, evt);
    }

    return evt.do_default;
}

console = {
    log: function(x) {
        jContext.log(x);
    }
}

Node.prototype.getAttribute = function(attr) {
    return jsContext.getAttribute(this.handle, attr)
}

Object.defineProperty(Node.prototype, 'innerHTML', {
    set: function(s) {
        jsContext.innerHtmlSet(this.handle, s);
    }
});

function Event(type) {
    this.type = type
    this.do_default = true;
}

function newEvent(type) {
    return new Event(type);
}

Event.prototype.preventDefault = function() {
    this.do_default = false;
}

function XMLHttpRequest() {}

XMLHttpRequest.prototype.open = function(method, url, is_async) {
    if (is_async) throw Error("Asynchronous XHR is not supported");
    this.method = method;
    this.url = url;
}

XMLHttpRequest.prototype.send = function(body) {
    this.responseText = Java.from(jsContext.XmlHttpRequestSend(this.method, this.url, body));
}

RAF_LISTENERS = [];

function requestAnimationFrame(fn) {
    RAF_LISTENERS.push(fn);
    jsContext.requestAnimationFrame();
}

function __runRAFHandlers() {
    var handlers_copy = RAF_LISTENERS;
    RAF_LISTENERS = [];
    for (var i = 0; i < handlers_copy.length; i++) {
        handlers_copy[i]();
    }
}



