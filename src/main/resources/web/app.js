(() => {
    'use strict';

    const HISTORY_KEY = 'light-redis-panel.command-history';
    const MAX_HISTORY = 100;
    const MAX_CONSOLE_LINES = 500;

    const state = {
        selectedKey: null,
        selectedValue: null,
        commandHistory: [],
        historyIndex: 0,
        busy: false,
        statusInFlight: false,
        refreshTimer: null,
        redisAddress: '127.0.0.1:6379'
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
        detailValue: document.getElementById('detail-value'),
        deleteKey: document.getElementById('delete-key'),
        copyValue: document.getElementById('copy-value'),
        consoleOutput: document.getElementById('console-output'),
        commandForm: document.getElementById('command-form'),
        commandInput: document.getElementById('command-input'),
        runCommand: document.getElementById('run-command'),
        clearConsole: document.getElementById('clear-console'),
        refreshInterval: document.getElementById('refresh-interval'),
        statusUpdated: document.getElementById('status-updated'),
        statusError: document.getElementById('status-error'),
        statUptime: document.getElementById('stat-uptime'),
        statCommands: document.getElementById('stat-commands'),
        statClients: document.getElementById('stat-clients'),
        statKeys: document.getElementById('stat-keys'),
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
            chip.addEventListener('click', () => {
                const command = chip.dataset.command || '';
                elements.commandInput.value = command;
                elements.commandInput.focus();
                elements.commandInput.setSelectionRange(command.length, command.length);
            });
        }
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
                if (verb === 'SET' || verb === 'DEL') {
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
        elements.keyList.textContent = '';
        elements.keyCount.textContent = keys.length + ' 个 key';

        if (keys.length === 0) {
            const empty = document.createElement('p');
            empty.className = 'empty';
            empty.textContent = '没有匹配的 key';
            elements.keyList.appendChild(empty);
            clearKeyDetail();
            return;
        }

        let selectedExists = false;
        for (const key of keys) {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'key-item mono';
            item.textContent = key;
            item.dataset.key = key;
            item.addEventListener('click', () => selectKey(key));
            if (key === state.selectedKey) {
                item.classList.add('active');
                selectedExists = true;
            }
            elements.keyList.appendChild(item);
        }

        if (!selectedExists) {
            clearKeyDetail();
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
        highlightSelectedKey();
        updateKeyHash(name);
        try {
            const data = await request('/api/key?name=' + encodeURIComponent(name));
            elements.detailName.textContent = data.key || name;
            elements.detailType.textContent = data.type || 'unknown';
            elements.detailType.dataset.type = data.type || 'unknown';
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
            elements.deleteKey.disabled = false;
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
        for (const item of elements.keyList.querySelectorAll('.key-item')) {
            item.classList.toggle('active', item.dataset.key === state.selectedKey);
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
        elements.detailName.textContent = '';
        elements.detailType.textContent = '';
        elements.detailType.dataset.type = '';
        elements.detailValue.textContent = '';
        elements.copyValue.disabled = true;
        elements.copyValue.textContent = '复制';
        elements.deleteKey.disabled = false;
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
        elements.consoleOutput.textContent = '';
        const hint = document.createElement('div');
        hint.className = 'console-line console-hint';
        hint.textContent = '输入命令后按 Enter 执行，↑/↓ 查看历史，Ctrl+L 清屏。支持引号：SET k "hello world"。';
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
