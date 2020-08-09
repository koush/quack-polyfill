(function(readFile, evalScript, global) {

function readJsonFile(filename) {
    const string = readFile(filename);
    if (!string)
        return;
    return JSON.parse(string);
}

function requireFactory(callingScript, isBrowser) {
    const require = function require(moduleName) {
        const currentPath = path.dirname(callingScript);
        const isPath = moduleName.startsWith('/') || moduleName.startsWith('.') || moduleName.startsWith('\\');
    
        // require cache can be set externally, so do a straight name check here.
        if (requireCache[moduleName])
            return requireCache[moduleName].exports;

        if (builtins[moduleName])
            return requireBuiltin(moduleName).exports;

        return requireFind(moduleName, currentPath, isPath, isBrowser).exports;
    }

    require.cache = requireCache;
    return require;
}

function requireLoadInternal(scriptString, exports, __require, newModule, filename, dirname, process) {
    if (!scriptString)
        return;
    if (filename.endsWith('.json')) {
        newModule.exports = JSON.parse(scriptString);
        return newModule;
    }
    // const moduleFunc = new Function('exports', 'require', 'module', '__filename', '__dirname', scriptString);
    const wrapped = `(function(exports, require, module, __filename, __dirname, process){${scriptString}})`;
    const moduleFunc = evalScript(wrapped, filename);
    moduleFunc(exports, __require, newModule, filename, dirname, process);
    return newModule;
}

function requireLoad(scriptString, filename, module, isBrowser) {
    return requireLoadInternal(scriptString, module.exports, requireFactory(filename, isBrowser), module, filename, path.dirname(filename), process);
}

const builtins = {
    inherits: {
        name: 'inherits',
        browser: true,
    },
    crypto: {
        name: 'crypto-browserify',
        browser: true,
    },
    zlib: 'browserify-zlib',
    assert: 'assert',
    url: 'url',
    'builtin-status-codes': {
        name: 'builtin-status-codes',
        browser: true,
    },
    process: {
        name: 'process',
        browser: true,
    },
    os: "os-browserify",
    path: "./path",
    buffer: "buffer",
    buffertools: "browserify-buffertools",
    https: "https-browserify",
    stream: "stream-browserify",
    http: "stream-http",
    events: 'events',
    util: 'util',
    tty: 'tty-browserify',
    querystring: 'querystring-es3',
};

function requireLoadSingleFile(fullname) {
    const found = requireCache[fullname];
    if (found)
        return found;
    const fileString = readFile(fullname)
    if (!fileString)
        return;
    const ret = requireLoad(fileString, fullname, createModule(fullname));
    return ret;
}
function appendScriptExtension(file) {
    return `${file}.js`;
}

function requireLoadFile(fullname) {
    let ret = requireLoadSingleFile(appendScriptExtension(fullname));
    if (ret)
        return ret;
    return requireLoadSingleFile(fullname);
}

function createModule(fullname) {
    const module = {
        exports: {}
    };
    requireCache[fullname] = module;
    return module;
}

function requireLoadPackage(fullpath, isBrowser) {
    const found = requireCache[fullpath];
    if (found)
        return found;
    const packageJson = readJsonFile(path.join(fullpath, 'package.json'));
    if (!packageJson)
        return;

    main = path.join(fullpath, (isBrowser && typeof packageJson.browser == 'string' ? packageJson.browser : packageJson.main) || 'index.js');
    let fileString = readFile(main);
    if (!fileString)
        fileString = readFile(appendScriptExtension(main));
    if (!fileString) {
        main = path.join(main, 'index.js')
        fileString = readFile(main);
    }
    const ret = requireLoad(fileString, main, createModule(fullpath), isBrowser);
    return ret;
}

function requireFind(name, directory, isPath, isBrowser) {
    if (isPath) {
        let fullname = path.join(directory, name);
        let ret = requireLoadFile(fullname);
        if (!ret)
            ret = requireLoadFile(path.join(fullname, 'index.js'))
        return ret;
    }

    let parent = directory;

    do {
        directory = parent;

        let fullpath = path.join(directory, 'node_modules', name);
        let ret = requireLoadPackage(fullpath, isBrowser);
        if (ret)
            return ret;
        if (!ret)
            ret = requireLoadFile(path.join(fullpath, 'index.js'))
        if (ret)
            return ret;
        ret = requireLoadFile(fullpath);
        if (ret)
            return ret;

        parent = path.dirname(directory);
    }
    while (!isPath && directory !== parent);
    throw new Error(`unable to load ${name}`);
}

function requireBuiltin(moduleName) {
    const modulePath = path.join('./node_modules', builtins[moduleName].name || builtins[moduleName]);
    let builtin = builtins[moduleName]
    var ret = requireLoadPackage(modulePath, builtin && builtin.browser);
    requireCache[moduleName] = ret;
    return ret;
}

const requireCache = {};
// require  imeplementation needs the path module, but can't require without path being ready.
// chicken egg problem.
function requirePath() {
    const pathDir = `./`;
    const pathPath = `${pathDir}/path.js`;
    const pathScript = readFile(pathPath);
    const module = createModule(pathPath);
    const ret = requireLoadInternal(pathScript, module.exports, null, module, pathPath, pathDir);
    if (!ret)
        throw new Error('unable to load path module');
    requireCache[`${pathDir}`] = ret;
    requireCache['path'] = ret;
    return ret.exports;
}

const path = requirePath();
global.global = global;

const require = requireFactory('./require.js');
require.builtins = builtins;

let process = {};
process = require('process');
// for debug module
process.type = 'renderer';
// process.env.DEBUG = '*';
global.process = process;
global.Buffer = require('buffer').Buffer;
const url = require('whatwg-url');
global.URL = url.URL;
global.URLSearchParams = url.URLSearchParams;

// need this to fix up a circular reference between util and inherits.
require('util');

const oldToString = Object.prototype.toString;
Object.prototype.toString = function() {
    if (this === process)
        return '[object process]';
    return oldToString.apply(this);
}

global.location = {
    protocol: 'https:'
}

return require;
})