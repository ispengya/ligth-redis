(() => {
    'use strict';

    const HISTORY_KEY = 'light-redis-panel.command-history';
    const MAX_HISTORY = 100;
    const MAX_CONSOLE_LINES = 500;
    const DEFAULT_EXPANDED_CHILDREN = 20;
    const SMALL_KEYSET_EXPAND_LIMIT = 50;
    const CONSOLE_HINT = '输入命令后按 Enter 执行，点击上方快捷命令可填入，↑/↓ 查看历史，Ctrl+L 清屏。支持引号：SET k "hello world"。';
    const KEY_MUTATING_COMMANDS = new Set([
        'SET', 'DEL',
        'EXPIRE', 'PEXPIRE', 'EXPIREAT', 'PEXPIREAT', 'PERSIST',
        'LPUSH', 'RPUSH', 'LPOP', 'RPOP', 'LSET', 'LREM',
        'HSET', 'HDEL',
        'SADD', 'SREM',
        'ZADD', 'ZREM'
    ]);

    const state = {
        selectedKey: null,
        selectedValue: null,
        commandHistory: [],
        historyIndex: 0,
        busy: false,
        statusInFlight: false,
        refreshTimer: null,
        redisAddress: '127.0.0.1:6379',
        currentKeys: [],
        expandedPaths: new Set(),
        collapsedPaths: new Set()
    };

    const elements = {
        badge: document.getElementById('connection-badge'),
        connectionText: document.getElementById('connection-text'),
        redisAddress: document.getElementById('redis-address'),
        prompt: document.getElementById('prompt'),
        keyPattern: document.getElementById('key-pattern'),
        keyList: document.getElementById('key-list'),
        keyCount: document.getElementById('key-count'),
        refreshKeys: document.getElementById('refresh-keys'),
        keyDetail: document.getElementById('key-detail'),
        detailName: document.getElementById('detail-name'),
        detailType: document.getElementById('detail-type'),
        detailTtl: document.getElementById('detail-ttl'),
        detailValue: document.getElementById('detail-value'),
        deleteKey: document.getElementById('delete-key'),
        copyValue: document.getElementById('copy-value'),
        consoleOutput: document.getElementById('console-output'),
        commandForm: document.getElementById('command-form'),
        commandInput: document.getElementById('command-input'),
        runCommand: document.getElementById('run-command'),
        clearConsole: document.getElementById('clear-console'),
        consoleTab: document.getElementById('tab-console'),
        detailTab: document.getElementById('tab-detail'),
        consoleView: document.getElementById('console-view'),
        detailView: document.getElementById('detail-view'),
        detailEmpty: document.getElementById('detail-empty'),
        refreshInterval: document.getElementById('refresh-interval'),
        statusUpdated: document.getElementById('status-updated'),
        statusError: document.getElementById('status-error'),
        statUptime: document.getElementById('stat-uptime'),
        statCommands: document.getElementById('stat-commands'),
        statClients: document.getElementById('stat-clients'),
        statKeys: document.getElementById('stat-keys'),
        statExpired: document.getElementById('stat-expired'),
        statMemory: document.getElementById('stat-memory'),
        statThreads: document.getElementById('stat-threads')
    };

    function init() {
        loadHistory();
        bindEvents();
        scheduleStatusRefresh();
        refreshStatus();
        refreshKeys();
        selectKeyFromHash();
        elements.commandInput.focus();
    }

    function selectKeyFromHash() {
        const hash = window.location.hash;
        if (!hash.startsWith('#key=')) {
            return;
        }
        let key;
        try {
            key = decodeURIComponent(hash.substring(5));
        } catch (error) {
            return;
        }
        if (key) {
            selectKey(key);
        }
    }

    function bindEvents() {
        for (const chip of document.querySelectorAll('.quick-chip')) {
            chip.title = '填入命令后按 Enter 执行';
            chip.addEventListener('click', () => {
                const command = chip.dataset.command || '';
                elements.commandInput.value = command;
                elements.commandInput.focus();
                elements.commandInput.setSelectionRange(command.length, command.length);
            });
        }
        elements.consoleTab.addEventListener('click', () => setMainTab('console'));
        elements.detailTab.addEventListener('click', () => setMainTab('detail'));
        elements.commandForm.addEventListener('submit', onSubmit);
        elements.clearConsole.addEventListener('click', clearConsole);
        elements.refreshKeys.addEventListener('click', refreshKeys);
        elements.deleteKey.addEventListener('click', deleteSelectedKey);
        elements.copyValue.addEventListener('click', copySelectedValue);
        elements.refreshInterval.addEventListener('change', scheduleStatusRefresh);
        elements.commandInput.addEventListener('keydown', handleHistoryNavigation);
        elements.keyPattern.addEventListener('keydown', event => {
            if (event.key === 'Enter') {
                event.preventDefault();
                refreshKeys();
            }
        });
        document.addEventListener('keydown', event => {
            if (event.ctrlKey && event.key.toLowerCase() === 'l') {
                event.preventDefault();
                clearConsole();
            }
        });
    }

    function setMainTab(tab) {
        const showConsole = tab === 'console';
        elements.consoleTab.classList.toggle('active', showConsole);
        elements.detailTab.classList.toggle('active', !showConsole);
        elements.consoleTab.setAttribute('aria-selected', String(showConsole));
        elements.detailTab.setAttribute('aria-selected', String(!showConsole));
        elements.consoleView.classList.toggle('hidden', !showConsole);
        elements.detailView.classList.toggle('hidden', showConsole);
        elements.clearConsole.classList.toggle('hidden', !showConsole);
        if (showConsole) {
            try {
                elements.commandInput.focus({ preventScroll: true });
            } catch (error) {
                elements.commandInput.focus();
            }
        }
    }

    async function request(path, options) {
        const response = await fetch(path, options || {});
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            const message = data && data.error ? data.error : 'HTTP ' + response.status;
            throw new Error(message);
        }
        return data || {};
    }

    async function runCommandLine(command) {
        const started = performance.now();
        const response = await fetch('/api/command', {
            method: 'POST',
            headers: {
                'Content-Type': 'text/plain; charset=utf-8',
                'X-Light-Redis-Panel': '1'
            },
            body: command
        });
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            const message = data && data.error ? data.error : 'HTTP ' + response.status;
            throw new Error(message);
        }
        return {
            ok: Boolean(data && data.ok),
            reply: data && data.reply,
            error: data && data.error,
            elapsedMs: data && typeof data.elapsedMs === 'number'
                ? data.elapsedMs
                : performance.now() - started
        };
    }

    async function onSubmit(event) {
        event.preventDefault();
        if (state.busy) {
            return;
        }
        const command = elements.commandInput.value.trim();
        if (!command) {
            return;
        }

        state.busy = true;
        elements.commandInput.disabled = true;
        elements.runCommand.disabled = true;
        appendCommand(command);
        saveHistory(command);

        try {
            const result = await runCommandLine(command);
            appendReply(result);
            if (result.ok) {
                const verb = command.split(/\s+/)[0].toUpperCase();
                if (KEY_MUTATING_COMMANDS.has(verb)) {
                    refreshKeys();
                }
            }
        } catch (error) {
            appendReply({ ok: false, error: error.message, elapsedMs: null });
        } finally {
            state.busy = false;
            elements.commandInput.disabled = false;
            elements.runCommand.disabled = false;
            elements.commandInput.value = '';
            elements.commandInput.focus();
            refreshStatus();
        }
    }

    async function refreshStatus() {
        if (state.statusInFlight) {
            return;
        }
        state.statusInFlight = true;
        try {
            const data = await request('/api/status');
            const status = data.status || {};
            if (data.redisAddress) {
                state.redisAddress = data.redisAddress;
                elements.redisAddress.textContent = data.redisAddress;
                elements.prompt.textContent = data.redisAddress;
            }
            setConnection(true, '已连接');
            updateStatus(status);
            const latency = typeof data.latencyMs === 'number' ? data.latencyMs : null;
            elements.statusUpdated.textContent = '更新于 ' + formatTime(new Date())
                + (latency == null ? '' : ' · ' + latency.toFixed(1) + ' ms');
            elements.statusError.classList.add('hidden');
            elements.statusError.textContent = '';
        } catch (error) {
            setConnection(false, '未连接');
            elements.statusError.textContent = error.message || '连接失败';
            elements.statusError.classList.remove('hidden');
            elements.statusUpdated.textContent = '更新失败';
        } finally {
            state.statusInFlight = false;
        }
    }

    async function refreshKeys() {
        const pattern = elements.keyPattern.value.trim() || '*';
        elements.refreshKeys.disabled = true;
        try {
            const data = await request('/api/keys?pattern=' + encodeURIComponent(pattern));
            renderKeys(Array.isArray(data.keys) ? data.keys : []);
        } catch (error) {
            elements.keyCount.textContent = '—';
            renderKeyMessage('读取 key 失败：' + (error.message || 'unknown error'), true);
        } finally {
            elements.refreshKeys.disabled = false;
        }
    }

    function renderKeys(keys) {
        state.currentKeys = keys;
        elements.keyCount.textContent = keys.length + ' 个 key';

        if (keys.length === 0) {
            elements.keyList.textContent = '';
            const empty = document.createElement('p');
            empty.className = 'empty';
            empty.textContent = '没有匹配的 key';
            elements.keyList.appendChild(empty);
            clearKeyDetail();
            return;
        }

        if (state.selectedKey) {
            expandAncestors(state.selectedKey);
        }
        renderTree();

        if (!state.selectedKey || !keys.includes(state.selectedKey)) {
            clearKeyDetail();
        }
    }

    function buildKeyTree(keys) {
        const root = createTreeNode('', '');
        for (const key of keys) {
            const segments = key.split(':');
            let node = root;
            node.keyCount++;
            let path = '';
            for (let i = 0; i < segments.length; i++) {
                const segment = segments[i];
                path = i === 0 ? segment : path + ':' + segment;
                let child = node.children.get(segment);
                if (!child) {
                    child = createTreeNode(segment, path);
                    node.children.set(segment, child);
                }
                child.keyCount++;
                node = child;
            }
            node.key = key;
        }
        return root;
    }

    function createTreeNode(label, path) {
        return {
            label,
            path,
            key: null,
            keyCount: 0,
            children: new Map()
        };
    }

    function sortedChildren(node) {
        return Array.from(node.children.values()).sort((left, right) => {
            const leftFolder = left.children.size > 0;
            const rightFolder = right.children.size > 0;
            if (leftFolder !== rightFolder) {
                return leftFolder ? -1 : 1;
            }
            return left.label.localeCompare(right.label);
        });
    }

    function isExpanded(node) {
        if (node.children.size === 0) {
            return false;
        }
        if (state.collapsedPaths.has(node.path)) {
            return false;
        }
        if (state.expandedPaths.has(node.path)) {
            return true;
        }
        if (state.currentKeys.length <= SMALL_KEYSET_EXPAND_LIMIT) {
            return true;
        }
        return node.children.size <= DEFAULT_EXPANDED_CHILDREN;
    }

    function toggleNode(node) {
        if (isExpanded(node)) {
            state.collapsedPaths.add(node.path);
            state.expandedPaths.delete(node.path);
        } else {
            state.expandedPaths.add(node.path);
            state.collapsedPaths.delete(node.path);
        }
        renderTree();
    }

    function expandAncestors(key) {
        if (!key) {
            return false;
        }
        const segments = key.split(':');
        let path = '';
        let changed = false;
        for (let i = 0; i < segments.length - 1; i++) {
            path = i === 0 ? segments[i] : path + ':' + segments[i];
            if (!state.expandedPaths.has(path) || state.collapsedPaths.has(path)) {
                state.expandedPaths.add(path);
                state.collapsedPaths.delete(path);
                changed = true;
            }
        }
        return changed;
    }

    function renderTree() {
        const scrollTop = elements.keyList.scrollTop;
        elements.keyList.textContent = '';
        if (state.currentKeys.length === 0) {
            return;
        }
        const root = buildKeyTree(state.currentKeys);
        renderNodeChildren(elements.keyList, root);
        elements.keyList.scrollTop = scrollTop;
        highlightSelectedKey();
    }

    function renderNodeChildren(container, node) {
        for (const child of sortedChildren(node)) {
            const hasChildren = child.children.size > 0;
            const expanded = hasChildren && isExpanded(child);
            const row = document.createElement('div');
            row.className = 'key-row';
            row.dataset.path = child.path;
            row.setAttribute('role', 'treeitem');
            if (hasChildren) {
                row.setAttribute('aria-expanded', String(expanded));
            }
            if (child.key) {
                row.dataset.key = child.key;
                row.setAttribute('aria-selected', String(child.key === state.selectedKey));
            }

            if (hasChildren) {
                const toggle = document.createElement('button');
                toggle.type = 'button';
                toggle.className = 'key-toggle';
                toggle.textContent = expanded ? '▾' : '▸';
                toggle.title = expanded ? '折叠' : '展开';
                toggle.setAttribute('aria-label', (expanded ? '折叠 ' : '展开 ') + child.path);
                toggle.setAttribute('aria-expanded', String(expanded));
                toggle.addEventListener('click', event => {
                    event.stopPropagation();
                    toggleNode(child);
                });
                row.appendChild(toggle);
            } else {
                const spacer = document.createElement('span');
                spacer.className = 'key-toggle-placeholder';
                spacer.setAttribute('aria-hidden', 'true');
                row.appendChild(spacer);
            }

            const label = document.createElement('button');
            label.type = 'button';
            label.className = 'key-label mono' + (hasChildren ? ' key-folder-label' : '');
            label.textContent = child.label || '(空)';
            label.title = child.key || child.path;
            if (child.key) {
                label.addEventListener('click', () => selectKey(child.key));
                if (child.key === state.selectedKey) {
                    row.classList.add('active');
                }
            } else {
                label.setAttribute('aria-label', (expanded ? '折叠 ' : '展开 ') + child.path);
                label.addEventListener('click', () => toggleNode(child));
            }
            row.appendChild(label);

            if (hasChildren) {
                const count = document.createElement('span');
                count.className = 'key-count-hint';
                count.textContent = child.keyCount;
                count.title = child.keyCount + ' 个 key';
                row.appendChild(count);
            }

            container.appendChild(row);

            if (hasChildren && expanded) {
                const group = document.createElement('div');
                group.className = 'key-children';
                group.setAttribute('role', 'group');
                renderNodeChildren(group, child);
                container.appendChild(group);
            }
        }
    }

    function renderKeyMessage(message, isError) {
        elements.keyList.textContent = '';
        const item = document.createElement('p');
        item.className = 'empty';
        item.textContent = message;
        elements.keyList.appendChild(item);
        if (isError) {
            appendSystem(message, true);
        }
    }

    async function selectKey(name) {
        state.selectedKey = name;
        const expanded = expandAncestors(name);
        if (expanded && state.currentKeys.length > 0) {
            renderTree();
        } else {
            highlightSelectedKey();
        }
        updateKeyHash(name);
        try {
            const data = await request('/api/key?name=' + encodeURIComponent(name));
            elements.detailName.textContent = data.key || name;
            elements.detailType.textContent = data.type || 'unknown';
            elements.detailType.dataset.type = data.type || 'unknown';
            renderTtl(data.ttl);
            state.selectedValue = data.value === null || data.value === undefined
                ? null
                : data.value;
            elements.copyValue.disabled = state.selectedValue === null;
            elements.copyValue.textContent = '复制';
            if (data.value === null || data.value === undefined) {
                elements.detailValue.textContent = '(nil)';
            } else if (data.value === '') {
                elements.detailValue.textContent = '(empty)';
            } else {
                elements.detailValue.textContent = data.value;
            }
            elements.keyDetail.classList.remove('hidden');
            elements.detailEmpty.classList.add('hidden');
            elements.deleteKey.disabled = false;
            setMainTab('detail');
        } catch (error) {
            clearKeyDetail();
            appendSystem('读取 key 失败：' + (error.message || 'unknown error'), true);
        }
    }

    async function deleteSelectedKey() {
        const name = state.selectedKey;
        if (!name) {
            return;
        }
        if (!window.confirm('确定删除 key "' + name + '" ？')) {
            return;
        }

        elements.deleteKey.disabled = true;
        try {
            const data = await request('/api/key?name=' + encodeURIComponent(name), {
                method: 'DELETE',
                headers: { 'X-Light-Redis-Panel': '1' }
            });
            appendSystem('已删除 ' + name + '（' + (data.deleted || 0) + ' 个）', false);
            clearKeyDetail();
            await refreshKeys();
            await refreshStatus();
        } catch (error) {
            appendSystem('删除失败：' + (error.message || 'unknown error'), true);
            elements.deleteKey.disabled = false;
        }
    }

    function highlightSelectedKey() {
        for (const row of elements.keyList.querySelectorAll('.key-row')) {
            const active = Boolean(row.dataset.key) && row.dataset.key === state.selectedKey;
            row.classList.toggle('active', active);
            if (row.dataset.key) {
                row.setAttribute('aria-selected', String(active));
            }
        }
    }

    function updateKeyHash(name) {
        if (!window.history || !window.history.replaceState) {
            return;
        }
        window.history.replaceState(null, '', '#key=' + encodeURIComponent(name));
    }

    function clearKeyDetail() {
        state.selectedKey = null;
        state.selectedValue = null;
        if (window.history && window.history.replaceState) {
            window.history.replaceState(null, '', window.location.pathname + window.location.search);
        }
        elements.keyDetail.classList.add('hidden');
        elements.detailEmpty.classList.remove('hidden');
        elements.detailName.textContent = '';
        elements.detailType.textContent = '';
        elements.detailType.dataset.type = '';
        elements.detailTtl.textContent = '';
        elements.detailTtl.dataset.state = '';
        elements.detailValue.textContent = '';
        elements.copyValue.disabled = true;
        elements.copyValue.textContent = '复制';
        elements.deleteKey.disabled = false;
        highlightSelectedKey();
    }

    function renderTtl(ttl) {
        if (ttl === null || ttl === undefined) {
            elements.detailTtl.textContent = '—';
            elements.detailTtl.dataset.state = '';
            return;
        }
        if (ttl === -2) {
            elements.detailTtl.textContent = '已过期';
            elements.detailTtl.dataset.state = 'expired';
            return;
        }
        if (ttl === -1) {
            elements.detailTtl.textContent = '永不过期';
            elements.detailTtl.dataset.state = 'persistent';
            return;
        }
        elements.detailTtl.textContent = '剩余 ' + formatDuration(ttl);
        elements.detailTtl.dataset.state = ttl <= 60000 ? 'expiring' : 'active';
    }

    function formatDuration(milliseconds) {
        const value = Math.max(0, Number(milliseconds) || 0);
        if (value >= 3600000) {
            return (value / 3600000).toFixed(1) + ' 小时';
        }
        if (value >= 60000) {
            return (value / 60000).toFixed(1) + ' 分钟';
        }
        if (value >= 1000) {
            return Math.round(value / 1000) + ' 秒';
        }
        return Math.round(value) + ' 毫秒';
    }

    function setConnection(connected, text) {
        elements.badge.classList.remove('badge-connected', 'badge-disconnected', 'badge-unknown');
        elements.badge.classList.add(connected ? 'badge-connected' : 'badge-disconnected');
        elements.connectionText.textContent = text;
    }

    function updateStatus(status) {
        elements.statUptime.textContent = status.uptimeInSeconds == null
            ? '—'
            : formatUptime(status.uptimeInSeconds);
        elements.statCommands.textContent = formatNumber(status.totalCommands);
        elements.statClients.textContent = formatNumber(status.connectedClients);
        elements.statKeys.textContent = formatNumber(status.keys);
        elements.statExpired.textContent = formatNumber(status.expiredKeys);
        elements.statMemory.textContent = status.jvmUsedMemoryMb == null
            ? '—'
            : Number(status.jvmUsedMemoryMb).toFixed(1) + ' MB';
        elements.statThreads.textContent = formatNumber(status.jvmThreads);
    }

    function formatNumber(value) {
        return value == null ? '—' : Number(value).toLocaleString('zh-CN');
    }

    function formatUptime(totalSeconds) {
        const seconds = Math.max(0, Math.floor(Number(totalSeconds) || 0));
        const days = Math.floor(seconds / 86400);
        const hours = Math.floor((seconds % 86400) / 3600);
        const minutes = Math.floor((seconds % 3600) / 60);
        const remain = seconds % 60;

        if (days > 0) {
            return days + ' 天 ' + hours + ' 时';
        }
        if (hours > 0) {
            return hours + ' 时 ' + minutes + ' 分';
        }
        if (minutes > 0) {
            return minutes + ' 分 ' + remain + ' 秒';
        }
        return remain + ' 秒';
    }

    function formatTime(date) {
        return date.toLocaleTimeString('zh-CN', { hour12: false });
    }

    function appendCommand(command) {
        const line = document.createElement('div');
        line.className = 'console-line console-command-line';

        const chevron = document.createElement('span');
        chevron.className = 'console-chevron';
        chevron.textContent = '❯';

        const address = document.createElement('span');
        address.className = 'console-address';
        address.textContent = state.redisAddress;

        const text = document.createElement('span');
        text.className = 'console-command';
        text.textContent = command;

        line.appendChild(chevron);
        line.appendChild(address);
        line.appendChild(text);
        appendConsoleLine(line);
    }

    function appendReply(result) {
        const line = document.createElement('div');
        line.className = 'console-line';

        const reply = document.createElement('span');
        reply.className = 'console-reply mono' + (result.ok ? '' : ' error');
        if (result.ok) {
            reply.textContent = result.reply == null || result.reply === ''
                ? '(empty)'
                : result.reply;
        } else {
            reply.textContent = result.error || 'ERROR';
        }
        line.appendChild(reply);

        if (typeof result.elapsedMs === 'number') {
            const timing = document.createElement('span');
            timing.className = 'console-timing';
            timing.textContent = ' (' + Number(result.elapsedMs).toFixed(2) + ' ms)';
            line.appendChild(timing);
        }

        appendConsoleLine(line);
    }

    function appendSystem(message, isError) {
        const line = document.createElement('div');
        line.className = 'console-line system' + (isError ? ' error' : '');
        line.textContent = message;
        appendConsoleLine(line);
    }

    function appendConsoleLine(line) {
        elements.consoleOutput.appendChild(line);
        while (elements.consoleOutput.childElementCount > MAX_CONSOLE_LINES) {
            elements.consoleOutput.removeChild(elements.consoleOutput.firstElementChild);
        }
        elements.consoleOutput.scrollTop = elements.consoleOutput.scrollHeight;
    }

    function clearConsole() {
        setMainTab('console');
        elements.consoleOutput.textContent = '';
        const hint = document.createElement('div');
        hint.className = 'console-line console-hint';
        hint.textContent = CONSOLE_HINT;
        elements.consoleOutput.appendChild(hint);
        elements.commandInput.focus();
    }

    function scheduleStatusRefresh() {
        if (state.refreshTimer) {
            clearInterval(state.refreshTimer);
            state.refreshTimer = null;
        }
        const interval = Number(elements.refreshInterval.value);
        if (interval > 0) {
            state.refreshTimer = setInterval(refreshStatus, interval);
        }
    }

    function loadHistory() {
        try {
            const raw = localStorage.getItem(HISTORY_KEY);
            const parsed = raw ? JSON.parse(raw) : [];
            if (Array.isArray(parsed)) {
                state.commandHistory = parsed
                    .filter(item => typeof item === 'string')
                    .slice(-MAX_HISTORY);
            }
        } catch (error) {
            state.commandHistory = [];
        }
        state.historyIndex = state.commandHistory.length;
    }

    function saveHistory(command) {
        const history = state.commandHistory.filter(item => item !== command);
        history.push(command);
        state.commandHistory = history.slice(-MAX_HISTORY);
        state.historyIndex = state.commandHistory.length;
        try {
            localStorage.setItem(HISTORY_KEY, JSON.stringify(state.commandHistory));
        } catch (error) {
            // localStorage 不可用时忽略
        }
    }

    function handleHistoryNavigation(event) {
        if (event.key === 'ArrowUp') {
            if (state.commandHistory.length === 0) {
                return;
            }
            event.preventDefault();
            state.historyIndex = Math.max(0, state.historyIndex - 1);
            setCommandInput(state.commandHistory[state.historyIndex] || '');
        } else if (event.key === 'ArrowDown') {
            if (state.commandHistory.length === 0) {
                return;
            }
            event.preventDefault();
            state.historyIndex = Math.min(state.commandHistory.length, state.historyIndex + 1);
            setCommandInput(state.historyIndex >= state.commandHistory.length
                ? ''
                : state.commandHistory[state.historyIndex]);
        }
    }

    function setCommandInput(value) {
        elements.commandInput.value = value;
        const length = value.length;
        elements.commandInput.setSelectionRange(length, length);
    }

    async function copySelectedValue() {
        if (state.selectedValue === null || state.selectedValue === undefined) {
            return;
        }
        const text = String(state.selectedValue);
        try {
            if (navigator.clipboard && window.isSecureContext) {
                await navigator.clipboard.writeText(text);
            } else {
                fallbackCopy(text);
            }
            flashCopyLabel('已复制');
        } catch (error) {
            flashCopyLabel('复制失败');
        }
    }

    function fallbackCopy(text) {
        const textarea = document.createElement('textarea');
        textarea.className = 'clipboard-helper';
        textarea.value = text;
        textarea.setAttribute('readonly', '');
        document.body.appendChild(textarea);
        textarea.select();
        try {
            if (!document.execCommand('copy')) {
                throw new Error('copy failed');
            }
        } finally {
            document.body.removeChild(textarea);
        }
    }

    function flashCopyLabel(label) {
        elements.copyValue.textContent = label;
        window.setTimeout(() => {
            elements.copyValue.textContent = '复制';
        }, 1200);
    }

    init();
})();
