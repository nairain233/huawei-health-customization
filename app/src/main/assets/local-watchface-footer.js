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
    let footer = null;
    let button = null;
    let status = null;
    function remove() { if (footer) footer.remove(); footer = button = status = null; }
    function render() {
        scheduled = false;
        if (disposed) return;
        observer.disconnect();
        try {
            const nav = document.querySelector('.home-tabs .tab-nav.active[tabid="MySpace"]');
            const spaces = document.querySelectorAll('.home-tabs .tm-panel.active > .my-space');
            const space = spaces.length === 1 ? spaces[0] : null;
            const sections = space ? space.querySelectorAll(':scope > .section.local-dials') : [];
            const section = sections.length === 1 ? sections[0] : null;
            const visible = location.hash.split('?')[0] === '#/index' && nav && section && section.getClientRects().length > 0;
            if (!visible) { remove(); return; }
            if (!footer || !footer.isConnected) {
                document.getElementById(id)?.remove();
                footer = document.createElement('section');
                footer.id = id;
                footer.style.cssText = 'box-sizing:border-box;clear:both;margin:16px 16px 0;padding:16px 0 calc(16px + env(safe-area-inset-bottom));color:inherit;';
                button = document.createElement('button');
                button.type = 'button';
                button.style.cssText = 'width:100%;min-height:48px;border:0;border-radius:24px;padding:12px 20px;font:inherit;font-size:16px;background:#007dff;color:#fff;';
                button.addEventListener('click', function (event) {
                    if (!event.isTrusted || !current.enabled || !footer.isConnected) return;
                    location.href = 'huawei-local-watchface://choose?token=' + encodeURIComponent(current.token);
                });
                status = document.createElement('p');
                status.setAttribute('role', 'status');
                status.setAttribute('aria-live', 'polite');
                status.style.cssText = 'font-size:14px;line-height:1.5;margin:12px 0 0;white-space:pre-wrap;overflow-wrap:anywhere;';
                footer.append(button, status);
            }
            if (section.nextElementSibling !== footer) section.after(footer);
            const title = section.querySelector('.sec-head .title');
            footer.style.color = getComputedStyle(title || space).color;
            if (button.textContent !== current.title) button.textContent = current.title;
            if (status.textContent !== current.message) status.textContent = current.message;
            button.disabled = !current.enabled;
            button.style.opacity = current.enabled ? '1' : '0.5';
        } finally {
            if (!disposed) observer.observe(document.documentElement, {subtree:true, childList:true, attributes:true, attributeFilter:['class','style']});
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
