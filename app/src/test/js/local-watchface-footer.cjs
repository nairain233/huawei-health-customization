// 在独立离屏 iframe 中运行生产脚本与已核对的宿主菜单样式，不操作真实表盘页面。
const fs = require('node:fs');
const path = require('node:path');

async function verifyFooter(source) {
    const frame = document.createElement('iframe');
    frame.style.cssText = 'position:fixed;left:-10000px;width:360px;height:800px;border:0;';
    document.body.append(frame);
    const doc = frame.contentDocument;
    const styles = `
        :root{--colorBackground:#fff;--colorButtonNormal:#eee;--colorSubheaderDivider:#eee;--colorSecondary:#777;color:rgb(20,20,20);font:16px sans-serif}
        :root[app-theme=dark]{--colorBackground:#202020;--colorButtonNormal:#333;--colorSubheaderDivider:#444;--colorSecondary:#aaa;color:rgb(250,250,250)}
        body{margin:0}.tm-menu-list{position:relative;display:flex;flex-wrap:wrap}
        .tm-menu-list .tm-menu-row{padding:0 16px;display:flex;height:56px;outline:none;background-color:var(--colorBackground);position:relative;box-sizing:border-box;width:100%}
        .tm-menu-list .tm-menu-row.active{background-color:var(--colorButtonNormal)}
        @media(min-width:520px){.tm-menu-list .tm-menu-row{width:50%}}
        .tm-menu-list .row-icon{font-size:24px;margin-right:16px;display:flex;justify-content:center;align-items:center;flex-shrink:0;position:relative}
        .tm-lang-mirror .tm-menu-list .row-icon{margin-left:16px;margin-right:0}
        .tm-menu-list .row-content{flex:1;display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid var(--colorSubheaderDivider)}
        .tm-menu-list .row-content>.right{display:flex;align-items:center}
        .tm-menu-list .row-content>.right>.icon{color:var(--colorSecondary);font-size:24px;width:.5em}
        .tm-menu-list .row-content .label{position:relative;white-space:nowrap}
    `;
    doc.head.innerHTML = '<style>' + styles + '</style>';
    doc.body.innerHTML = '<div class="home-tabs"><div class="tab-nav active" tabid="MySpace">我的</div><div class="tm-panel active"><div class="my-space"><div class="tm-menu-list"><div class="tm-menu-row device"><span class="row-content"><span class="label">设备</span></span></div><div class="tm-menu-row personalize"><span class="row-content"><span class="label">设置</span></span></div></div><div class="section local-dials"><div class="sec-body"></div></div></div></div></div>';
    const space = doc.querySelector('.my-space');
    let menu = space.querySelector('.tm-menu-list');
    let device = menu.querySelector('.device');
    let setting = menu.querySelector('.personalize');
    const section = space.querySelector('.local-dials');
    const handlers = new Map();
    const win = {addEventListener(type, fn) { handlers.set(type, fn); }, removeEventListener(type) { handlers.delete(type); }};
    win.top = win;
    const location = {origin: 'https://h5hosting-drcn.dbankcdn.cn', pathname: '/cch5/health/watchFace/index.html', hash: '#/index'};
    const config = {title: '导入本地表盘', message: 'Ready', enabled: true, token: 'fixture'};
    const run = () => Function('window', 'document', 'location', 'getComputedStyle', 'MutationObserver', 'requestAnimationFrame',
        'return ' + source + '(' + JSON.stringify(config) + ')')(win, doc, location, frame.contentWindow.getComputedStyle.bind(frame.contentWindow), MutationObserver, callback => setTimeout(callback, 0));
    const settle = () => new Promise(resolve => setTimeout(resolve, 40));
    const entry = () => doc.getElementById('huawei-hook-local-face');
    const style = node => frame.contentWindow.getComputedStyle(node);
    const placed = () => entry() && device.nextElementSibling === entry() && entry().nextElementSibling === setting;
    const passed = [];
    const check = (condition, label) => { if (!condition) throw new Error(label); passed.push(label); };
    const rejectAndRestore = async (change, restore, label) => {
        change(); await settle(); check(!entry(), label);
        restore(); await settle(); check(placed(), label + ' restores');
    };
    try {
        run(); await settle();
        check(placed(), 'empty-list entry is between device and settings');
        check(entry().tagName === 'BUTTON' && entry().type === 'button' && entry().tabIndex === 0 &&
            entry().textContent === config.title && !entry().querySelector('[role=status]'), 'native keyboard button shows only title');
        check(entry().querySelectorAll('svg[aria-hidden=true][focusable=false]').length === 2, 'decorative icons excluded from accessible name');
        check(style(entry()).height === style(setting).height && style(entry()).padding === style(setting).padding &&
            style(entry()).backgroundColor === style(setting).backgroundColor && style(entry()).borderTopWidth === '0px', 'row matches host height padding background and border reset');
        const lightColor = style(entry()).color;
        check(lightColor === style(setting.querySelector('.label')).color, 'light theme inherits host text');
        doc.documentElement.setAttribute('app-theme', 'dark'); await settle();
        check(style(entry()).color !== lightColor && style(entry()).color === style(setting.querySelector('.label')).color &&
            style(entry()).backgroundColor === style(setting).backgroundColor, 'dark theme updates text and background');
        entry().focus(); await settle(); check(entry().classList.contains('active'), 'keyboard focus has visible host highlight');
        entry().blur(); await settle();
        for (let i = 0; i < 50; i++) section.querySelector('.sec-body').append(doc.createElement('div'));
        await settle(); run(); run(); await settle();
        check(doc.querySelectorAll('#huawei-hook-local-face').length === 1 && placed(), 'long-list and repeated mounting stay unique');
        section.remove(); await settle(); check(placed(), 'missing local-dials still shows entry');
        config.message = 'Error 100007'; config.enabled = false; run(); await settle();
        check(entry().disabled && !entry().textContent.includes('100007') && !entry().classList.contains('active'), 'disabled error state does not add status text');
        config.enabled = true; config.title = 'Import local watch face'; run(); await settle();
        check(!entry().disabled && entry().textContent === config.title, 'config updates reenable entry and refresh title');
        entry().click(); entry().dispatchEvent(new MouseEvent('click', {bubbles:true}));
        check(!location.href, 'synthetic clicks cannot launch picker');
        let deviceClicks = 0, settingClicks = 0;
        device.addEventListener('click', () => deviceClicks++);
        setting.addEventListener('click', () => settingClicks++);
        device.click(); setting.click();
        check(deviceClicks === 1 && settingClicks === 1 && !location.href, 'host device and settings actions remain independent');
        const replacement = menu.cloneNode(true);
        menu.replaceWith(replacement); menu = replacement;
        device = menu.querySelector('.device'); setting = menu.querySelector('.personalize');
        await settle(); check(placed() && doc.querySelectorAll('#huawei-hook-local-face').length === 1, 'menu clone discards stale button and remounts one entry');
        entry().after(entry().cloneNode(true)); await settle();
        check(placed() && doc.querySelectorAll('#huawei-hook-local-face').length === 1, 'duplicate cloned entry is removed');
        for (const tab of ['Recommended', 'Official', 'Vip']) {
            const nav = doc.querySelector('.tab-nav');
            await rejectAndRestore(() => nav.setAttribute('tabid', tab), () => nav.setAttribute('tabid', 'MySpace'), tab + ' hides entry');
        }
        location.hash = '#/detail'; handlers.get('hashchange')(); await settle(); check(!entry(), 'detail route hides entry');
        location.hash = '#/index?fixture=1'; handlers.get('hashchange')(); await settle(); check(placed(), 'index route with query restores entry');
        for (const [node, name] of [[device, 'device'], [setting, 'settings'], [menu, 'menu'], [space, 'space']]) {
            await rejectAndRestore(() => { node.style.display = 'none'; }, () => { node.style.display = ''; }, 'hidden ' + name + ' removes entry');
        }
        await rejectAndRestore(() => { setting.style.visibility = 'hidden'; }, () => { setting.style.visibility = ''; }, 'invisible settings removes entry');
        await rejectAndRestore(() => device.setAttribute('hidden', ''), () => device.removeAttribute('hidden'), 'hidden attribute removes entry');
        await rejectAndRestore(() => space.setAttribute('aria-hidden', 'true'), () => space.removeAttribute('aria-hidden'), 'aria-hidden space removes entry');
        for (const [node, name] of [[device, 'device'], [setting, 'settings'], [menu, 'menu'], [space, 'space'], [doc.querySelector('.tab-nav'), 'nav']]) {
            const duplicate = node.cloneNode(true);
            duplicate.querySelector('#huawei-hook-local-face')?.remove();
            await rejectAndRestore(() => node.after(duplicate), () => duplicate.remove(), 'duplicate ' + name + ' removes entry');
        }
        await rejectAndRestore(() => device.remove(), () => menu.prepend(device), 'missing device removes entry');
        await rejectAndRestore(() => setting.remove(), () => menu.append(setting), 'missing settings removes entry');
        const other = doc.createElement('div'); other.className = 'tm-menu-row';
        await rejectAndRestore(() => setting.before(other), () => other.remove(), 'unexpected row between anchors removes entry');
        await rejectAndRestore(() => menu.prepend(setting), () => device.after(setting), 'reversed anchors remove entry');
        await rejectAndRestore(() => space.classList.remove('my-space'), () => space.classList.add('my-space'), 'missing page container removes entry');
        await rejectAndRestore(() => menu.remove(), () => space.prepend(menu), 'missing menu removes entry');
        doc.documentElement.dir = 'rtl'; doc.documentElement.classList.add('tm-lang-mirror'); await settle();
        check(entry().querySelector('.right svg').style.transform === 'scaleX(-1)' && style(entry().querySelector('.row-icon')).marginLeft === '16px', 'RTL mirrors arrow and icon spacing');
        doc.documentElement.dir = 'ltr'; doc.documentElement.classList.remove('tm-lang-mirror'); await settle();
        check(entry().querySelector('.right svg').style.transform === '', 'LTR restores arrow');
        frame.style.width = '800px'; handlers.get('resize')(); await settle();
        check(placed() && Math.abs(entry().getBoundingClientRect().width - menu.getBoundingClientRect().width / 2) < 1, 'wide screen follows host two-column layout and DOM order');
        entry().after(entry().cloneNode(true));
        run(); win.__huaweiHookLocalFace.dispose(); await settle();
        check(!win.__huaweiHookLocalFace && !entry() && handlers.size === 0, 'dispose cancels queued rendering and unregisters listeners');
        for (const change of [() => { location.origin = 'https://example.com'; }, () => { location.pathname = '/other'; }, () => { win.top = {}; }]) {
            location.origin = 'https://h5hosting-drcn.dbankcdn.cn'; location.pathname = '/cch5/health/watchFace/index.html'; win.top = win;
            change(); check(run() === false && !entry() && !win.__huaweiHookLocalFace, 'untrusted page or frame rejected before mounting');
        }
        return passed;
    } finally { win.__huaweiHookLocalFace?.dispose(); frame.remove(); }
}

(async () => {
    const base = process.argv[2] || 'http://127.0.0.1:9223';
    const tabs = await (await fetch(base + '/json')).json();
    const tab = tabs.find(item => item.type === 'page' && item.webSocketDebuggerUrl);
    if (!tab) throw new Error('No browser page available');
    const socket = new WebSocket(tab.webSocketDebuggerUrl);
    const source = fs.readFileSync(path.join(__dirname, '../../main/assets/local-watchface-footer.js'), 'utf8');
    const timer = setTimeout(() => { console.error('Browser fixture timed out'); socket.close(); process.exitCode = 1; }, 15000);
    const evaluate = () => socket.send(JSON.stringify({id: 1, method: 'Runtime.evaluate', params: {
        expression: '(' + verifyFooter.toString() + ')(' + JSON.stringify(source) + ')', awaitPromise: true, returnByValue: true,
    }}));
    // 无界面浏览器默认不派发真实 focus/blur；启用焦点模拟后再验证原生按钮的焦点事件。
    socket.onopen = () => socket.send(JSON.stringify({id: 0, method: 'Emulation.setFocusEmulationEnabled', params: {enabled:true}}));
    socket.onmessage = event => {
        const message = JSON.parse(event.data);
        if (message.id === 0 && !message.error) { evaluate(); return; }
        if (message.id === 0) { clearTimeout(timer); socket.close(); console.error(JSON.stringify(message)); process.exitCode = 1; return; }
        if (message.id !== 1) return;
        clearTimeout(timer); socket.close();
        if (message.error || message.result.exceptionDetails) {
            console.error(JSON.stringify(message)); process.exitCode = 1;
        } else console.log(JSON.stringify({passed: message.result.result.value}, null, 2));
    };
})().catch(error => { console.error(error.message); process.exitCode = 1; });
