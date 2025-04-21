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
        console.log(this.handle);
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

