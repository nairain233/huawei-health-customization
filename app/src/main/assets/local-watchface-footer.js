(function (config) {
    'use strict';
    const key = '__huaweiHookLocalFace';
    const id = 'huawei-hook-local-face';
    if (window.top !== window || location.origin !== 'https://h5hosting-drcn.dbankcdn.cn' ||
        location.pathname !== '/cch5/health/watchFace/index.html') return false;
    if (window[key]) { window[key].update(config); return true; }
    let current = config;
    let scheduled = false;
    let disposed = false;
    let button = null;
    let label = null;
    let arrow = null;
    function remove() {
        if (button) button.remove();
        for (const stale of document.querySelectorAll('#' + id)) stale.remove();
        button = label = arrow = null;
    }
    function visible(element) {
        if (!element || element.closest('[hidden], [aria-hidden="true"]') || !element.getClientRects().length) return false;
        const style = getComputedStyle(element);
        return style.display !== 'none' && style.visibility !== 'hidden' && style.visibility !== 'collapse';
    }
    function icon(path, width) {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 ' + width + ' 24');
        svg.setAttribute('aria-hidden', 'true');
        svg.setAttribute('focusable', 'false');
        svg.setAttribute('fill', 'none');
        svg.setAttribute('stroke', 'currentColor');
        svg.setAttribute('stroke-width', '1.5');
        svg.style.cssText = 'width:' + (width === 24 ? '1em' : '0.5em') + ';height:1em;';
        svg.classList.add('icon');
        const shape = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        shape.setAttribute('d', path);
        svg.append(shape);
        return svg;
    }
    function render() {
        scheduled = false;
        if (disposed) return;
        observer.disconnect();
        try {
            // 宿主重绘若克隆了菜单，移除没有本实例监听器的残留入口。
            for (const stale of document.querySelectorAll('#' + id)) { if (stale !== button) stale.remove(); }
            const navs = document.querySelectorAll('.home-tabs .tab-nav.active[tabid="MySpace"]');
            const spaces = document.querySelectorAll('.home-tabs .tm-panel.active > .my-space');
            const space = spaces.length === 1 ? spaces[0] : null;
            // 公开 MySpace 菜单以 device / personalize 为直属行键；只接受唯一且相邻的锚点。
            const menus = space ? space.querySelectorAll(':scope > .tm-menu-list') : [];
            const menu = menus.length === 1 ? menus[0] : null;
            const devices = menu ? menu.querySelectorAll(':scope > .tm-menu-row.device') : [];
            const settings = menu ? menu.querySelectorAll(':scope > .tm-menu-row.personalize') : [];
            const device = devices.length === 1 ? devices[0] : null;
            const setting = settings.length === 1 ? settings[0] : null;
            const next = device && device.nextElementSibling;
            const adjacent = next === setting || (button && next === button && button.nextElementSibling === setting);
            if (location.hash.split('?')[0] !== '#/index' || navs.length !== 1 ||
                navs[0].closest('.home-tabs') !== space?.closest('.home-tabs') ||
                ![navs[0], space, menu, device, setting].every(visible) || !adjacent) { remove(); return; }
            if (!button || !button.isConnected) {
                button = document.createElement('button');
                button.id = id;
                button.type = 'button';
                button.className = 'tm-menu-row';
                button.style.cssText = 'appearance:none;border:0;border-radius:0;margin:0;font:inherit;color:inherit;text-align:start;cursor:pointer;';
                const entry = button;
                button.addEventListener('click', function (event) {
                    if (!event.isTrusted) return;
                    render();
                    if (event.currentTarget !== button || !current.enabled || !button.isConnected) return;
                    location.href = 'huawei-local-watchface://choose?token=' + encodeURIComponent(current.token);
                });
                // 保持宿主行结构，但独立创建内容和监听器，不复制原菜单的业务动作。
                const left = document.createElement('span');
                left.className = 'row-icon';
                left.append(icon('M12 3v12m-5-5 5 5 5-5M4 15v5h16v-5', 24));
                const content = document.createElement('span');
                content.className = 'row-content';
                label = document.createElement('span');
                label.className = 'label';
                const right = document.createElement('span');
                right.className = 'right';
                arrow = icon('m3 6 6 6-6 6', 12);
                right.append(arrow);
                content.append(label, right);
                button.append(left, content);
                button.addEventListener('pointerdown', () => { if (current.enabled && entry.isConnected) entry.classList.add('active'); });
                for (const event of ['pointerup', 'pointercancel', 'pointerleave', 'blur']) {
                    button.addEventListener(event, () => entry.classList.remove('active'));
                }
                button.addEventListener('focus', () => { if (current.enabled && entry.isConnected) entry.classList.add('active'); });
            }
            if (device.nextElementSibling !== button || button.nextElementSibling !== setting) setting.before(button);
            button.style.color = getComputedStyle(setting.querySelector('.label') || setting).color;
            const mirrored = space.closest('.tm-lang-mirror') || getComputedStyle(space).direction === 'rtl';
            arrow.style.transform = mirrored ? 'scaleX(-1)' : '';
            if (label.textContent !== current.title) label.textContent = current.title;
            button.disabled = !current.enabled;
            button.style.opacity = current.enabled ? '1' : '0.5';
            if (button.disabled) button.classList.remove('active');
        } finally {
            if (!disposed) observer.observe(document.documentElement, {subtree:true, childList:true, attributes:true,
                attributeFilter:['class','style','hidden','aria-hidden','dir','lang','app-theme','tabid']});
        }
    }
    function schedule() { if (!scheduled && !disposed) { scheduled = true; requestAnimationFrame(render); } }
    const observer = new MutationObserver(schedule);
    window.addEventListener('hashchange', schedule);
    window.addEventListener('resize', schedule);
    window[key] = {
        update(value) { current = value; schedule(); },
        dispose() { disposed = true; observer.disconnect(); window.removeEventListener('hashchange', schedule); window.removeEventListener('resize', schedule); remove(); delete window[key]; }
    };
    render();
    return true;
})
