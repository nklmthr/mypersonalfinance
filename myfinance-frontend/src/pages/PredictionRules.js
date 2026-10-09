import React, { useEffect, useState } from "react";
import api from "../auth/api";
import { useNavigate } from "react-router-dom";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import SearchSelect from "./transactions/components/SearchSelect";
import { buildTree, flattenCategories } from "./transactions/utils/utils";
import { useErrorModal } from "../auth/ErrorModalContext";
const RECURRENCE_TYPES = [
  { value: "MONTHLY", label: "Monthly" },
  { value: "QUARTERLY", label: "Quarterly (Jan/Apr/Jul/Oct)" },
  { value: "HALF_YEARLY", label: "Half-Yearly (Jan/Jul)" },
  { value: "YEARLY", label: "Yearly" },
  { value: "CUSTOM", label: "Custom Months" },
];

const MONTH_NAMES = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

function getMonthName(month) {
  const months = [
    "January","February","March","April","May","June",
    "July","August","September","October","November","December",
  ];
  return months[month - 1] || "";
}

const EMPTY_RECURRING = {
  id: null,
  description: "",
  categoryId: "",
  amount: "",
  transactionType: "DEBIT",
  recurrenceType: "MONTHLY",
  activeMonths: "",
  dayOfMonth: 1,
  startYearMonth: "",
  endYearMonth: "",
  enabled: true,
};

function RecurringForm({ form, setForm, flattened, onSave, onCancel }) {
  const needsMonths = form.recurrenceType === "YEARLY" || form.recurrenceType === "CUSTOM";

  const toggleMonth = (num) => {
    const current = form.activeMonths
      ? form.activeMonths.split(",").map(Number).filter(Boolean)
      : [];
    const updated = current.includes(num)
      ? current.filter((m) => m !== num)
      : [...current, num].sort((a, b) => a - b);
    setForm({ ...form, activeMonths: updated.join(",") });
  };

  const activeSet = new Set(
    form.activeMonths ? form.activeMonths.split(",").map(Number).filter(Boolean) : []
  );

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-start justify-center z-50 p-4 overflow-y-auto">
      <div className="bg-white rounded-lg shadow-xl max-w-lg w-full p-6 my-8">
        <h2 className="text-xl font-semibold text-gray-800 mb-4">
          {form.id ? "Edit Recurring Transaction" : "Add Recurring Transaction"}
        </h2>
        <div className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <input
              className="w-full border px-3 py-2 rounded"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder="e.g. Society Maintenance"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
            <SearchSelect
              options={flattened.map((c) => ({ id: c.id, name: c.name }))}
              value={form.categoryId}
              onChange={(val) => setForm({ ...form, categoryId: val })}
              placeholder="Select Category"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Amount</label>
              <input
                type="number"
                min="0"
                step="0.01"
                className="w-full border px-3 py-2 rounded"
                value={form.amount}
                onChange={(e) => setForm({ ...form, amount: e.target.value })}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
              <select
                className="w-full border px-3 py-2 rounded"
                value={form.transactionType}
                onChange={(e) => setForm({ ...form, transactionType: e.target.value })}
              >
                <option value="DEBIT">Debit (Expense)</option>
                <option value="CREDIT">Credit (Income)</option>
              </select>
            </div>
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Recurrence</label>
            <select
              className="w-full border px-3 py-2 rounded"
              value={form.recurrenceType}
              onChange={(e) => setForm({ ...form, recurrenceType: e.target.value, activeMonths: "" })}
            >
              {RECURRENCE_TYPES.map((r) => (
                <option key={r.value} value={r.value}>{r.label}</option>
              ))}
            </select>
          </div>
          {needsMonths && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Active Months <span className="text-gray-400 text-xs">(select which months)</span>
              </label>
              <div className="flex flex-wrap gap-1">
                {MONTH_NAMES.map((name, i) => {
                  const num = i + 1;
                  return (
                    <button
                      key={num}
                      type="button"
                      onClick={() => toggleMonth(num)}
                      className={`px-2 py-1 rounded text-xs border ${
                        activeSet.has(num)
                          ? "bg-blue-600 text-white border-blue-600"
                          : "bg-white text-gray-700 border-gray-300 hover:bg-gray-50"
                      }`}
                    >
                      {name}
                    </button>
                  );
                })}
              </div>
            </div>
          )}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Day of Month</label>
            <input
              type="number"
              min="1"
              max="28"
              className="w-full border px-3 py-2 rounded"
              value={form.dayOfMonth}
              onChange={(e) => setForm({ ...form, dayOfMonth: parseInt(e.target.value) || 1 })}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Start Month (YYYY-MM)</label>
              <input
                type="month"
                className="w-full border px-3 py-2 rounded"
                value={form.startYearMonth}
                onChange={(e) => setForm({ ...form, startYearMonth: e.target.value })}
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">End Month (optional)</label>
              <input
                type="month"
                className="w-full border px-3 py-2 rounded"
                value={form.endYearMonth}
                onChange={(e) => setForm({ ...form, endYearMonth: e.target.value })}
              />
            </div>
          </div>
          <div className="flex items-center">
            <input
              type="checkbox"
              checked={form.enabled}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              className="mr-2"
            />
            <label className="text-sm text-gray-700">Enabled</label>
          </div>
        </div>
        <div className="flex gap-2 justify-end mt-6">
          <button
            onClick={onCancel}
            className="px-4 py-2 text-sm border border-gray-300 rounded hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            onClick={() => onSave(form)}
            disabled={!form.description || !form.categoryId || !form.amount || !form.startYearMonth}
            className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          >
            Save
          </button>
        </div>
      </div>
    </div>
  );
}

export default function PredictionRules() {
  const navigate = useNavigate();
  const [rules, setRules] = useState([]);
  const [categories, setCategories] = useState([]);
  const [recurringList, setRecurringList] = useState([]);
  const [predByCategory, setPredByCategory] = useState({}); // categoryId → prediction (all types)
  const [loading, setLoading] = useState(false);
  const [editRecurring, setEditRecurring] = useState(null);
  const [deleteRecurringId, setDeleteRecurringId] = useState(null);
  const [overrideEdit, setOverrideEdit] = useState({}); // predId → draft amount string

  const now = new Date();
  const [selectedMonth, setSelectedMonth] = useState(now.getMonth() + 1);
  const [selectedYear, setSelectedYear] = useState(now.getFullYear());

  NProgress.configure({ showSpinner: false });

  const fetchPredictions = async (month, year) => {
    const targetMonth = `${year}-${String(month).padStart(2, "0")}`;
    try {
      const res = await api.get(`/predicted-transactions?month=${targetMonth}`);
      // Index by categoryId — last writer wins (rule takes priority, recurring ignored here)
      const map = {};
      for (const p of res.data) {
        if (!p.recurringTransactionId) map[p.categoryId] = p;
      }
      setPredByCategory(map);
    } catch (err) {
      console.error("Failed to fetch predictions:", err);
    }
  };

  const fetchData = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const [rulesRes, catRes] = await Promise.all([
        api.get("/prediction-rules"),
        api.get("/categories"),
      ]);
      setRules(rulesRes.data);
      setCategories(catRes.data);
    } catch (error) {
      if (error.response?.status === 401) {
        localStorage.removeItem("authToken");
        navigate("/");
      } else {
        console.error("Failed to fetch data:", error);
      }
    }
    try {
      const recurringRes = await api.get("/recurring-transactions");
      setRecurringList(recurringRes.data);
    } catch (error) {
      console.error("Failed to fetch recurring transactions:", error);
    } finally {
      NProgress.done();
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  useEffect(() => {
    fetchPredictions(selectedMonth, selectedYear);
  }, [selectedMonth, selectedYear]);

  const generateAllCategories = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const targetMonth = `${selectedYear}-${String(selectedMonth).padStart(2, "0")}`;
      const res = await api.post(`/prediction-rules/generate-all-categories?targetMonth=${targetMonth}`);
      const count = res.data || `All-category predictions generated for ${getMonthName(selectedMonth)} ${selectedYear}!`;
      NProgress.done();
      setLoading(false);
      navigate("/forecast", { state: { month: targetMonth, notice: count } });
    } catch (err) {
      console.error("Failed to generate all-category predictions:", err);
      NProgress.done();
      setLoading(false);
    }
  };

  const saveRecurring = async (form) => {
    setLoading(true);
    NProgress.start();
    try {
      const payload = {
        description: form.description,
        categoryId: form.categoryId,
        amount: parseFloat(form.amount),
        transactionType: form.transactionType,
        recurrenceType: form.recurrenceType,
        activeMonths: form.activeMonths || null,
        dayOfMonth: form.dayOfMonth,
        startYearMonth: form.startYearMonth,
        endYearMonth: form.endYearMonth || null,
        enabled: form.enabled,
      };
      if (form.id) {
        await api.put(`/recurring-transactions/${form.id}`, payload);
      } else {
        await api.post("/recurring-transactions", payload);
      }
      await fetchData();
      setEditRecurring(null);
    } catch (err) {
      console.error("Failed to save recurring transaction:", err);
    } finally {
      NProgress.done();
      setLoading(false);
    }
  };

  const deleteRecurring = async (id) => {
    setDeleteRecurringId(null);
    setLoading(true);
    NProgress.start();
    try {
      await api.delete(`/recurring-transactions/${id}`);
      await fetchData();
    } catch (err) {
      console.error("Failed to delete recurring transaction:", err);
    } finally {
      NProgress.done();
      setLoading(false);
    }
  };

  const generateRecurring = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const targetMonth = `${selectedYear}-${String(selectedMonth).padStart(2, "0")}`;
      const res = await api.post(`/recurring-transactions/generate?targetMonth=${targetMonth}`);
      const count = res.data || `Recurring predictions generated for ${getMonthName(selectedMonth)} ${selectedYear}!`;
      NProgress.done();
      setLoading(false);
      navigate("/forecast", { state: { month: targetMonth, notice: count } });
    } catch (err) {
      console.error("Failed to generate recurring predictions:", err);
      NProgress.done();
      setLoading(false);
    }
  };

  const treeCategories = buildTree(categories);
  const rootHome = treeCategories.filter((cat) => cat?.name === "Home");
  const limitedTree = rootHome.length > 0 ? rootHome : treeCategories;
  const flattened = flattenCategories(limitedTree);

  // Index rules by categoryId for O(1) lookup in tree rendering
  const ruleByCategory = {};
  for (const rule of rules) {
    ruleByCategory[rule.categoryId] = rule;
  }

  const autoPredictions = Object.values(predByCategory).filter(p => !p.predictionRuleId);

  const deleteAutoPrediction = async (id) => {
    try {
      await api.delete(`/predicted-transactions/${id}`);
      setPredByCategory(prev => {
        const next = { ...prev };
        for (const k of Object.keys(next)) { if (next[k].id === id) delete next[k]; }
        return next;
      });
    } catch (err) {
      console.error("Failed to delete prediction:", err);
    }
  };

  const deleteAllAutoPredictions = async () => {
    if (!window.confirm(`Delete all auto-generated predictions for ${getMonthName(selectedMonth)} ${selectedYear}?`)) return;
    const targetMonth = `${selectedYear}-${String(selectedMonth).padStart(2, "0")}`;
    setLoading(true);
    NProgress.start();
    try {
      await api.delete(`/predicted-transactions/auto-generated?month=${targetMonth}`);
      setPredByCategory(prev => {
        const next = {};
        for (const [k, v] of Object.entries(prev)) { if (v.predictionRuleId) next[k] = v; }
        return next;
      });
    } catch (err) {
      console.error("Failed to delete all auto predictions:", err);
    } finally {
      NProgress.done();
      setLoading(false);
    }
  };

  const handleOverride = async (pred, amount) => {
    try {
      const res = await api.patch(`/predicted-transactions/${pred.id}/override?amount=${amount}`);
      setPredByCategory(prev => ({ ...prev, [pred.categoryId]: res.data }));
      setOverrideEdit(prev => { const n = { ...prev }; delete n[pred.id]; return n; });
    } catch (err) {
      console.error("Failed to override prediction:", err);
    }
  };

  const handleResetOverride = async (pred) => {
    try {
      const res = await api.delete(`/predicted-transactions/${pred.id}/override`);
      setPredByCategory(prev => ({ ...prev, [pred.categoryId]: res.data }));
    } catch (err) {
      console.error("Failed to reset override:", err);
    }
  };

  const recurrenceLabel = (rt) => {
    const type = RECURRENCE_TYPES.find((r) => r.value === rt.recurrenceType);
    if (!type) return rt.recurrenceType;
    if ((rt.recurrenceType === "YEARLY" || rt.recurrenceType === "CUSTOM") && rt.activeMonths) {
      const months = rt.activeMonths.split(",").map(Number).map((m) => MONTH_NAMES[m - 1]).join(", ");
      return `${type.label} (${months})`;
    }
    return type.label;
  };

  const renderCategoryRuleTree = (nodes, depth = 0) => {
    return nodes.map((cat) => {
      const rule = ruleByCategory[cat.id];
      const pred = predByCategory[cat.id]; // prediction for this month (rule or auto)
      const isAuto = pred && !pred.predictionRuleId;
      const hasChildren = cat.children && cat.children.length > 0;
      const hasAnything = rule || pred;

      const isEditing = pred && overrideEdit[pred.id] !== undefined;
      const draftAmt = pred ? (overrideEdit[pred.id] ?? "") : "";

      return (
        <div key={cat.id} style={{ marginLeft: depth * 16 }}>
          <div className={`flex items-center justify-between py-1.5 px-3 rounded text-sm border-l-2 ${rule ? "border-blue-400 bg-blue-50" : pred ? "border-orange-300 bg-orange-50" : "border-gray-200 bg-white hover:bg-gray-50"}`}>
            {/* Left: name + metadata */}
            <div className="flex items-center gap-4 min-w-0 flex-1 mr-4 flex-wrap">
              <span className={`font-medium truncate ${hasAnything ? "text-gray-800" : "text-gray-400"}`}>
                {cat.name}
                {hasChildren && <span className="ml-1 text-xs text-gray-400">({cat.children.length})</span>}
              </span>
              {rule && (
                <>
                  <span className="text-xs text-blue-600 font-medium whitespace-nowrap">Rule</span>
                  <span className="text-gray-500 text-xs whitespace-nowrap">
                    {rule.predictionType === "MONTHLY" ? "Monthly" : `Yearly (${getMonthName(rule.specificMonth)})`}
                  </span>
                  <span className="text-gray-500 text-xs whitespace-nowrap">Lookback: {rule.lookbackMonths}m</span>
                  <span className={`px-2 py-0.5 rounded text-xs whitespace-nowrap ${rule.enabled ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-500"}`}>
                    {rule.enabled ? "Enabled" : "Disabled"}
                  </span>
                </>
              )}
              {isAuto && (
                <span className="text-xs text-orange-600 font-medium whitespace-nowrap">Auto-generated</span>
              )}

              {/* Amount + override UI — shown when a prediction exists for this month */}
              {pred && (
                <div className="flex items-center gap-1.5 flex-wrap">
                  {isEditing ? (
                    <>
                      <span className="text-gray-400 text-xs">₹</span>
                      <input
                        type="number"
                        min="0"
                        step="1"
                        className="border rounded px-1.5 py-0.5 w-28 text-xs"
                        value={draftAmt}
                        autoFocus
                        onChange={(e) => setOverrideEdit(prev => ({ ...prev, [pred.id]: e.target.value }))}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" && draftAmt !== "") handleOverride(pred, draftAmt);
                          if (e.key === "Escape") setOverrideEdit(prev => { const n = { ...prev }; delete n[pred.id]; return n; });
                        }}
                      />
                      <button
                        onClick={() => draftAmt !== "" && handleOverride(pred, draftAmt)}
                        disabled={draftAmt === ""}
                        className="text-xs bg-green-600 text-white px-2 py-0.5 rounded hover:bg-green-700 disabled:opacity-40"
                      >✓</button>
                      <button
                        onClick={() => setOverrideEdit(prev => { const n = { ...prev }; delete n[pred.id]; return n; })}
                        className="text-xs text-gray-400 hover:text-gray-600"
                      >✕</button>
                    </>
                  ) : (
                    <>
                      <span className={`text-xs font-medium whitespace-nowrap ${pred.manualOverride ? "text-amber-700" : "text-gray-600"}`}>
                        ₹{parseFloat(pred.predictedAmount).toLocaleString("en-IN")}
                      </span>
                      {pred.manualOverride && (
                        <>
                          <span className="text-xs bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded whitespace-nowrap">Overridden</span>
                          <span className="text-xs text-gray-400 whitespace-nowrap">
                            (was ₹{parseFloat(pred.manualOverrideOriginalAmount).toLocaleString("en-IN")})
                          </span>
                          <button
                            onClick={() => handleResetOverride(pred)}
                            className="text-xs text-blue-600 hover:underline whitespace-nowrap"
                          >Reset</button>
                        </>
                      )}
                      <button
                        onClick={() => setOverrideEdit(prev => ({ ...prev, [pred.id]: String(parseFloat(pred.predictedAmount).toFixed(0)) }))}
                        className="text-xs text-gray-400 hover:text-amber-600 whitespace-nowrap"
                        title="Override predicted amount for this month"
                      >✎ Override</button>
                    </>
                  )}
                </div>
              )}

              {!hasAnything && <span className="text-gray-300 text-xs italic">no rule</span>}
            </div>
            {/* Right: action buttons */}
            <div className="flex gap-3 shrink-0">
              {isAuto && pred && (
                <button onClick={() => deleteAutoPrediction(pred.id)} className="text-red-600 hover:underline text-xs">Delete</button>
              )}
            </div>
          </div>
          {hasChildren && renderCategoryRuleTree(cat.children, depth + 1)}
        </div>
      );
    });
  };

  return (
    <div className="space-y-6">
      {/* Header with controls */}
      <div className="flex items-center justify-between flex-wrap gap-2">
        <h1 className="text-2xl font-bold text-gray-800">Prediction Rules</h1>
        <div className="flex gap-2 items-center flex-wrap">
          <div className="flex gap-2 items-center bg-gray-50 px-3 py-2 rounded border border-gray-200">
            <label className="text-sm font-medium text-gray-700">Generate for:</label>
            <select
              value={selectedMonth}
              onChange={(e) => setSelectedMonth(parseInt(e.target.value))}
              className="border border-gray-300 px-2 py-1 rounded text-sm"
            >
              {[...Array(12)].map((_, i) => (
                <option key={i + 1} value={i + 1}>{getMonthName(i + 1)}</option>
              ))}
            </select>
            <select
              value={selectedYear}
              onChange={(e) => setSelectedYear(parseInt(e.target.value))}
              className="border border-gray-300 px-2 py-1 rounded text-sm"
            >
              {[...Array(5)].map((_, i) => {
                const year = new Date().getFullYear() + i - 1;
                return <option key={year} value={year}>{year}</option>;
              })}
            </select>
          </div>
          <button
            onClick={generateAllCategories}
            className="bg-indigo-600 text-white px-4 py-2 rounded text-sm shadow hover:bg-indigo-700"
          >
            Generate All Categories
          </button>
          {autoPredictions.length > 0 && (
            <button
              onClick={deleteAllAutoPredictions}
              className="bg-red-500 text-white px-4 py-2 rounded text-sm shadow hover:bg-red-600"
            >
              Delete All Auto ({autoPredictions.length})
            </button>
          )}
        </div>
      </div>

      {/* Prediction Rules */}
      <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
        <div className="space-y-0.5">
          {treeCategories.length > 0
            ? renderCategoryRuleTree(treeCategories)
            : (
              <div className="text-center text-gray-500 py-8">
                No categories found. Add categories first, then create prediction rules.
              </div>
            )
          }
        </div>
      </div>

      {/* Recurring Transactions */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-xl font-semibold text-gray-800">Recurring Transactions</h2>
          <div className="flex gap-2">
            <button
              onClick={generateRecurring}
              className="bg-teal-600 text-white px-4 py-2 rounded text-sm shadow hover:bg-teal-700"
            >
              Generate Recurring
            </button>
            <button
              onClick={() => setEditRecurring({ ...EMPTY_RECURRING })}
              className="bg-green-500 text-white px-4 py-2 rounded text-sm shadow hover:bg-green-600"
            >
              Add Recurring
            </button>
          </div>
        </div>
        <div className="bg-white border border-gray-200 rounded-md p-4 shadow-sm">
          <div className="space-y-2">
            {recurringList.map((rt) => (
              <div
                key={rt.id}
                className="grid grid-cols-1 md:grid-cols-7 gap-3 py-3 px-4 rounded border border-gray-200 items-center text-sm bg-amber-50"
              >
                <div className="font-medium text-gray-800 md:col-span-2">{rt.description}</div>
                <div className="text-gray-600">{rt.categoryName}</div>
                <div className="text-gray-800 font-medium">
                  ₹{parseFloat(rt.amount).toLocaleString("en-IN")}
                </div>
                <div className="text-gray-600 text-xs">{recurrenceLabel(rt)}</div>
                <div>
                  <span className={`inline-block px-2 py-1 rounded text-xs ${rt.enabled ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-600"}`}>
                    {rt.enabled ? "Active" : "Inactive"}
                  </span>
                </div>
                <div className="flex gap-2 justify-end">
                  <button
                    onClick={() => setEditRecurring({ ...rt, amount: rt.amount?.toString() ?? "" })}
                    className="text-blue-600 hover:underline"
                  >
                    Edit
                  </button>
                  <button
                    onClick={() => setDeleteRecurringId(rt.id)}
                    className="text-red-600 hover:underline"
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
            {recurringList.length === 0 && (
              <div className="text-center text-gray-500 py-8">
                No recurring transactions defined. Click "Add Recurring" to create one.
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Recurring Transaction Modal */}
      {editRecurring && (
        <RecurringForm
          form={editRecurring}
          setForm={setEditRecurring}
          flattened={flattened}
          onSave={saveRecurring}
          onCancel={() => setEditRecurring(null)}
        />
      )}

      {/* Delete Recurring Confirmation */}
      {deleteRecurringId && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4 p-6">
            <h2 className="text-xl font-semibold text-gray-900 mb-2">Delete Recurring Transaction</h2>
            <p className="text-gray-600 mb-6">
              Are you sure? This will remove the recurring transaction definition (existing generated predictions are not affected).
            </p>
            <div className="flex gap-3 justify-end">
              <button onClick={() => setDeleteRecurringId(null)} className="px-4 py-2 text-gray-700 bg-gray-100 hover:bg-gray-200 rounded-md">Cancel</button>
              <button onClick={() => deleteRecurring(deleteRecurringId)} className="px-4 py-2 text-white bg-red-600 hover:bg-red-700 rounded-md">Delete</button>
            </div>
          </div>
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
