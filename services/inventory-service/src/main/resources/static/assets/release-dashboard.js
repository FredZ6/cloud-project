(() => {
    "use strict";

    const QUERY_ENDPOINT = "/api/stocks/release-events";
    const EXPORT_ENDPOINT = "/api/stocks/release-events/export";
    const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
    const SVG_NS = "http://www.w3.org/2000/svg";
    const DONUT_COLORS = [
        "#0f766e",
        "#c05f2c",
        "#4c7fa3",
        "#7b6ca8",
        "#ad7f1f",
        "#4d8f46",
        "#b45c80",
        "#567582"
    ];

    const dateTimeFormatter = new Intl.DateTimeFormat(undefined, {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false
    });

    const axisFormatter = new Intl.DateTimeFormat(undefined, {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false
    });

    const clockFormatter = new Intl.DateTimeFormat(undefined, {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hour12: false
    });

    const elements = {
        orderId: byId("orderId"),
        fromTime: byId("fromTime"),
        toTime: byId("toTime"),
        pageSize: byId("pageSize"),
        exportLimit: byId("exportLimit"),
        loadButton: byId("loadButton"),
        resetButton: byId("resetButton"),
        exportButton: byId("exportButton"),
        autoRefreshToggle: byId("autoRefreshToggle"),
        autoRefreshInterval: byId("autoRefreshInterval"),
        autoRefreshState: byId("autoRefreshState"),
        statusMessage: byId("statusMessage"),
        metricTotal: byId("metricTotal"),
        metricPageCount: byId("metricPageCount"),
        metricNewest: byId("metricNewest"),
        metricOldest: byId("metricOldest"),
        trendHint: byId("trendHint"),
        trendChart: byId("trendChart"),
        reasonHint: byId("reasonHint"),
        reasonDonut: byId("reasonDonut"),
        reasonDonutCenter: byId("reasonDonutCenter"),
        reasonLegend: byId("reasonLegend"),
        pageIndicator: byId("pageIndicator"),
        eventRows: byId("eventRows"),
        prevPageButton: byId("prevPageButton"),
        nextPageButton: byId("nextPageButton")
    };

    if (!elements.loadButton || !elements.eventRows) {
        return;
    }

    const state = {
        page: 0,
        size: parsePositiveInt(elements.pageSize?.value, 20, 1, 100),
        totalElements: 0,
        totalPages: 0,
        hasNext: false,
        items: [],
        loading: false,
        exporting: false,
        requestId: 0,
        lastLoadedAt: null,
        autoRefreshEnabled: Boolean(elements.autoRefreshToggle?.checked),
        autoRefreshIntervalSec: parsePositiveInt(elements.autoRefreshInterval?.value, 30, 15, 600),
        autoRefreshTimerId: null
    };

    bindEvents();
    renderEmptyState();
    updatePager();
    updateAutoRefreshState();
    loadData({targetPage: 0, silent: false}).finally(syncAutoRefresh);

    function bindEvents() {
        elements.loadButton?.addEventListener("click", () => {
            state.size = parsePositiveInt(elements.pageSize?.value, state.size, 1, 100);
            loadData({targetPage: 0, silent: false});
        });

        elements.resetButton?.addEventListener("click", () => {
            if (elements.orderId) {
                elements.orderId.value = "";
            }
            if (elements.fromTime) {
                elements.fromTime.value = "";
            }
            if (elements.toTime) {
                elements.toTime.value = "";
            }
            if (elements.pageSize) {
                elements.pageSize.value = "20";
            }
            if (elements.exportLimit) {
                elements.exportLimit.value = "1000";
            }
            state.page = 0;
            state.size = 20;
            clearStatus();
            loadData({targetPage: 0, silent: false});
        });

        elements.exportButton?.addEventListener("click", exportCsv);

        elements.pageSize?.addEventListener("change", () => {
            state.size = parsePositiveInt(elements.pageSize?.value, state.size, 1, 100);
            loadData({targetPage: 0, silent: false});
        });

        elements.prevPageButton?.addEventListener("click", () => {
            if (state.loading || state.exporting || state.page <= 0) {
                return;
            }
            loadData({targetPage: state.page - 1, silent: false});
        });

        elements.nextPageButton?.addEventListener("click", () => {
            if (state.loading || state.exporting || !state.hasNext) {
                return;
            }
            loadData({targetPage: state.page + 1, silent: false});
        });

        [elements.orderId, elements.fromTime, elements.toTime].forEach((input) => {
            input?.addEventListener("keydown", (event) => {
                if (event.key !== "Enter") {
                    return;
                }
                event.preventDefault();
                state.size = parsePositiveInt(elements.pageSize?.value, state.size, 1, 100);
                loadData({targetPage: 0, silent: false});
            });
        });

        elements.autoRefreshToggle?.addEventListener("change", () => {
            state.autoRefreshEnabled = Boolean(elements.autoRefreshToggle?.checked);
            syncAutoRefresh();
            if (state.autoRefreshEnabled) {
                loadData({targetPage: state.page, silent: true});
            }
        });

        elements.autoRefreshInterval?.addEventListener("change", () => {
            state.autoRefreshIntervalSec = parsePositiveInt(elements.autoRefreshInterval?.value, 30, 15, 600);
            syncAutoRefresh();
        });

        document.addEventListener("visibilitychange", () => {
            if (!state.autoRefreshEnabled) {
                return;
            }
            if (document.hidden) {
                clearAutoRefreshTimer();
                updateAutoRefreshState();
                return;
            }
            syncAutoRefresh();
            loadData({targetPage: state.page, silent: true});
        });
    }

    async function loadData(options = {}) {
        const targetPage = typeof options.targetPage === "number" ? Math.max(options.targetPage, 0) : state.page;
        const silent = Boolean(options.silent);

        let filters;
        try {
            filters = readFilters();
        } catch (error) {
            setStatus(error.message || "Invalid filter input.", "error");
            return;
        }

        const params = new URLSearchParams();
        if (filters.orderId) {
            params.set("orderId", filters.orderId);
        }
        if (filters.from) {
            params.set("from", filters.from);
        }
        if (filters.to) {
            params.set("to", filters.to);
        }
        params.set("page", String(targetPage));
        params.set("size", String(state.size));

        const currentRequest = ++state.requestId;
        state.loading = true;
        updateInteractiveState();
        if (!silent) {
            setStatus("Loading release events...", "");
        }

        try {
            const response = await fetch(`${QUERY_ENDPOINT}?${params.toString()}`, {
                headers: {"Accept": "application/json"}
            });
            if (!response.ok) {
                throw new Error(await readErrorMessage(response, "Failed to load release events."));
            }

            const payload = await response.json();
            if (currentRequest !== state.requestId) {
                return;
            }

            state.page = Number.isInteger(payload.page) ? payload.page : targetPage;
            state.size = Number.isInteger(payload.size) ? payload.size : state.size;
            if (elements.pageSize) {
                elements.pageSize.value = String(state.size);
            }
            state.totalElements = Number.isFinite(payload.totalElements) ? payload.totalElements : 0;
            state.totalPages = Number.isInteger(payload.totalPages) ? payload.totalPages : 0;
            state.hasNext = Boolean(payload.hasNext);
            state.items = Array.isArray(payload.items) ? payload.items : [];
            state.lastLoadedAt = new Date();

            renderSummary(state.items, state.totalElements);
            renderTable(state.items);
            renderTrendChart(state.items);
            renderReasonShare(state.items);
            updatePager();
            updateAutoRefreshState();

            if (!silent) {
                setStatus(`Loaded ${state.items.length} rows. Total matched: ${state.totalElements}.`, "success");
            }
        } catch (error) {
            if (currentRequest === state.requestId) {
                setStatus(error.message || "Failed to load release events.", "error");
            }
        } finally {
            if (currentRequest === state.requestId) {
                state.loading = false;
                updateInteractiveState();
            }
        }
    }

    async function exportCsv() {
        let filters;
        try {
            filters = readFilters();
        } catch (error) {
            setStatus(error.message || "Invalid filter input.", "error");
            return;
        }

        const limit = parsePositiveInt(elements.exportLimit?.value, 1000, 1, 10_000);
        if (!Number.isInteger(limit) || limit < 1 || limit > 10_000) {
            setStatus("Export limit must be between 1 and 10000.", "error");
            return;
        }

        const params = new URLSearchParams();
        if (filters.orderId) {
            params.set("orderId", filters.orderId);
        }
        if (filters.from) {
            params.set("from", filters.from);
        }
        if (filters.to) {
            params.set("to", filters.to);
        }
        params.set("limit", String(limit));

        state.exporting = true;
        updateInteractiveState();
        setStatus("Exporting CSV...", "");

        try {
            const response = await fetch(`${EXPORT_ENDPOINT}?${params.toString()}`, {
                headers: {"Accept": "text/csv"}
            });
            if (!response.ok) {
                throw new Error(await readErrorMessage(response, "Failed to export CSV."));
            }

            const blob = await response.blob();
            const disposition = response.headers.get("content-disposition");
            const filename = parseFilename(disposition) || `inventory-release-events-${Date.now()}.csv`;
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = filename;
            document.body.append(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
            setStatus(`CSV exported: ${filename}`, "success");
        } catch (error) {
            setStatus(error.message || "Failed to export CSV.", "error");
        } finally {
            state.exporting = false;
            updateInteractiveState();
        }
    }

    function readFilters() {
        const orderId = elements.orderId?.value.trim() || "";
        const fromRaw = elements.fromTime?.value || "";
        const toRaw = elements.toTime?.value || "";

        if (orderId && !UUID_PATTERN.test(orderId)) {
            throw new Error("Order ID must be a valid UUID.");
        }

        const from = toIsoString(fromRaw);
        const to = toIsoString(toRaw);
        if (from && to && Date.parse(from) > Date.parse(to)) {
            throw new Error("'From' time must be earlier than or equal to 'To' time.");
        }

        return {orderId, from, to};
    }

    function renderSummary(items, totalElements) {
        if (elements.metricTotal) {
            elements.metricTotal.textContent = formatCount(totalElements);
        }
        if (elements.metricPageCount) {
            elements.metricPageCount.textContent = formatCount(items.length);
        }

        const validTimes = items
                .map((item) => Date.parse(item?.createdAt))
                .filter((timestamp) => Number.isFinite(timestamp));

        if (!validTimes.length) {
            if (elements.metricNewest) {
                elements.metricNewest.textContent = "-";
            }
            if (elements.metricOldest) {
                elements.metricOldest.textContent = "-";
            }
            return;
        }

        const newest = new Date(Math.max(...validTimes));
        const oldest = new Date(Math.min(...validTimes));
        if (elements.metricNewest) {
            elements.metricNewest.textContent = formatDateTime(newest);
        }
        if (elements.metricOldest) {
            elements.metricOldest.textContent = formatDateTime(oldest);
        }
    }

    function renderTable(items) {
        if (!elements.eventRows) {
            return;
        }

        elements.eventRows.replaceChildren();
        if (!items.length) {
            const emptyRow = document.createElement("tr");
            const emptyCell = document.createElement("td");
            emptyCell.colSpan = 5;
            emptyCell.className = "empty";
            emptyCell.textContent = "No release events found for current filters.";
            emptyRow.append(emptyCell);
            elements.eventRows.append(emptyRow);
            return;
        }

        const fragment = document.createDocumentFragment();
        for (const item of items) {
            const row = document.createElement("tr");
            row.append(
                    makeCell(item?.releaseId),
                    makeCell(item?.orderId),
                    makeCell(item?.reservationId),
                    makeCell(item?.reason),
                    makeCell(formatDateTime(item?.createdAt))
            );
            fragment.append(row);
        }
        elements.eventRows.append(fragment);
    }

    function renderTrendChart(items) {
        if (!elements.trendChart || !elements.trendHint) {
            return;
        }

        elements.trendChart.replaceChildren();
        const buckets = buildTrendBuckets(items);
        if (!buckets.length) {
            const empty = createSvgNode("text", {
                x: 320,
                y: 118,
                "text-anchor": "middle",
                class: "trend-empty"
            });
            empty.textContent = "No timeline data on current page.";
            elements.trendChart.append(empty);
            elements.trendHint.textContent = "No data loaded yet.";
            return;
        }

        const width = 640;
        const height = 220;
        const margin = {top: 14, right: 16, bottom: 34, left: 38};
        const chartWidth = width - margin.left - margin.right;
        const chartHeight = height - margin.top - margin.bottom;
        const baseline = margin.top + chartHeight;

        const minTs = buckets[0].ts;
        const maxTs = buckets[buckets.length - 1].ts;
        const timeSpan = Math.max(maxTs - minTs, 1);
        const maxCount = Math.max(...buckets.map((point) => point.count), 1);

        const points = buckets.map((bucket, index) => {
            const x = buckets.length === 1
                    ? margin.left + chartWidth / 2
                    : margin.left + ((bucket.ts - minTs) / timeSpan) * chartWidth;
            const y = baseline - (bucket.count / maxCount) * chartHeight;
            return {x, y, count: bucket.count, ts: bucket.ts, index};
        });

        for (let i = 0; i <= 4; i += 1) {
            const y = margin.top + (chartHeight / 4) * i;
            elements.trendChart.append(createSvgNode("line", {
                x1: margin.left,
                y1: y,
                x2: margin.left + chartWidth,
                y2: y,
                class: "trend-grid"
            }));
        }

        const linePath = points
                .map((point, index) => `${index === 0 ? "M" : "L"} ${round(point.x)} ${round(point.y)}`)
                .join(" ");
        const areaPath = `${linePath} L ${round(points[points.length - 1].x)} ${round(baseline)} L ${round(points[0].x)} ${round(baseline)} Z`;

        elements.trendChart.append(createSvgNode("path", {d: areaPath, class: "trend-area"}));
        elements.trendChart.append(createSvgNode("path", {d: linePath, class: "trend-line"}));

        for (const point of points) {
            elements.trendChart.append(createSvgNode("circle", {
                cx: round(point.x),
                cy: round(point.y),
                r: 3.7,
                class: "trend-point"
            }));
        }

        const labelIndexes = uniqueSortedIndexes(points.length);
        for (const idx of labelIndexes) {
            const point = points[idx];
            const label = createSvgNode("text", {
                x: round(point.x),
                y: height - 10,
                "text-anchor": "middle",
                class: "trend-axis-label"
            });
            label.textContent = formatAxisLabel(point.ts);
            elements.trendChart.append(label);
        }

        const maxLabel = createSvgNode("text", {
            x: 8,
            y: margin.top + 8,
            class: "trend-axis-label"
        });
        maxLabel.textContent = String(maxCount);
        elements.trendChart.append(maxLabel);

        const zeroLabel = createSvgNode("text", {
            x: 16,
            y: baseline,
            class: "trend-axis-label"
        });
        zeroLabel.textContent = "0";
        elements.trendChart.append(zeroLabel);

        elements.trendHint.textContent = `${items.length} events on page, ${buckets.length} time buckets, peak ${maxCount}.`;
    }

    function renderReasonShare(items) {
        if (!elements.reasonDonut || !elements.reasonLegend || !elements.reasonHint || !elements.reasonDonutCenter) {
            return;
        }

        const counts = new Map();
        for (const item of items) {
            const key = (item?.reason && String(item.reason).trim()) || "UNKNOWN";
            counts.set(key, (counts.get(key) || 0) + 1);
        }

        const total = items.length;
        const totalText = elements.reasonDonutCenter.querySelector(".donut-total");
        if (totalText) {
            totalText.textContent = String(total);
        }

        elements.reasonLegend.replaceChildren();
        if (!counts.size || !total) {
            elements.reasonDonut.classList.add("empty");
            elements.reasonDonut.style.background = "conic-gradient(#dbe1dd 0 100%)";
            const empty = document.createElement("li");
            empty.className = "empty";
            empty.textContent = "No reasons to display.";
            elements.reasonLegend.append(empty);
            elements.reasonHint.textContent = "No data loaded yet.";
            return;
        }

        const entries = Array.from(counts.entries())
                .sort((left, right) => {
                    if (right[1] !== left[1]) {
                        return right[1] - left[1];
                    }
                    return left[0].localeCompare(right[0]);
                });

        const segments = [];
        let progress = 0;
        entries.forEach(([reason, count], index) => {
            const ratio = count / total;
            const start = progress * 100;
            progress += ratio;
            const end = Math.min(progress * 100, 100);
            const color = DONUT_COLORS[index % DONUT_COLORS.length];
            segments.push(`${color} ${start.toFixed(2)}% ${end.toFixed(2)}%`);

            const item = document.createElement("li");
            const left = document.createElement("span");
            left.className = "legend-left";
            const dot = document.createElement("span");
            dot.className = "legend-dot";
            dot.style.backgroundColor = color;
            const name = document.createElement("span");
            name.className = "legend-name";
            name.textContent = reason;
            left.append(dot, name);

            const value = document.createElement("span");
            value.className = "legend-value";
            value.textContent = `${count} (${formatPercent(ratio)})`;
            item.append(left, value);
            elements.reasonLegend.append(item);
        });

        elements.reasonDonut.classList.remove("empty");
        elements.reasonDonut.style.background = `conic-gradient(${segments.join(", ")})`;

        const [topReason, topCount] = entries[0];
        elements.reasonHint.textContent = `${entries.length} reasons, top: ${topReason} (${formatPercent(topCount / total)}).`;
    }

    function renderEmptyState() {
        renderSummary([], 0);
        renderTable([]);
        renderTrendChart([]);
        renderReasonShare([]);
    }

    function updatePager() {
        if (elements.pageIndicator) {
            const current = state.page + 1;
            const totalPages = state.totalPages > 0 ? state.totalPages : 1;
            elements.pageIndicator.textContent = `Page ${current} / ${totalPages}`;
        }
        updateInteractiveState();
    }

    function updateInteractiveState() {
        const busy = state.loading || state.exporting;
        if (elements.loadButton) {
            elements.loadButton.disabled = state.loading;
        }
        if (elements.resetButton) {
            elements.resetButton.disabled = busy;
        }
        if (elements.exportButton) {
            elements.exportButton.disabled = busy;
        }
        if (elements.pageSize) {
            elements.pageSize.disabled = busy;
        }
        if (elements.prevPageButton) {
            elements.prevPageButton.disabled = busy || state.page <= 0;
        }
        if (elements.nextPageButton) {
            elements.nextPageButton.disabled = busy || !state.hasNext;
        }
    }

    function syncAutoRefresh() {
        clearAutoRefreshTimer();
        if (!state.autoRefreshEnabled) {
            updateAutoRefreshState();
            return;
        }
        if (document.hidden) {
            updateAutoRefreshState();
            return;
        }

        const delay = state.autoRefreshIntervalSec * 1000;
        state.autoRefreshTimerId = window.setTimeout(async () => {
            state.autoRefreshTimerId = null;
            if (!state.autoRefreshEnabled) {
                updateAutoRefreshState();
                return;
            }
            if (!document.hidden) {
                await loadData({targetPage: state.page, silent: true});
            }
            syncAutoRefresh();
        }, delay);

        updateAutoRefreshState();
    }

    function clearAutoRefreshTimer() {
        if (state.autoRefreshTimerId !== null) {
            window.clearTimeout(state.autoRefreshTimerId);
            state.autoRefreshTimerId = null;
        }
    }

    function updateAutoRefreshState() {
        if (!elements.autoRefreshState) {
            return;
        }
        if (elements.autoRefreshInterval) {
            elements.autoRefreshInterval.disabled = !state.autoRefreshEnabled;
        }
        if (!state.autoRefreshEnabled) {
            elements.autoRefreshState.textContent = "Auto refresh is off.";
            return;
        }
        if (document.hidden) {
            elements.autoRefreshState.textContent = "Auto refresh paused while this tab is hidden.";
            return;
        }
        if (state.lastLoadedAt) {
            elements.autoRefreshState.textContent = `Auto refresh every ${state.autoRefreshIntervalSec}s. Last sync ${clockFormatter.format(state.lastLoadedAt)}.`;
            return;
        }
        elements.autoRefreshState.textContent = `Auto refresh every ${state.autoRefreshIntervalSec}s.`;
    }

    function buildTrendBuckets(items) {
        const timestamps = items
                .map((item) => Date.parse(item?.createdAt))
                .filter((value) => Number.isFinite(value))
                .sort((left, right) => left - right);

        if (!timestamps.length) {
            return [];
        }

        const minTs = timestamps[0];
        const maxTs = timestamps[timestamps.length - 1];
        const span = maxTs - minTs;

        let bucketSizeMs = 60_000;
        if (span > 14 * 24 * 60 * 60 * 1000) {
            bucketSizeMs = 24 * 60 * 60 * 1000;
        } else if (span > 72 * 60 * 60 * 1000) {
            bucketSizeMs = 6 * 60 * 60 * 1000;
        } else if (span > 12 * 60 * 60 * 1000) {
            bucketSizeMs = 60 * 60 * 1000;
        } else if (span > 2 * 60 * 60 * 1000) {
            bucketSizeMs = 10 * 60 * 1000;
        }

        let buckets = aggregateByBucket(timestamps, bucketSizeMs);
        while (buckets.length > 18) {
            bucketSizeMs *= 2;
            buckets = aggregateByBucket(timestamps, bucketSizeMs);
        }
        return buckets;
    }

    function aggregateByBucket(timestamps, bucketSizeMs) {
        const map = new Map();
        for (const timestamp of timestamps) {
            const bucket = Math.floor(timestamp / bucketSizeMs) * bucketSizeMs;
            map.set(bucket, (map.get(bucket) || 0) + 1);
        }
        return Array.from(map.entries())
                .sort((left, right) => left[0] - right[0])
                .map(([ts, count]) => ({ts, count}));
    }

    function parseFilename(dispositionHeader) {
        if (!dispositionHeader) {
            return "";
        }
        const utfMatch = dispositionHeader.match(/filename\*=UTF-8''([^;]+)/i);
        if (utfMatch && utfMatch[1]) {
            try {
                return decodeURIComponent(utfMatch[1]);
            } catch (_error) {
                return utfMatch[1];
            }
        }
        const plainMatch = dispositionHeader.match(/filename=\"?([^\";]+)\"?/i);
        return plainMatch?.[1] || "";
    }

    async function readErrorMessage(response, fallback) {
        let body = "";
        try {
            body = await response.text();
        } catch (_error) {
            return fallback;
        }

        if (!body) {
            return fallback;
        }

        try {
            const json = JSON.parse(body);
            if (typeof json.message === "string" && json.message.trim()) {
                return json.message.trim();
            }
            if (typeof json.error === "string" && json.error.trim()) {
                return json.error.trim();
            }
        } catch (_error) {
            // keep plain text
        }

        const text = body.trim();
        if (!text) {
            return fallback;
        }
        return text.length > 240 ? `${text.slice(0, 237)}...` : text;
    }

    function setStatus(message, type) {
        if (!elements.statusMessage) {
            return;
        }
        elements.statusMessage.classList.remove("error", "success");
        if (type === "error" || type === "success") {
            elements.statusMessage.classList.add(type);
        }
        elements.statusMessage.textContent = message || "";
    }

    function clearStatus() {
        setStatus("", "");
    }

    function makeCell(value) {
        const cell = document.createElement("td");
        if (value === undefined || value === null || value === "") {
            cell.textContent = "-";
            return cell;
        }
        cell.textContent = String(value);
        return cell;
    }

    function createSvgNode(tagName, attributes) {
        const node = document.createElementNS(SVG_NS, tagName);
        Object.entries(attributes).forEach(([key, value]) => {
            node.setAttribute(key, String(value));
        });
        return node;
    }

    function uniqueSortedIndexes(size) {
        if (size <= 0) {
            return [];
        }
        if (size === 1) {
            return [0];
        }
        const values = [0, Math.floor((size - 1) / 2), size - 1];
        return Array.from(new Set(values)).sort((left, right) => left - right);
    }

    function toIsoString(localInput) {
        if (!localInput) {
            return "";
        }
        const parsed = new Date(localInput);
        if (Number.isNaN(parsed.getTime())) {
            throw new Error("Date/time format is invalid.");
        }
        return parsed.toISOString();
    }

    function byId(id) {
        return document.getElementById(id);
    }

    function parsePositiveInt(raw, fallback, min, max) {
        const value = Number.parseInt(String(raw ?? ""), 10);
        if (!Number.isInteger(value)) {
            return fallback;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    function formatDateTime(value) {
        const date = value instanceof Date ? value : new Date(value);
        if (Number.isNaN(date.getTime())) {
            return typeof value === "string" && value ? value : "-";
        }
        return dateTimeFormatter.format(date);
    }

    function formatAxisLabel(timestamp) {
        const date = new Date(timestamp);
        if (Number.isNaN(date.getTime())) {
            return "-";
        }
        return axisFormatter.format(date);
    }

    function formatPercent(ratio) {
        return `${(ratio * 100).toFixed(ratio >= 0.1 ? 1 : 2)}%`;
    }

    function formatCount(value) {
        const safe = Number.isFinite(value) ? value : 0;
        return new Intl.NumberFormat().format(safe);
    }

    function round(value) {
        return Number(value).toFixed(2);
    }
})();
