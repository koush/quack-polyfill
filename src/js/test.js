import * as std from "std";
import * as os from "os";
import * as fs from './fs.js';

console.log('poops');
console.log('poops');

function ab2str(buf) {
    return String.fromCharCode.apply(null, new Uint8Array(buf));
  }

function readFile(filename) {
    const stat = os.stat(filename);
    if (stat[1] !== 0 || stat[0].mode & os.S_IFREG !== 0)
        return null;
    const fd = os.open(filename, os.O_RDONLY)
    if (fd === -1)
        return null;
    const buffer = new ArrayBuffer(stat[0].size);
    var total = 0;
    do {
        const read = os.read(fd, buffer, 0, stat[0].size - total);
        if (read === -1)
            throw new Error('read error');
        total += read;
    }
    while (total != stat[0].size);

    os.close(fd);
    return ab2str(buffer)
}

function evalScript(script, filename) {
    return std.evalScript(script, filename);
}

const require = evalScript(readFile('require.js'), 'require.js')(readFile, evalScript, globalThis);
const stream = require('stream');
global.rootRequire = require;

fs.registerFsModule();