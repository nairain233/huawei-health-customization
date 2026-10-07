// 在浏览器的离屏文档中运行生产脚本，不操作真实表盘列表。可传入本机 CDP HTTP 端口。
const fs = require('node:fs');
const path = require('node:path');

async function verifyFooter(source) {
    const doc = document.implementation.createHTMLDocument('fixture');
    doc.body.innerHTML = '<div class="home-tabs"><div class="tab-nav active" tabid="MySpace"></div><div class="tm-panel active"><div class="my-space"><div class="section local-dials"><div class="sec-head"><div class="title">本地表盘</div></div><div class="sec-body"></div></div></div></div></div>';
    const section = doc.querySelector('.local-dials');
    section.getClientRects = () => [{width: 300, height: 200}];
    let color = 'rgb(250, 250, 250)';
    const win = {addEventListener() {}, removeEventListener() {}};
    win.top = win;
    const location = {origin: 'https://h5hosting-drcn.dbankcdn.cn', pathname: '/cch5/health/watchFace/index.html', hash: '#/index'};
    const config = {title: '导入本地表盘', message: 'Ready', enabled: true, token: 'fixture'};
    const run = () => Function('window', 'document', 'location', 'getComputedStyle', 'MutationObserver', 'requestAnimationFrame',
        'return ' + source + '(' + JSON.stringify(config) + ')')(win, doc, location, () => ({color}), MutationObserver, callback => setTimeout(callback, 0));
    const settle = () => new Promise(resolve => setTimeout(resolve, 40));
    const footer = () => doc.getElementById('huawei-hook-local-face');
    const check = (condition, label) => { if (!condition) throw new Error(label); passed.push(label); };
    const passed = [];
    try {
        run(); await settle();
        check(section.nextElementSibling === footer(), 'empty-list footer follows local section');
        for (let i = 0; i < 50; i++) section.querySelector('.sec-body').append(doc.createElement('div'));
        await settle(); run(); run(); await settle();
        check(doc.querySelectorAll('#huawei-hook-local-face').length === 1 && section.nextElementSibling === footer(), 'long-list and repeated mounting stay unique');
        check(footer().style.color === color, 'dark theme inherits readable color');
        color = 'rgb(20, 20, 20)'; config.message = 'Error 100007'; config.enabled = false; run(); await settle();
        check(footer().style.color === color && footer().querySelector('button').disabled && footer().textContent.includes('100007'), 'light theme and disabled error status');
        for (const tab of ['Recommended', 'Official', 'Vip']) {
            doc.querySelector('.tab-nav').classList.remove('active');
            await settle(); check(!footer(), tab + ' hides footer');
            doc.querySelector('.tab-nav').classList.add('active');
            await settle(); check(!!footer(), tab + ' return restores footer');
        }
        location.hash = '#/detail'; run(); await settle(); check(!footer(), 'detail route hides footer');
        location.hash = '#/index'; run(); await settle();
        config.enabled = true; run(); await settle(); footer().querySelector('button').click();
        check(!location.href, 'synthetic click cannot launch picker');
        section.remove(); await settle(); check(!footer(), 'missing anchor removes footer');
        doc.querySelector('.my-space').append(section);
        run();
        win.__huaweiHookLocalFace.dispose();
        await settle();
        check(!win.__huaweiHookLocalFace && !footer(), 'dispose cancels queued rendering and removes observer state');
        return passed;
    } finally { win.__huaweiHookLocalFace?.dispose(); }
}

(async () => {
    const base = process.argv[2] || 'http://127.0.0.1:9223';
    const tabs = await (await fetch(base + '/json')).json();
    const tab = tabs.find(item => item.type === 'page' && item.webSocketDebuggerUrl);
    if (!tab) throw new Error('No browser page available');
    const socket = new WebSocket(tab.webSocketDebuggerUrl);
    const source = fs.readFileSync(path.join(__dirname, '../../main/assets/local-watchface-footer.js'), 'utf8');
    const timer = setTimeout(() => { socket.close(); process.exitCode = 1; }, 10000);
    socket.onopen = () => socket.send(JSON.stringify({id: 1, method: 'Runtime.evaluate', params: {
        expression: '(' + verifyFooter.toString() + ')(' + JSON.stringify(source) + ')', awaitPromise: true, returnByValue: true,
    }}));
    socket.onmessage = event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer); socket.close();
        if (message.error || message.result.exceptionDetails) {
            console.error(JSON.stringify(message)); process.exitCode = 1;
        } else console.log(JSON.stringify({passed: message.result.result.value}, null, 2));
    };
})().catch(error => { console.error(error.message); process.exitCode = 1; });
