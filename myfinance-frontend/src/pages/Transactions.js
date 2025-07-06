import React, { useEffect, useState } from "react";
import axios from "axios";
import TransactionForm from "./TransactionForm";

export default function Transactions() {
  const [transactions, setTransactions] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [expandedIds, setExpandedIds] = useState(new Set());
  const [editTx, setEditTx] = useState(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [showSplitModal, setShowSplitModal] = useState(false);
  const [newTx, setNewTx] = useState({
    description: "",
    amount: "",
    date: new Date().toISOString().slice(0, 10),
    type: "DEBIT",
    accountId: "",
    categoryId: "",
    parentId: null,
    parentName: ""
  });
  const [splitTransaction, setSplitTransaction] = useState({
    parentId: null,
    date: new Date().toISOString().slice(0, 10),
    description: "",
    amount: "",
    type: "DEBIT",
    accountId: "",
    categoryId: "",
    parentName: ""
  });

  const fetchTransactions = async () => {
    try {
      const res = await axios.get("/api/transactions/root");
      setTransactions(res.data);
    } catch (err) {
      console.error("Fetch transactions error:", err);
    }
  };

  const fetchMeta = async () => {
    try {
      const [accRes, catRes] = await Promise.all([
        axios.get("/api/accounts"),
        axios.get("/api/categories"),
      ]);
      setAccounts(accRes.data);
      setCategories(catRes.data);
    } catch (err) {
      console.error("Fetch meta error:", err);
    }
  };

  useEffect(() => {
    fetchMeta();
    fetchTransactions();
  }, []);

  const handleAdd = async () => {
    try {
      const payload = {
        description: newTx.description,
        amount: newTx.amount,
        date: newTx.date,
        type: newTx.type,
        account: { id: newTx.accountId },
        category: newTx.categoryId ? { id: newTx.categoryId } : null,
        parent: newTx.parentId ? { id: newTx.parentId } : null
      };
      await axios.post("/api/transactions", payload);
      setShowAddModal(false);
      setNewTx({ description: "", amount: "", date: new Date().toISOString().slice(0, 10), type: "DEBIT", accountId: "", categoryId: "", parentId: null, parentName: "" });
      fetchTransactions();
    } catch (err) {
      console.error("Add error:", err);
    }
  };

  const handleEdit = async () => {
    try {
      const payload = {
        description: editTx.description,
        amount: editTx.amount,
        date: editTx.date,
        type: editTx.type,
        account: { id: editTx.accountId },
        category: editTx.categoryId ? { id: editTx.categoryId } : null,
        parent: editTx.parentId ? { id: editTx.parentId } : null
      };
      await axios.put(`/api/transactions/${editTx.id}`, payload);
      setEditTx(null);
      fetchTransactions();
    } catch (err) {
      console.error("Edit error:", err);
    }
  };

  const handleSplit = async () => {
    try {
      const payload = {
        description: splitTransaction.description,
        amount: splitTransaction.amount,
        date: splitTransaction.date,
        type: splitTransaction.type,
        parent: splitTransaction.parentId ? { id: splitTransaction.parentId } : null,
        account: { id: splitTransaction.accountId },
        category: splitTransaction.categoryId ? { id: splitTransaction.categoryId } : null
      };
      await axios.post("/api/transactions", payload);
      setShowSplitModal(false);
      fetchTransactions();
    } catch (err) {
      console.error("Split add error:", err);
    }
  };

  const handleDelete = async (id) => {
    if (window.confirm("Are you sure you want to delete this transaction?")) {
      try {
        await axios.delete(`/api/transactions/${id}`);
        fetchTransactions();
      } catch (err) {
        console.error("Delete error:", err);
      }
    }
  };

  const handleCategoryChange = async (txId, newCategoryId) => {
    try {
      const tx = transactions.find(t => t.id === txId);
      await axios.put(`/api/transactions/${txId}`, {
        description: tx.description,
        amount: tx.amount,
        date: tx.date,
        type: tx.type,
        account: { id: tx.account?.id },
        parent: tx.parent ? { id: tx.parent.id } : null,
        category: newCategoryId ? { id: newCategoryId } : null,
      });
      fetchTransactions();
    } catch (err) {
      console.error("Failed to update category:", err);
    }
  };

  const toggleExpand = (id) => {
    setExpandedIds(prev => {
      const newSet = new Set(prev);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      return newSet;
    });
  };

  const renderTransactions = (items, level = 0) =>
    items.map((tx) => {
      const isExpanded = expandedIds.has(tx.id);
      const bgColors = ["bg-yellow-100", "bg-gray-50", "bg-blue-50", "bg-green-50"];
      const rowColor = !isExpanded ? "bg-orange-100" : bgColors[level % bgColors.length];
      const indicator = isExpanded ? "▼" : "▶";

      return (
        <div key={tx.id} className={`ml-${level * 2} border-l-2 pl-2 ${rowColor}`}>
          <div className={`text-sm flex justify-between items-center px-1 py-1 cursor-pointer hover:bg-gray-100`} onClick={() => toggleExpand(tx.id)}>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-gray-400">{indicator}</span>
              <strong>{tx.description}</strong>
              <span className="text-gray-600">| ₹{tx.amount}</span>
              <span className="text-gray-600">| {tx.type}</span>
              <span className="text-gray-600">| {tx.account?.name}</span>
              {tx.account && (
                <select
                  className="text-xs border px-1 py-0.5 rounded"
                  value={tx.category?.id || ""}
                  onClick={(e) => e.stopPropagation()}
                  onChange={(e) => handleCategoryChange(tx.id, e.target.value)}
                >
                  <option value="">-- Category --</option>
                  {categories.map((cat) => (
                    <option key={cat.id} value={cat.id}>
                      {cat.name}
                    </option>
                  ))}
                </select>
              )}
            </div>
            <div className="ml-auto space-x-2 text-xs">
              <span className="text-gray-400">#{tx.id}</span>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setSplitTransaction({
                    parentId: tx.id,
                    parentName: tx.description,
                    date: new Date().toISOString().slice(0, 10),
                    description: "",
                    amount: "",
                    type: "DEBIT",
                    accountId: tx.account?.id || "",
                    categoryId: ""
                  });
                  setShowSplitModal(true);
                }}
                className="text-purple-600 hover:underline"
              >
                Split
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  handleDelete(tx.id);
                }}
                className="text-red-600 hover:underline"
              >
                Delete
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  setEditTx({
                    id: tx.id,
                    description: tx.description,
                    amount: tx.amount,
                    date: tx.date,
                    type: tx.type,
                    accountId: tx.account?.id || "",
                    categoryId: tx.category?.id || "",
                    parentId: tx.parent?.id || null,
                    parentName: tx.parent?.description || ""
                  });
                }}
                className="text-blue-600 hover:underline"
              >
                Update
              </button>
            </div>
          </div>
          {isExpanded && tx.children && tx.children.length > 0 && renderTransactions(tx.children, level + 1)}
        </div>
      );
    });

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold">💸 Transactions</h2>
        <button
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          ➕ Add Transaction
        </button>
      </div>

      <div className="bg-white shadow rounded p-4 text-sm leading-tight">
        {transactions.length === 0 ? (
          <div className="text-gray-500">No transactions yet.</div>
        ) : (
          renderTransactions(transactions)
        )}
      </div>

      {editTx && (
        <TransactionForm
          transaction={editTx}
          setTransaction={setEditTx}
          onCancel={() => setEditTx(null)}
          onSubmit={handleEdit}
          accounts={accounts}
          categories={categories}
          transactions={transactions}
        />
      )}

      {showAddModal && (
        <TransactionForm
          transaction={newTx}
          setTransaction={setNewTx}
          onCancel={() => setShowAddModal(false)}
          onSubmit={handleAdd}
          accounts={accounts}
          categories={categories}
          transactions={transactions}
          mode="add"
        />
      )}

      {showSplitModal && (
        <TransactionForm
          transaction={splitTransaction}
          setTransaction={setSplitTransaction}
          onCancel={() => setShowSplitModal(false)}
          onSubmit={handleSplit}
          accounts={accounts}
          categories={categories}
          transactions={transactions}
          mode="split"
        />
      )}
    </div>
  );
}
