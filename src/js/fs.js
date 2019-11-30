import * as std from "std";
import * as os from "os";

function createParser() {
    var index = 0;
    const args = arguments;
    return function parse(type) {
        if (args.length <= index)
            return undefined;
        if (type === 'undefined' || typeof args[index] === type)
            return args[index++];
    }
}

function open() {
    const parse = createParser(...arguments);
    const path = parse('string');
    let flags = parse('string') || 'r';
    let mode;
    if (flags)
        mode = parse('number');
    else
        mode = undefined;
    const callback = parse('function');

    var stdFlags = 0;
    if (flags.contains('r'))
        stdFlags |= os.O_RDONLY;
    if (flags.contains('w'))
        stdFlags |= os.O_WRONLY;

    try {
        const fd = std.open(path, stdFlags);
        setImmediate(callback, null, fd)
    }
    catch (e) {
        setImmediate(callback, e);
    }
}

function probeBuffer(buffer) {
    return buffer.buffer || buffer;
}

function read(fd, buffer, offset, length, position, callback) {
    if (typeof position === 'number') {
        const file = std.fdopen(os.dup(fd), 'r');
        const curPos = os.tell(fd);
        file.seek(position, std.SEEK_SET);
        const read = file.read(probeBuffer(buffer), offset, length);
        file.seek(curPos, std.SEEK_SET);
        file.close()
        const err = read === -1 ? new Error('read error') : undefined;
        setImmediate(callback, err, read, buffer);
        return;
    }
    const read = os.read(fd, probeBuffer(buffer), offset, length);
    const err = read === -1 ? new Error('read error') : undefined;
    setImmediate(callback, err, read, buffer);
}

function write() {
    const parse = createParser(...arguments);
    const fd = parse('number');
    const buffer = parse();
    const offset = parse('number');
    const length = parse('number');
    const position = parse('number');
    const callback = parse('function');

    if (typeof position === 'number') {
        const file = std.fdopen(os.dup(fd), 'r');
        const curPos = os.tell(fd);
        file.seek(position, std.SEEK_SET);
        const written = file.write(probeBuffer(buffer), offset, length);
        file.seek(curPos, std.SEEK_SET);
        file.close()
        const err = read === -1 ? new Error('write error') : undefined;
        setImmediate(callback, err, written, buffer);
        return;
    }
    const written = os.write(fd, probeBuffer(buffer), offset, length);
    const err = read === -1 ? new Error('read error') : undefined;
    setImmediate(callback, err, written, buffer);
}

function fstat() {
    
}
function statSync() {

}
function stat() {
    
}

function ftruncate() {

}

function unlink() {

}

function rmdir() {

}


function mkdir() {

}

function mkdirSync() {

}



export function registerFsModule() {
    const exports = {
        open,
        read,
        write,
        fstat,
        statSync,
        stat,
        ftruncate,
        unlink,
        rmdir,
        mkdir,
        mkdirSync,
    }
    const module = { exports };

    open('a', 'b', function() {})


    globalThis.rootRequire.cache['fs'] = module;
}