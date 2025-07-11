import React, { useEffect, useState } from "react";
import axios from "axios";

function TransactionForm({ transaction, setTransaction, onCancel, onSubmit, accounts, categories, mode }) {
  return (
    <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
      <form
        onSubmit={(e) => { e.preventDefault(); onSubmit(); }}
        className="bg-white p-6 rounded shadow-lg w-full max-w-md space-y-4"
      >
        <h3 className="text-lg font-semibold">{mode === "add" ? "Add" : mode === "split" ? "Split" : "Edit"} Transaction</h3>

        <label className="block text-sm">
          Date
          <input type="date" value={transaction.date.slice(0, 10)} required
            onChange={e => setTransaction(tx => ({ ...tx, date: e.target.value }))}
            className="mt-1 block w-full border rounded px-2 py-1"
          />
        </label>

        <label className="block text-sm">
          Description
          <input type="text" value={transaction.description} required
            onChange={e => setTransaction(tx => ({ ...tx, description: e.target.value }))}
            className="mt-1 block w-full border rounded px-2 py-1"
          />
        </label>

        <label className="block text-sm">
          Explanation
          <input type="text" value={transaction.explanation || ""}
            onChange={e => setTransaction(tx => ({ ...tx, explanation: e.target.value }))}
            className="mt-1 block w-full border rounded px-2 py-1"
          />
        </label>

        <label className="block text-sm">
          Amount
          <input type="number" step="0.01" value={transaction.amount} required
            onChange={e => setTransaction(tx => ({ ...tx, amount: e.target.value }))}
            className="mt-1 block w-full border rounded px-2 py-1"
          />
        </label>

        <label className="block text-sm">
          Type
          <select value={transaction.type}
            onChange={e => setTransaction(tx => ({ ...tx, type: e.target.value }))}
            className="mt-1 block w-full border rounded px-2 py-1"
          >
            <option>DEBIT</option>
            <option>CREDIT</option>
          </select>
        </label>

        <label className="block text-sm">
          Account
          <select value={transaction.accountId} required
            onChange={e => setTransaction(tx => ({ ...tx, accountId: e.target.value }))}
            className="mt-1 block w-full border rounded px-2 py-1"
          >
            <option value="">— Select —</option>
            {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </label>

        <label className="block text-sm">
          Category
          <select value={transaction.categoryId}
            onChange={e => setTransaction(tx => ({ ...tx, categoryId: e.target.value }))}
            className="mt-1 block w-full border rounded px-2 py-1"
          >
            <option value="">— None —</option>
            {categories.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>

        <div className="flex justify-end space-x-2">
          <button type="button" onClick={onCancel}>Cancel</button>
          <button type="submit" className="bg-blue-600 text-white px-4 py-1 rounded">
            {mode === "add" ? "Add" : mode === "split" ? "Split" : "Save"}
          </button>
        </div>
      </form>
    </div>
  );
}

export default function Transactions() {
  const [transactions, setTransactions] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [expandedIds, setExpandedIds] = useState(new Set());

  const [editTx, setEditTx] = useState(null);
  const [addTx, setAddTx] = useState(false);
  const [splitTx, setSplitTx] = useState(null);

  const emptyTx = {
    id: null, date: new Date().toISOString(),
    description: "", explanation: "", amount: "",
    type: "DEBIT", accountId: "", categoryId: "", parentId: null,
  };

  const fetchData = async () => {
    const [tRes, aRes, cRes] = await Promise.all([
      axios.get("/api/transactions/root"),
      axios.get("/api/accounts"),
      axios.get("/api/categories/flat"),
    ]);
    setTransactions(tRes.data);
    setAccounts(aRes.data);
    setCategories(cRes.data);
  };

  useEffect(() => { fetchData(); }, []);

  const saveTx = async (tx, method, url) => {
    const payload = {
      description: tx.description,
      explanation: tx.explanation || "",
      amount: tx.amount,
      date: tx.date,
      type: tx.type,
      account: { id: tx.accountId },
      category: tx.categoryId ? { id: tx.categoryId } : null,
      parent: tx.parentId ? { id: tx.parentId } : null
    };
    await axios[method](url, payload);
    fetchData();
  };

  const deleteTx = async id => {
    if (!window.confirm("Delete this transaction?")) return;
    await axios.delete(`/api/transactions/${id}`);
    fetchData();
  };

  const toggleExpand = (id) => {
    setExpandedIds(prev => {
      const newSet = new Set(prev);
      newSet.has(id) ? newSet.delete(id) : newSet.add(id);
      return newSet;
    });
  };

  const renderTree = (txs, level = 0) => txs.map(tx => {
    const isExpanded = expandedIds.has(tx.id);
    const children = tx.children || [];

    return (
      <div key={tx.id} className={`ml-${level * 4} border-l pl-2`}>
        <div className="grid grid-cols-[2fr_1fr_1.5fr_1fr_max-content] items-center gap-2 bg-white border rounded px-3 py-2 my-1">
          <div>
            <div className="flex items-center">
              {children.length > 0 && (
                <button onClick={() => toggleExpand(tx.id)} className="mr-2 text-xs text-gray-400">
                  {isExpanded ? "▼" : "▶"}
                </button>
              )}
              <span className="truncate font-medium">{tx.description}</span>
            </div>
            <div className="text-xs text-gray-500">{tx.explanation}</div>
          </div>
          <div>
            ₹{tx.amount} <span className="ml-2 text-xs uppercase">{tx.type}</span><br />
            <span className="text-sm text-gray-600">{tx.account?.name}</span>
          </div>
          <select
            className="w-full border rounded px-2 py-1 text-sm"
            value={tx.category?.id || ""}
            onChange={(e) =>
              saveTx({ ...tx, categoryId: e.target.value }, "put", `/api/transactions/${tx.id}`)
            }
          >
            <option value="">— Category —</option>
            {categories.map(c => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </select>
          <div className="text-sm text-gray-500">{tx.date.replace("T", " ").slice(0, 19)}</div>
          <div className="flex items-center space-x-2 text-sm">
            <button onClick={() => setSplitTx({ ...emptyTx, parentId: tx.id, accountId: tx.account?.id })} className="text-purple-600 hover:underline">Split</button>
            <button onClick={() => deleteTx(tx.id)} className="text-red-600 hover:underline">Delete</button>
            <button onClick={() => setEditTx({
              ...tx,
              accountId: tx.account?.id || "",
              categoryId: tx.category?.id || "",
              parentId: tx.parent?.id || null
            })} className="text-blue-600 hover:underline">Update</button>
          </div>
        </div>
        {isExpanded && children.length > 0 && renderTree(children, level + 1)}
      </div>
    );
  });

  return (
    <div className="p-4 space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-2xl font-semibold">💸 Transactions</h2>
        <button onClick={() => { setAddTx(true); setEditTx(null); setSplitTx(null); }}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">+ Add Transaction</button>
      </div>

      <div className="space-y-2">
        {transactions.length === 0 ? (
          <div className="text-gray-500">No transactions found.</div>
        ) : (
          renderTree(transactions)
        )}
      </div>

      {addTx && (
        <TransactionForm
          transaction={{ ...emptyTx }}
          setTransaction={() => {}}
          onCancel={() => setAddTx(false)}
          onSubmit={async () => {
            await saveTx(emptyTx, "post", "/api/transactions");
            setAddTx(false);
          }}
          accounts={accounts}
          categories={categories}
          mode="add"
        />
      )}

      {editTx && (
        <TransactionForm
          transaction={editTx}
          setTransaction={setEditTx}
          onCancel={() => setEditTx(null)}
          onSubmit={async () => {
            await saveTx(editTx, "put", `/api/transactions/${editTx.id}`);
            setEditTx(null);
          }}
          accounts={accounts}
          categories={categories}
          mode="edit"
        />
      )}

      {splitTx && (
        <TransactionForm
          transaction={splitTx}
          setTransaction={setSplitTx}
          onCancel={() => setSplitTx(null)}
          onSubmit={async () => {
            await saveTx(splitTx, "post", "/api/transactions");
            setSplitTx(null);
          }}
          accounts={accounts}
          categories={categories}
          mode="split"
        />
      )}
    </div>
  );
}
