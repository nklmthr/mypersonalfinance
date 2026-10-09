import React, { useEffect, useState, useCallback } from "react";
import api from "../auth/api";
import { useNavigate, useLocation } from "react-router-dom";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { ChevronLeftIcon, ChevronRightIcon, ChevronDownIcon } from "@heroicons/react/24/outline";
import { buildTree } from "./transactions/utils/utils";

const fmt = (num) =>
  num != null
    ? `₹${parseFloat(num).toLocaleString("en-IN", { minimumFractionDigits: 0, maximumFractionDigits: 0 })}`
    : "–";

function fmtDateTime(dateStr) {
  if (!dateStr) return "–";
  const d = new Date(dateStr);
  if (isNaN(d)) return dateStr.slice(0, 16).replace("T", " ");
  return d.toLocaleString("en-IN", {
    year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hour12: false,
  });
}

function sortByDateDesc(txns) {
  return [...txns].sort((a, b) => new Date(b.date ?? 0) - new Date(a.date ?? 0));
}

// Consistent grid row for both mapped-list and search-list
function TxRow({ t, onAction, actionLabel, actionClass, hoverClass = "hover:bg-gray-50" }) {
  return (
    <div
      className={`grid grid-cols-[1fr_auto_auto_auto] gap-x-3 items-center px-2 py-1.5 text-xs border-b border-gray-100 ${hoverClass} ${onAction && !actionLabel ? "cursor-pointer" : ""}`}
      onClick={!actionLabel && onAction ? (e) => { e.stopPropagation(); onAction(); } : undefined}
    >
      <span className="text-gray-700 truncate">{t.description || t.categoryName}</span>
      <span className="text-gray-400 shrink-0 whitespace-nowrap">{fmtDateTime(t.date)}</span>
      <span className="font-medium text-gray-800 shrink-0 text-right">{fmt(t.amount)}</span>
      {actionLabel
        ? <button className={`shrink-0 ${actionClass}`} onClick={(e) => { e.stopPropagation(); onAction(); }}>{actionLabel}</button>
        : <span />}
    </div>
  );
}

function monthLabel(ym) {
  if (!ym) return "";
  const [year, month] = ym.split("-").map(Number);
  return new Date(year, month - 1, 1).toLocaleString("default", { month: "long", year: "numeric" });
}

function currentYearMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function addMonths(ym, delta) {
  const [y, m] = ym.split("-").map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

function isPastOrCurrentMonth(ym) {
  return ym <= currentYearMonth();
}

const STRATEGIES = ["HIGHER_OF", "AVERAGE", "SUM", "MANUAL"];
const STRATEGY_LABELS = {
  HIGHER_OF: "Higher Of",
  AVERAGE: "Average (Rule)",
  SUM: "Sum Both",
  MANUAL: "Manual",
};

function resolveEffectiveAmount(ruleAmt, recurringTotal, strategy, manualAmt) {
  const r = parseFloat(ruleAmt ?? 0);
  const rec = parseFloat(recurringTotal ?? 0);
  switch (strategy) {
    case "HIGHER_OF": return Math.max(r, rec);
    case "AVERAGE": return r;
    case "SUM": return r + rec;
    case "MANUAL": return parseFloat(manualAmt ?? 0) || Math.max(r, rec);
    default: return Math.max(r, rec);
  }
}

function getStoredStrategy(categoryId) {
  try {
    const stored = JSON.parse(localStorage.getItem("forecastStrategies") || "{}");
    return stored[categoryId] || { strategy: "HIGHER_OF", manualAmt: "" };
  } catch { return { strategy: "HIGHER_OF", manualAmt: "" }; }
}

function setStoredStrategy(categoryId, strategy, manualAmt) {
  try {
    const stored = JSON.parse(localStorage.getItem("forecastStrategies") || "{}");
    stored[categoryId] = { strategy, manualAmt };
    localStorage.setItem("forecastStrategies", JSON.stringify(stored));
  } catch {}
}

// ── Merged row: rule prediction + one or more recurring obligations ───────────

function MergedPredictionRow({ rulePred, recurringList, onUpdatePrediction, onStrategyChange, transactions, isIncome, globalMappedIds }) {
  const categoryId = rulePred.categoryId;
  const initial = getStoredStrategy(categoryId);
  const [strategy, setStrategy] = useState(initial.strategy);
  const [manualAmt, setManualAmt] = useState(initial.manualAmt);
  const [expanded, setExpanded] = useState(false);
  const [recurringExpanded, setRecurringExpanded] = useState(false);
  const [showMapPanel, setShowMapPanel] = useState(false);
  const [mapTarget, setMapTarget] = useState(null); // rulePred.id or a recurringPred.id
  const [linkedRule, setLinkedRule] = useState(null);
  const [linkedRecurringMap, setLinkedRecurringMap] = useState({}); // id → linked data
  const [loadingRow, setLoadingRow] = useState(false);
  const [txSearch, setTxSearch] = useState("");

  const ruleAmt = parseFloat(rulePred.predictedAmount ?? 0);
  // Sum all recurring obligations for this category
  const recurringTotal = recurringList.reduce((s, p) => s + parseFloat(p.predictedAmount ?? 0), 0);
  const effectiveAmt = resolveEffectiveAmount(ruleAmt, recurringTotal, strategy, manualAmt);

  const ruleActual = parseFloat(rulePred.actualSpent ?? 0);
  const recurringActual = recurringList.reduce((s, p) => s + parseFloat(p.actualSpent ?? 0), 0);
  const totalActual = ruleActual + recurringActual;
  const remaining = effectiveAmt - totalActual;
  const pct = effectiveAmt > 0 ? Math.min(100, (totalActual / effectiveAmt) * 100) : 0;
  const isOver = totalActual > effectiveAmt;

  const handleStrategyChange = (s) => {
    setStrategy(s);
    setStoredStrategy(categoryId, s, manualAmt);
    onStrategyChange?.(categoryId, s, manualAmt);
  };
  const handleManualAmt = (v) => {
    setManualAmt(v);
    setStoredStrategy(categoryId, strategy, v);
    onStrategyChange?.(categoryId, strategy, v);
  };

  const loadLinked = async () => {
    setLoadingRow(true);
    try {
      const [rRes, ...recResults] = await Promise.all([
        api.get(`/predicted-transactions/${rulePred.id}/linked-transactions`),
        ...recurringList.map((p) => api.get(`/predicted-transactions/${p.id}/linked-transactions`)),
      ]);
      setLinkedRule(rRes.data);
      const map = {};
      recurringList.forEach((p, i) => { map[p.id] = recResults[i].data; });
      setLinkedRecurringMap(map);
    } catch (e) {
      console.error("Failed to load linked transactions", e);
    } finally {
      setLoadingRow(false);
    }
  };

  const toggleExpand = () => {
    if (!expanded) loadLinked();
    setExpanded(!expanded);
    setShowMapPanel(false);
  };

  const handleMap = async (predId, txId) => {
    try {
      const res = await api.post(`/predicted-transactions/${predId}/map-actual?transactionId=${txId}`);
      onUpdatePrediction(res.data);
      // Reload all linked data in-place — keep panel and row open
      const [ruleRes, ...recurringRes] = await Promise.all([
        api.get(`/predicted-transactions/${rulePred.id}/linked-transactions`),
        ...recurringList.map((p) => api.get(`/predicted-transactions/${p.id}/linked-transactions`)),
      ]);
      setLinkedRule(ruleRes.data);
      const newMap = {};
      recurringList.forEach((p, i) => { newMap[p.id] = recurringRes[i].data; });
      setLinkedRecurringMap(newMap);
    } catch (e) { console.error("Map failed", e); }
  };

  const handleUnmap = async (predId, txId) => {
    try {
      const res = await api.post(`/predicted-transactions/${predId}/unmap-actual?transactionId=${txId}`);
      onUpdatePrediction(res.data);
      const [ruleRes, ...recurringRes] = await Promise.all([
        api.get(`/predicted-transactions/${rulePred.id}/linked-transactions`),
        ...recurringList.map((p) => api.get(`/predicted-transactions/${p.id}/linked-transactions`)),
      ]);
      setLinkedRule(ruleRes.data);
      const newMap = {};
      recurringList.forEach((p, i) => { newMap[p.id] = recurringRes[i].data; });
      setLinkedRecurringMap(newMap);
    } catch (e) { console.error("Unmap failed", e); }
  };

  const allMappedIds = new Set([
    ...(linkedRule?.actualTransactions || []).map((t) => t.id),
    ...Object.values(linkedRecurringMap).flatMap((d) => (d?.actualTransactions || []).map((t) => t.id)),
  ]);

  const filteredTx = transactions.filter((t) => {
    if (allMappedIds.has(t.id)) return false;
    if (globalMappedIds?.has(t.id)) return false;
    return !txSearch ||
      t.description?.toLowerCase().includes(txSearch.toLowerCase()) ||
      t.categoryName?.toLowerCase().includes(txSearch.toLowerCase());
  });

  const isPast = isPastOrCurrentMonth(rulePred.predictionMonth);

  return (
    <div className="rounded border-2 border-purple-300 mb-2 bg-purple-50">
      {/* Header row */}
      <div
        className="grid grid-cols-1 md:grid-cols-7 gap-3 py-3 px-4 items-center text-sm cursor-pointer"
        onClick={toggleExpand}
      >
        <div className="font-medium text-gray-800 md:col-span-2">
          {rulePred.categoryName}
          <span className="ml-1 text-xs bg-purple-200 text-purple-800 px-1.5 py-0.5 rounded">Merged</span>
        </div>

        {/* Sub-amounts with expandable recurring list */}
        <div className="text-xs text-gray-500 space-y-0.5" onClick={(e) => e.stopPropagation()}>
          <div>Rule avg: <span className="font-medium text-blue-700">{fmt(ruleAmt)}</span></div>
          <div
            className="flex items-center gap-1 cursor-pointer hover:text-amber-700"
            onClick={() => setRecurringExpanded(!recurringExpanded)}
          >
            <span>{recurringExpanded ? <ChevronDownIcon className="h-3 w-3 inline" /> : <ChevronRightIcon className="h-3 w-3 inline" />}</span>
            <span>
              Recurring ({recurringList.length}): <span className="font-medium text-amber-700">{fmt(recurringTotal)}</span>
            </span>
          </div>
          {/* Expandable recurring obligations list */}
          {recurringExpanded && (
            <div className="mt-1 ml-2 space-y-1 border-l-2 border-amber-200 pl-2">
              {recurringList.map((p) => (
                <div key={p.id} className="flex justify-between gap-2">
                  <span className="text-gray-600 truncate">{p.recurringTransactionDescription || p.description}</span>
                  <span className="font-medium text-amber-700 flex-shrink-0">{fmt(p.predictedAmount)}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Effective amount */}
        <div className="text-lg font-bold text-purple-700">{fmt(effectiveAmt)}</div>

        {isPast && (
          <>
            <div className={`font-medium text-sm ${isIncome ? (isOver ? "text-green-700" : "text-gray-700") : (isOver ? "text-red-600" : "text-green-700")}`}>
              {fmt(totalActual)} {isIncome ? "received" : "spent"}
            </div>
            <div className="text-gray-600 text-sm">{fmt(remaining)} {isIncome ? "remaining" : "left"}</div>
          </>
        )}

        <div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div className={`h-2 rounded-full ${isIncome ? (isOver ? "bg-green-500" : "bg-blue-400") : (isOver ? "bg-red-500" : "bg-purple-500")}`} style={{ width: `${pct}%` }} />
          </div>
          <div className="text-xs text-gray-500 mt-0.5">{pct.toFixed(0)}% {isIncome ? "received" : "used"}</div>
        </div>
      </div>

      {/* Strategy selector */}
      <div
        className="px-4 pb-2 flex flex-wrap items-center gap-2 text-xs border-t border-purple-200"
        onClick={(e) => e.stopPropagation()}
      >
        <span className="text-gray-500 font-medium">Effective amount strategy:</span>
        {STRATEGIES.map((s) => (
          <button
            key={s}
            onClick={() => handleStrategyChange(s)}
            className={`px-2 py-0.5 rounded border text-xs ${
              strategy === s
                ? "bg-purple-600 text-white border-purple-600"
                : "bg-white text-gray-600 border-gray-300 hover:border-purple-400"
            }`}
          >
            {STRATEGY_LABELS[s]}
          </button>
        ))}
        {strategy === "MANUAL" && (
          <input
            type="number"
            className="border rounded px-2 py-0.5 w-28 text-xs"
            placeholder="Enter amount"
            value={manualAmt}
            onChange={(e) => handleManualAmt(e.target.value)}
          />
        )}
        <span className="ml-auto text-gray-400 italic">
          {strategy === "HIGHER_OF" && `max(${fmt(ruleAmt)}, ${fmt(recurringTotal)}) = ${fmt(effectiveAmt)}`}
          {strategy === "AVERAGE" && `rule avg = ${fmt(effectiveAmt)}`}
          {strategy === "SUM" && `${fmt(ruleAmt)} + ${fmt(recurringTotal)} = ${fmt(effectiveAmt)}`}
          {strategy === "MANUAL" && `manual = ${fmt(effectiveAmt)}`}
        </span>
      </div>

      {/* Expanded linked transactions */}
      {expanded && (
        <div className="px-4 pb-3 border-t border-purple-100">
          {loadingRow && <div className="text-xs text-gray-400 py-2">Loading...</div>}
          {(linkedRule || Object.keys(linkedRecurringMap).length > 0) && (
            <div className="mt-2 space-y-3">
              {/* Rule mapped actuals */}
              <div>
                <div className="text-xs font-semibold text-blue-700 mb-1">Rule Prediction — Mapped Transactions</div>
                {linkedRule?.actualTransactions?.length > 0 ? (
                  sortByDateDesc(linkedRule.actualTransactions).map((t) => (
                    <TxRow key={t.id} t={t}
                      actionLabel={isPast ? "Unmap" : undefined}
                      actionClass="text-red-500 hover:underline"
                      onAction={isPast ? () => handleUnmap(rulePred.id, t.id) : undefined}
                    />
                  ))
                ) : (
                  <div className="text-xs text-gray-400">No transactions mapped.</div>
                )}
              </div>

              {/* Per-recurring mapped actuals */}
              {recurringList.map((recPred) => (
                <div key={recPred.id}>
                  <div className="text-xs font-semibold text-amber-700 mb-1">
                    {recPred.recurringTransactionDescription || "Recurring Obligation"} — Mapped Transactions
                    <span className="ml-1 font-normal text-gray-400">({fmt(recPred.predictedAmount)})</span>
                  </div>
                  {linkedRecurringMap[recPred.id]?.actualTransactions?.length > 0 ? (
                    sortByDateDesc(linkedRecurringMap[recPred.id].actualTransactions).map((t) => (
                      <TxRow key={t.id} t={t}
                        actionLabel={isPast ? "Unmap" : undefined}
                        actionClass="text-red-500 hover:underline"
                        onAction={isPast ? () => handleUnmap(recPred.id, t.id) : undefined}
                      />
                    ))
                  ) : (
                    <div className="text-xs text-gray-400">No transactions mapped.</div>
                  )}
                </div>
              ))}

              {/* Map panel */}
              <div>
                  {!showMapPanel ? (
                    <div className="flex flex-wrap gap-3">
                      <button
                        onClick={(e) => { e.stopPropagation(); setMapTarget(rulePred.id); setShowMapPanel(true); }}
                        className="text-xs text-blue-600 hover:underline"
                      >
                        Map to Rule Prediction
                      </button>
                      {recurringList.map((p) => (
                        <button
                          key={p.id}
                          onClick={(e) => { e.stopPropagation(); setMapTarget(p.id); setShowMapPanel(true); }}
                          className="text-xs text-amber-600 hover:underline"
                        >
                          Map to {p.recurringTransactionDescription || "Recurring"}
                        </button>
                      ))}
                    </div>
                  ) : (
                    <div className="mt-2">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-xs font-medium text-gray-600">
                          Mapping to: <span className={mapTarget === rulePred.id ? "text-blue-700" : "text-amber-700"}>
                            {mapTarget === rulePred.id
                              ? "Rule Prediction"
                              : recurringList.find((p) => p.id === mapTarget)?.recurringTransactionDescription || "Recurring"}
                          </span>
                        </span>
                        <button onClick={(e) => { e.stopPropagation(); setShowMapPanel(false); setTxSearch(""); }} className="text-xs text-gray-400 hover:text-gray-600">✕ Done</button>
                      </div>
                      <input
                        className="w-full border px-2 py-1 rounded text-xs mb-2"
                        placeholder="Search transactions..."
                        value={txSearch}
                        onChange={(e) => setTxSearch(e.target.value)}
                        onClick={(e) => e.stopPropagation()}
                        autoFocus
                      />
                      <div className="max-h-48 overflow-y-auto border rounded">
                        {sortByDateDesc(filteredTx).slice(0, 50).map((t) => (
                          <TxRow key={t.id} t={t}
                            hoverClass="hover:bg-purple-50"
                            onAction={() => handleMap(mapTarget, t.id)}
                          />
                        ))}
                        {filteredTx.length === 0 && (
                          <div className="text-center text-gray-400 py-4 text-xs">No transactions found</div>
                        )}
                      </div>
                    </div>
                  )}
                </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── Single prediction row (rule-only or recurring-only) ───────────────────────

function PredictionRow({ p, onUpdatePrediction, transactions, isIncome, globalMappedIds }) {
  const [expanded, setExpanded] = useState(false);
  const [linkedData, setLinkedData] = useState(null);
  const [loadingRow, setLoadingRow] = useState(false);
  const [showMapPanel, setShowMapPanel] = useState(false);
  const [txSearch, setTxSearch] = useState("");

  const loadLinked = async () => {
    if (linkedData) return;
    setLoadingRow(true);
    try {
      const res = await api.get(`/predicted-transactions/${p.id}/linked-transactions`);
      setLinkedData(res.data);
    } catch (e) {
      console.error("Failed to load linked transactions", e);
    } finally {
      setLoadingRow(false);
    }
  };

  const toggleExpand = () => {
    if (!expanded) loadLinked();
    setExpanded(!expanded);
    setShowMapPanel(false);
  };

  const remaining = parseFloat(p.remainingAmount ?? p.predictedAmount);
  const actual = parseFloat(p.actualSpent ?? 0);
  const predicted = parseFloat(p.predictedAmount ?? 0);
  const pct = predicted > 0 ? Math.min(100, (actual / predicted) * 100) : 0;
  const isOver = actual > predicted;

  const mappedIds = new Set((linkedData?.actualTransactions || []).map((m) => m.id));
  const filteredTx = transactions.filter((t) => {
    if (mappedIds.has(t.id)) return false;
    if (globalMappedIds?.has(t.id)) return false;
    return !txSearch ||
      t.description?.toLowerCase().includes(txSearch.toLowerCase()) ||
      t.categoryName?.toLowerCase().includes(txSearch.toLowerCase());
  });

  const handleMapActual = async (txId) => {
    try {
      const mapRes = await api.post(`/predicted-transactions/${p.id}/map-actual?transactionId=${txId}`);
      onUpdatePrediction(mapRes.data);
      // Reload linked data in-place — keep row expanded and map panel open
      const res = await api.get(`/predicted-transactions/${p.id}/linked-transactions`);
      setLinkedData(res.data);
    } catch (e) { console.error("Failed to map transaction", e); }
  };

  const handleUnmap = async (txId) => {
    try {
      const mapRes = await api.post(`/predicted-transactions/${p.id}/unmap-actual?transactionId=${txId}`);
      onUpdatePrediction(mapRes.data);
      const res = await api.get(`/predicted-transactions/${p.id}/linked-transactions`);
      setLinkedData(res.data);
    } catch (e) { console.error("Failed to unmap transaction", e); }
  };

  const isRecurring = !!p.recurringTransactionId;
  const rowBg = isRecurring ? "bg-amber-50" : "bg-blue-50";
  const badge = isRecurring
    ? <span className="ml-1 text-xs bg-amber-200 text-amber-800 px-1.5 py-0.5 rounded">Recurring</span>
    : <span className="ml-1 text-xs bg-blue-200 text-blue-800 px-1.5 py-0.5 rounded">Rule</span>;

  const isPast = isPastOrCurrentMonth(p.predictionMonth);

  return (
    <div className={`rounded border border-gray-200 mb-2 ${rowBg}`}>
      <div
        className="grid grid-cols-1 md:grid-cols-6 gap-3 py-3 px-4 items-center text-sm cursor-pointer"
        onClick={toggleExpand}
      >
        <div className="font-medium text-gray-800 md:col-span-2">
          {p.categoryName}
          {badge}
          {p.recurringTransactionDescription && (
            <div className="text-xs text-gray-500 mt-0.5">{p.recurringTransactionDescription}</div>
          )}
        </div>
        <div className="text-gray-700">{fmt(p.predictedAmount)}</div>
        <div className={`font-medium ${isIncome ? (isOver ? "text-green-700" : "text-gray-700") : (isOver ? "text-red-600" : "text-green-700")}`}>
          {fmt(p.actualSpent)} {isIncome ? "received" : "spent"}
        </div>
        <div className="text-gray-600">{fmt(remaining)} {isIncome ? "remaining" : "left"}</div>
        <div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div className={`h-2 rounded-full ${isIncome ? (isOver ? "bg-green-500" : "bg-blue-400") : (isOver ? "bg-red-500" : "bg-green-500")}`} style={{ width: `${pct}%` }} />
          </div>
          <div className="text-xs text-gray-500 mt-0.5">{pct.toFixed(0)}% {isIncome ? "received" : "used"}</div>
        </div>
      </div>

      {expanded && (
        <div className="px-4 pb-3 border-t border-gray-100">
          {loadingRow && <div className="text-xs text-gray-400 py-2">Loading...</div>}
          {linkedData && (
            <div className="space-y-3 mt-2">
              {linkedData.actualTransactions?.length > 0 ? (
                <div>
                  <div className="text-xs font-semibold text-gray-600 mb-1">Mapped Actual Transactions</div>
                  {sortByDateDesc(linkedData.actualTransactions).map((t) => (
                    <TxRow key={t.id} t={t}
                      actionLabel={isPast ? "Unmap" : undefined}
                      actionClass="text-red-500 hover:underline"
                      onAction={isPast ? () => handleUnmap(t.id) : undefined}
                    />
                  ))}
                </div>
              ) : (
                <div className="text-xs text-gray-400">No transactions mapped yet.</div>
              )}
              <div>
                {!showMapPanel ? (
                  <button
                    onClick={(e) => { e.stopPropagation(); setShowMapPanel(true); }}
                    className="text-xs text-blue-600 hover:underline"
                  >
                    Map to Actual Transaction
                  </button>
                ) : (
                  <div className="mt-2">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-semibold text-gray-600">Select transaction to map</span>
                      <button onClick={(e) => { e.stopPropagation(); setShowMapPanel(false); setTxSearch(""); }} className="text-xs text-gray-400 hover:text-gray-600">✕ Done</button>
                    </div>
                    <input
                      className="w-full border px-2 py-1 rounded text-xs mb-2"
                      placeholder="Search transactions..."
                      value={txSearch}
                      onChange={(e) => setTxSearch(e.target.value)}
                      onClick={(e) => e.stopPropagation()}
                      autoFocus
                    />
                    <div className="max-h-48 overflow-y-auto border rounded">
                      {sortByDateDesc(filteredTx).slice(0, 50).map((t) => (
                        <TxRow key={t.id} t={t}
                          hoverClass="hover:bg-blue-50"
                          onAction={() => handleMapActual(t.id)}
                        />
                      ))}
                      {filteredTx.length === 0 && (
                        <div className="text-center text-gray-400 py-4 text-xs">No transactions found</div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ── Tree helpers ──────────────────────────────────────────────────────────────

function effectiveForGroup(group, strategies) {
  if (!group) return 0;
  const ruleAmt = parseFloat(group.rule?.predictedAmount ?? 0);
  const recurringTotal = (group.recurringList || []).reduce((s, p) => s + parseFloat(p.predictedAmount ?? 0), 0);
  if (group.rule && group.recurringList?.length > 0) {
    const stored = strategies[group.rule.categoryId] || getStoredStrategy(group.rule.categoryId);
    return resolveEffectiveAmount(ruleAmt, recurringTotal, stored.strategy, stored.manualAmt);
  }
  if (group.rule) return ruleAmt;
  return recurringTotal;
}

function actualForGroup(group) {
  if (!group) return 0;
  const ra = parseFloat(group.rule?.actualSpent ?? 0);
  const ca = (group.recurringList || []).reduce((s, p) => s + parseFloat(p.actualSpent ?? 0), 0);
  return ra + ca;
}

function subtreeTotals(node, byCategory, strategies) {
  let predicted = 0;
  let actual = 0;
  const group = byCategory[node.id];
  if (group) {
    predicted += effectiveForGroup(group, strategies);
    actual += actualForGroup(group);
  }
  for (const child of (node.children || [])) {
    const sub = subtreeTotals(child, byCategory, strategies);
    predicted += sub.predicted;
    actual += sub.actual;
  }
  return { predicted, actual };
}

// Returns income and expense totals separately.
// When a node has incomeCategory=true, its entire subtotal goes to income.
// Otherwise, recurse into children so that income sub-trees nested under
// expense parents (e.g. Earning inside Home) are still correctly bucketed.
function subtreeTotalsSplitNode(node, byCategory, strategies) {
  let incomePredicted = 0, incomeActual = 0;
  let expensePredicted = 0, expenseActual = 0;

  if (node.incomeCategory) {
    const { predicted, actual } = subtreeTotals(node, byCategory, strategies);
    incomePredicted += predicted;
    incomeActual += actual;
  } else {
    // Own prediction (if any) is an expense
    const group = byCategory[node.id];
    if (group) {
      expensePredicted += effectiveForGroup(group, strategies);
      expenseActual += actualForGroup(group);
    }
    // Recurse into children — each child decides for itself
    for (const child of (node.children || [])) {
      const sub = subtreeTotalsSplitNode(child, byCategory, strategies);
      incomePredicted += sub.incomePredicted;
      incomeActual += sub.incomeActual;
      expensePredicted += sub.expensePredicted;
      expenseActual += sub.expenseActual;
    }
  }
  return { incomePredicted, incomeActual, expensePredicted, expenseActual };
}

function subtreeTotalsSplit(nodes, byCategory, strategies) {
  let incomePredicted = 0, incomeActual = 0;
  let expensePredicted = 0, expenseActual = 0;
  for (const node of nodes) {
    const sub = subtreeTotalsSplitNode(node, byCategory, strategies);
    incomePredicted += sub.incomePredicted;
    incomeActual += sub.incomeActual;
    expensePredicted += sub.expensePredicted;
    expenseActual += sub.expenseActual;
  }
  return { incomePredicted, incomeActual, expensePredicted, expenseActual };
}

function subtreeHasPredictions(node, byCategory) {
  if (byCategory[node.id]) return true;
  return (node.children || []).some((c) => subtreeHasPredictions(c, byCategory));
}

// ── CategoryTreeNode ──────────────────────────────────────────────────────────

function CategoryTreeNode({
  node, byCategory, strategies, onStrategyChange,
  onUpdatePrediction, transactions, depth, allCategories, inheritedIncome, globalMappedIds,
}) {
  const [expanded, setExpanded] = useState(false);

  const isIncome = node.incomeCategory || inheritedIncome || false;

  const group = byCategory[node.id];
  const childrenWithPredictions = (node.children || []).filter((c) => subtreeHasPredictions(c, byCategory));
  const childrenWithoutPredictions = (node.children || []).filter((c) => !subtreeHasPredictions(c, byCategory));
  const hasChildren = childrenWithPredictions.length > 0;
  const { predicted: subPredicted, actual: subActual } = subtreeTotals(node, byCategory, strategies);
  const isParentRollup = !group && hasChildren;

  if (!group && !hasChildren) return null;

  const indent = depth * 16;
  const pct = subPredicted > 0 ? Math.min(100, (subActual / subPredicted) * 100) : 0;
  const isOver = subActual > subPredicted;

  const isMerged = group?.rule && group?.recurringList?.length > 0;

  return (
    <div className="mb-1">
      {/* Parent rollup row */}
      {isParentRollup && (
        <div
          className="flex items-center gap-2 px-3 py-2 bg-gray-100 rounded cursor-pointer hover:bg-gray-200 select-none"
          style={{ marginLeft: indent }}
          onClick={() => setExpanded(!expanded)}
        >
          <span className="text-gray-400 w-4 flex-shrink-0">
            {expanded ? <ChevronDownIcon className="h-4 w-4" /> : <ChevronRightIcon className="h-4 w-4" />}
          </span>
          <span className="font-semibold text-gray-700 flex-1 text-sm">{node.name}</span>
          <span className="text-xs text-gray-500 mr-4">{childrenWithPredictions.length} sub-categor{childrenWithPredictions.length === 1 ? "y" : "ies"}</span>
          <span className="text-sm font-medium text-gray-700 w-24 text-right">{fmt(subPredicted)}</span>
          {subActual > 0 && (
            <span className={`text-sm font-medium w-24 text-right ${isIncome ? (isOver ? "text-green-700" : "text-gray-700") : (isOver ? "text-red-600" : "text-green-700")}`}>
              {fmt(subActual)} {isIncome ? "received" : "spent"}
            </span>
          )}
          <div className="w-20 ml-2">
            <div className="w-full bg-gray-200 rounded-full h-1.5">
              <div className={`h-1.5 rounded-full ${isIncome ? (isOver ? "bg-green-400" : "bg-blue-400") : (isOver ? "bg-red-400" : "bg-blue-400")}`} style={{ width: `${pct}%` }} />
            </div>
          </div>
        </div>
      )}

      {/* Leaf prediction row */}
      {group && (
        <div style={{ marginLeft: indent }}>
          {isMerged ? (
            <MergedPredictionRow
              rulePred={group.rule}
              recurringList={group.recurringList}
              onUpdatePrediction={onUpdatePrediction}
              onStrategyChange={onStrategyChange}
              transactions={transactions}
              isIncome={isIncome}
              globalMappedIds={globalMappedIds}
            />
          ) : (
            <PredictionRow
              p={group.rule || group.recurringList[0]}
              onUpdatePrediction={onUpdatePrediction}
              transactions={transactions}
              isIncome={isIncome}
              globalMappedIds={globalMappedIds}
            />
          )}
          {hasChildren && (
            <div
              className="flex items-center gap-1 text-xs text-gray-400 cursor-pointer hover:text-gray-600 px-3 py-1"
              style={{ marginLeft: 8 }}
              onClick={() => setExpanded(!expanded)}
            >
              {expanded ? <ChevronDownIcon className="h-3 w-3" /> : <ChevronRightIcon className="h-3 w-3" />}
              {expanded ? "Hide" : "Show"} sub-category breakdown
            </div>
          )}
        </div>
      )}

      {/* Children with predictions */}
      {expanded && childrenWithPredictions.map((child) => (
        <CategoryTreeNode
          key={child.id}
          node={child}
          byCategory={byCategory}
          strategies={strategies}
          onStrategyChange={onStrategyChange}
          onUpdatePrediction={onUpdatePrediction}
          transactions={transactions}
          depth={depth + 1}
          allCategories={allCategories}
          inheritedIncome={isIncome}
          globalMappedIds={globalMappedIds}
        />
      ))}

      {/* Layer 2: Missing coverage chips — shown when parent is expanded */}
      {expanded && childrenWithoutPredictions.length > 0 && (
        <div className="flex flex-wrap gap-2 mt-1 mb-2" style={{ marginLeft: indent + 16 + 8 }}>
          {childrenWithoutPredictions.map((child) => (
            <span
              key={child.id}
              className="text-xs text-gray-400 bg-gray-50 border border-dashed border-gray-300 rounded px-2 py-0.5"
              title="No forecast rule or recurring obligation for this category"
            >
              {child.name} — no forecast
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Main Forecast component ───────────────────────────────────────────────────

export default function Forecast() {
  const navigate = useNavigate();
  const location = useLocation();
  const [month, setMonth] = useState(() => location.state?.month || currentYearMonth());
  const [notice, setNotice] = useState(() => location.state?.notice || null);
  const [predictions, setPredictions] = useState([]);
  const [categories, setCategories] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [globalMappedIds, setGlobalMappedIds] = useState(new Set());
  const [loading, setLoading] = useState(false);
  const [strategies, setStrategies] = useState(() => {
    try { return JSON.parse(localStorage.getItem("forecastStrategies") || "{}"); }
    catch { return {}; }
  });

  const handleStrategyChange = useCallback((categoryId, strategy, manualAmt) => {
    setStrategies((prev) => ({ ...prev, [categoryId]: { strategy, manualAmt } }));
  }, []);

  NProgress.configure({ showSpinner: false });

  const loadData = useCallback(async () => {
    setLoading(true);
    NProgress.start();
    try {
      const [predRes, catRes] = await Promise.all([
        api.get(`/predicted-transactions?month=${month}`),
        api.get("/categories"),
      ]);
      setPredictions(predRes.data);
      setCategories(catRes.data);
    } catch (error) {
      if (error.response?.status === 401) {
        localStorage.removeItem("authToken");
        navigate("/");
      } else {
        console.error("Failed to load forecast data", error);
      }
    } finally {
      NProgress.done();
      setLoading(false);
    }
  }, [month, navigate]);

  const loadTransactions = useCallback(async () => {
    if (!isPastOrCurrentMonth(month)) return;
    try {
      const [y, m] = month.split("-").map(Number);
      const lastDay = new Date(y, m, 0).getDate();
      const start = `${month}-01`;
      const end = `${month}-${String(lastDay).padStart(2, "0")}`;
      const [txRes, mappedRes] = await Promise.all([
        api.get(`/transactions?startDate=${start}&endDate=${end}&page=0&size=200`),
        api.get(`/predicted-transactions/mapped-transaction-ids?month=${month}`),
      ]);
      setTransactions(txRes.data?.content ?? txRes.data ?? []);
      setGlobalMappedIds(new Set(mappedRes.data));
    } catch (e) {
      console.error("Failed to load transactions", e);
    }
  }, [month]);

  useEffect(() => {
    loadData();
    loadTransactions();
  }, [loadData, loadTransactions]);

  const updatePrediction = useCallback((updated) => {
    setPredictions((prev) => prev.map((p) => p.id === updated.id ? updated : p));
    // Refresh global mapped IDs so other categories' panels reflect the new mapping
    api.get(`/predicted-transactions/mapped-transaction-ids?month=${month}`)
      .then((res) => setGlobalMappedIds(new Set(res.data)))
      .catch(() => {});
  }, [month]);

  // Build category prediction groups — recurringList is an array (supports multiple per category)
  const byCategory = {};
  for (const p of predictions) {
    const cid = p.categoryId;
    if (!byCategory[cid]) byCategory[cid] = { rule: null, recurringList: [] };
    if (p.recurringTransactionId) byCategory[cid].recurringList.push(p);
    else byCategory[cid].rule = p;
  }

  const categoryTree = buildTree(categories);
  const activeRoots = categoryTree.filter((node) => subtreeHasPredictions(node, byCategory));

  const totalPredicted = activeRoots.reduce((s, node) => s + subtreeTotals(node, byCategory, strategies).predicted, 0);
  const totalActual = activeRoots.reduce((s, node) => s + subtreeTotals(node, byCategory, strategies).actual, 0);
  const { incomePredicted, incomeActual, expensePredicted, expenseActual } = subtreeTotalsSplit(activeRoots, byCategory, strategies);
  const mergedCount = Object.values(byCategory).filter((g) => g.rule && g.recurringList?.length > 0).length;
  const isPast = isPastOrCurrentMonth(month);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-800">Forecast</h1>
        <div className="flex items-center gap-3">
          <button onClick={() => setMonth(addMonths(month, -1))} className="p-1 rounded hover:bg-gray-100 border border-gray-200">
            <ChevronLeftIcon className="h-5 w-5 text-gray-600" />
          </button>
          <span className="text-lg font-semibold text-gray-700 min-w-[160px] text-center">{monthLabel(month)}</span>
          <button onClick={() => setMonth(addMonths(month, 1))} className="p-1 rounded hover:bg-gray-100 border border-gray-200">
            <ChevronRightIcon className="h-5 w-5 text-gray-600" />
          </button>
          <button onClick={() => setMonth(currentYearMonth())} className="text-sm text-blue-600 hover:underline ml-2">
            This Month
          </button>
        </div>
      </div>

      {notice && (
        <div className="bg-green-50 border border-green-200 rounded-md px-4 py-2 text-sm text-green-800 flex justify-between items-center">
          <span>{notice}</span>
          <button onClick={() => setNotice(null)} className="ml-4 text-green-600 hover:text-green-800 font-bold">✕</button>
        </div>
      )}

      {predictions.length > 0 && (
        <div className="bg-white border border-gray-200 rounded-md px-5 py-3 flex flex-wrap gap-8 text-sm shadow-sm">
          <div>
            <div className="text-gray-500">Predicted Income</div>
            <div className="text-lg font-bold text-green-700">{fmt(incomePredicted)}</div>
          </div>
          <div>
            <div className="text-gray-500">Predicted Expenses</div>
            <div className="text-lg font-bold text-red-600">{fmt(expensePredicted)}</div>
          </div>
          <div>
            <div className="text-gray-500">Net Predicted</div>
            <div className={`text-lg font-bold ${incomePredicted - expensePredicted >= 0 ? "text-green-700" : "text-red-600"}`}>
              {fmt(incomePredicted - expensePredicted)}
            </div>
          </div>
          {isPast && (
            <>
              <div>
                <div className="text-gray-500">Actual Income</div>
                <div className="text-lg font-bold text-green-700">{fmt(incomeActual)}</div>
              </div>
              <div>
                <div className="text-gray-500">Actual Expenses</div>
                <div className="text-lg font-bold text-red-600">{fmt(expenseActual)}</div>
              </div>
              <div>
                <div className="text-gray-500">Net Actual</div>
                <div className={`text-lg font-bold ${incomeActual - expenseActual >= 0 ? "text-green-700" : "text-red-600"}`}>
                  {fmt(incomeActual - expenseActual)}
                </div>
              </div>
            </>
          )}
          {mergedCount > 0 && (
            <div className="text-xs text-purple-700 self-center bg-purple-50 border border-purple-200 rounded px-2 py-1">
              {mergedCount} merged {mergedCount > 1 ? "categories" : "category"} — rule + recurring
            </div>
          )}
          <div className="ml-auto text-gray-400 text-xs self-end">
            {isPast ? "Past / Current month — actuals tracked" : "Future month — prediction only"}
          </div>
        </div>
      )}

      {predictions.length === 0 && !loading && (
        <div className="bg-white border border-gray-200 rounded-md px-5 py-10 text-center text-gray-500 shadow-sm">
          No predictions for {monthLabel(month)}.
          <div className="mt-2 text-sm">
            Go to <a href="/prediction-rules" className="text-blue-600 hover:underline">Prediction Rules</a> and generate predictions for this month.
          </div>
        </div>
      )}

      {activeRoots.length > 0 && (
        <div className="space-y-1">
          {activeRoots.map((node) => (
            <CategoryTreeNode
              key={node.id}
              node={node}
              byCategory={byCategory}
              strategies={strategies}
              onStrategyChange={handleStrategyChange}
              onUpdatePrediction={updatePrediction}
              transactions={transactions}
              depth={0}
              allCategories={categories}
              globalMappedIds={globalMappedIds}
            />
          ))}
        </div>
      )}

      {loading && (
        <div className="fixed inset-0 bg-white bg-opacity-40 z-50 flex items-center justify-center">
          <div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-500 h-10 w-10 animate-spin"></div>
        </div>
      )}
    </div>
  );
}
