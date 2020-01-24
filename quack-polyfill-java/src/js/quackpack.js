const fs = require('fs');
const path = require('path');
const dgram = require('dgram');
const net = require('net');
const tls = require('tls');
const crypto = require('crypto');
const dns = require('dns');
const mkdirp = require('mkdirp').sync;
const rimraf = require('rimraf').sync;

const filesRead = {};

const dist = path.join(__dirname, 'dist/webtorrent-dist');
rimraf(dist);

function readFile(filename) {
    var file = path.join(__dirname, filename);
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory())
        return null;
    var ret = fs.readFileSync(file).toString();
    const relFile = path.relative(__dirname, filename);
    filesRead[relFile] = ret;

    const distFile = path.join(dist, relFile);
    mkdirp(path.dirname(distFile));
    fs.copyFileSync(file, distFile);
    return ret;
}

function evalScript(script, filename) {
    return eval(script);
}

const quackRequireFactory = evalScript(readFile('require.js'))

const quackRequire = quackRequireFactory(readFile, evalScript, {});

const defaultModules = {
    fs,
    dgram,
    net,
    tls,
    dns,
}

for (var module of Object.keys(defaultModules)) {
    quackRequire.cache[module] = { exports: defaultModules[module] }
}

const webTorrent = quackRequire('webtorrent');
const SMB2 = quackRequire('@marsaud/smb2');
const browserCrypto = quackRequire('crypto');

// these are dynamically imported.
quackRequire('@marsaud/smb2/lib/messages/close.js');
quackRequire('@marsaud/smb2/lib/messages/create.js');
quackRequire('@marsaud/smb2/lib/messages/create_folder.js');
quackRequire('@marsaud/smb2/lib/messages/negotiate.js');
quackRequire('@marsaud/smb2/lib/messages/open.js');
quackRequire('@marsaud/smb2/lib/messages/open_folder.js');
quackRequire('@marsaud/smb2/lib/messages/query_directory.js');
quackRequire('@marsaud/smb2/lib/messages/read.js');
quackRequire('@marsaud/smb2/lib/messages/session_setup_step1.js');
quackRequire('@marsaud/smb2/lib/messages/session_setup_step2.js');
quackRequire('@marsaud/smb2/lib/messages/set_info.js');
quackRequire('@marsaud/smb2/lib/messages/tree_connect.js');
quackRequire('@marsaud/smb2/lib/messages/write.js');
quackRequire('@marsaud/smb2/lib/structures/close.js');
quackRequire('@marsaud/smb2/lib/structures/constants.js');
quackRequire('@marsaud/smb2/lib/structures/create.js');
quackRequire('@marsaud/smb2/lib/structures/negotiate.js');
quackRequire('@marsaud/smb2/lib/structures/query_directory.js');
quackRequire('@marsaud/smb2/lib/structures/read.js');
quackRequire('@marsaud/smb2/lib/structures/session_setup.js');
quackRequire('@marsaud/smb2/lib/structures/set_info.js');
quackRequire('@marsaud/smb2/lib/structures/tree_connect.js');
quackRequire('@marsaud/smb2/lib/structures/write.js');

console.log(webTorrent);
console.log(SMB2);
console.log(browserCrypto);
