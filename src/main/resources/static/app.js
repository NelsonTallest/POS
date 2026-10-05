const uploadForm = document.getElementById("upload-form");
const fileInput = document.getElementById("file-input");
const uploadButton = document.getElementById("upload-button");
const uploadStatus = document.getElementById("upload-status");
const resultsPanel = document.getElementById("results-panel");
const resultSummary = document.getElementById("result-summary");
const columnsTableBody = document.querySelector("#columns-table tbody");
const issuesList = document.getElementById("issues-list");
const suggestionsList = document.getElementById("suggestions-list");
const datasetWarnings = document.getElementById("dataset-warnings");
const historyTableBody = document.querySelector("#history-table tbody");
const historyEmpty = document.getElementById("history-empty");

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
}

async function fetchJSON(url, options) {
    const response = await fetch(url, options);
    const contentType = response.headers.get("content-type") || "";
    const body = contentType.includes("application/json") ? await response.json() : null;
    if (!response.ok) {
        const message = (body && body.error) || `Request failed with status ${response.status}`;
        throw new Error(message);
    }
    return body;
}

function formatNumber(n) {
    if (n === null || n === undefined) return "-";
    return Number.isInteger(n) ? n.toString() : n.toFixed(3);
}

function renderColumns(columns) {
    columnsTableBody.innerHTML = "";
    for (const col of columns) {
        const row = document.createElement("tr");

        row.appendChild(el("td", null, col.name));

        const typeCell = el("td");
        typeCell.appendChild(el("span", "type-pill", col.type));
        row.appendChild(typeCell);

        row.appendChild(el("td", `confidence-${confidenceBand(col.confidence)}`, `${Math.round(col.confidence * 100)}%`));
        row.appendChild(el("td", null, `${col.stats.missingCount} (${col.stats.missingPercent.toFixed(1)}%)`));
        row.appendChild(el("td", null, String(col.stats.distinctCount)));

        const statsCell = el("td");
        if (col.stats.mean !== null && col.stats.mean !== undefined) {
            statsCell.appendChild(el("div", null, `min ${formatNumber(col.stats.min)} · max ${formatNumber(col.stats.max)} · mean ${formatNumber(col.stats.mean)} · stdDev ${formatNumber(col.stats.stdDev)}`));
        } else if (col.stats.topValues && col.stats.topValues.length > 0) {
            const top = col.stats.topValues.slice(0, 3).map(v => `${v.value} (${v.count})`).join(", ");
            statsCell.appendChild(el("div", null, top));
        } else {
            statsCell.appendChild(el("div", "empty-note", "-"));
        }
        row.appendChild(statsCell);

        columnsTableBody.appendChild(row);
    }
}

function confidenceBand(confidence) {
    if (confidence >= 0.85) return "HIGH";
    if (confidence >= 0.6) return "MEDIUM";
    return "LOW";
}

const SEVERITY_ORDER = { HIGH: 0, MEDIUM: 1, LOW: 2 };

function renderIssues(issues) {
    issuesList.innerHTML = "";
    if (!issues || issues.length === 0) {
        issuesList.appendChild(el("li", "empty-note", "No data quality issues detected."));
        return;
    }
    const sorted = [...issues].sort((a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity]);
    for (const issue of sorted) {
        const li = el("li", "issue-item");
        li.appendChild(el("span", `badge badge-${issue.severity}`, issue.severity));
        li.appendChild(el("span", "issue-column", issue.columnName || "dataset"));
        li.appendChild(el("span", "issue-message", issue.message));
        issuesList.appendChild(li);
    }
}

function renderSuggestions(suggestions) {
    suggestionsList.innerHTML = "";
    if (!suggestions || suggestions.length === 0) {
        suggestionsList.appendChild(el("p", "empty-note", "No suggestions available."));
        return;
    }
    for (const s of suggestions) {
        const card = el("div", "suggestion-card");
        const heading = el("h4");
        heading.appendChild(el("span", null, s.type));
        heading.appendChild(el("span", `badge confidence-${s.confidence}`, s.confidence));
        card.appendChild(heading);
        card.appendChild(el("p", null, s.rationale));
        for (const caveat of s.caveats || []) {
            card.appendChild(el("p", "caveat", `⚠ ${caveat}`));
        }
        suggestionsList.appendChild(card);
    }
}

function renderWarnings(warnings) {
    datasetWarnings.innerHTML = "";
    if (!warnings || warnings.length === 0) return;
    const heading = el("h3", null, "Parser Warnings");
    datasetWarnings.appendChild(heading);
    for (const w of warnings) {
        datasetWarnings.appendChild(el("p", null, w));
    }
}

function renderResult(data) {
    resultSummary.innerHTML = "";
    resultSummary.appendChild(el("span", null, "File: "));
    resultSummary.appendChild(el("strong", null, data.fileName));
    resultSummary.appendChild(el("span", null, ` · ${data.rowCount} rows · ${data.columnCount} columns · uploaded ${new Date(data.uploadedAt).toLocaleString()}`));

    renderColumns(data.columns);
    renderIssues(data.issues);
    renderSuggestions(data.suggestions);
    renderWarnings(data.datasetWarnings);

    resultsPanel.hidden = false;
    resultsPanel.scrollIntoView({ behavior: "smooth", block: "start" });
}

async function loadHistory() {
    try {
        const history = await fetchJSON("/api/history");
        historyTableBody.innerHTML = "";
        if (!history || history.length === 0) {
            historyEmpty.hidden = false;
            return;
        }
        historyEmpty.hidden = true;
        for (const item of history) {
            const row = document.createElement("tr");
            row.className = "clickable-row";
            row.appendChild(el("td", null, item.fileName));
            row.appendChild(el("td", null, new Date(item.uploadedAt).toLocaleString()));
            row.appendChild(el("td", null, String(item.rowCount)));
            row.appendChild(el("td", null, String(item.columnCount)));
            row.addEventListener("click", () => viewHistoryItem(item.id));
            historyTableBody.appendChild(row);
        }
    } catch (err) {
        historyEmpty.hidden = false;
        historyEmpty.textContent = `Failed to load history: ${err.message}`;
    }
}

async function viewHistoryItem(id) {
    try {
        const data = await fetchJSON(`/api/history/${id}`);
        renderResult(data);
    } catch (err) {
        uploadStatus.textContent = `Failed to load analysis: ${err.message}`;
        uploadStatus.classList.add("error");
    }
}

uploadForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const file = fileInput.files[0];
    if (!file) return;

    uploadButton.disabled = true;
    uploadStatus.classList.remove("error");
    uploadStatus.textContent = "Analyzing...";

    const formData = new FormData();
    formData.append("file", file);

    try {
        const data = await fetchJSON("/api/upload", { method: "POST", body: formData });
        uploadStatus.textContent = "Analysis complete.";
        renderResult(data);
        loadHistory();
    } catch (err) {
        uploadStatus.textContent = err.message;
        uploadStatus.classList.add("error");
    } finally {
        uploadButton.disabled = false;
    }
});

loadHistory();
