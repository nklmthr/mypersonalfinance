import React, { useEffect, useState } from "react";
import axios from "axios";

export default function Transactions() {
  const [transactions, setTransactions] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [categories, setCategories] = useState([]);

  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const fetchData = async () => {
    const [txRes, accRes, catRes] = await Promise.all([
      axios.get(`/api/transactions?page=${page}&size=10`),
      axios.get("/api/accounts"),
      axios.get("/api/categories/flat"),
    ]);
    setTransactions(txRes.data.content);
    setTotalPages(txRes.data.totalPages);
    setAccounts(accRes.data);
    setCategories(catRes.data);
  };

  useEffect(() => {
    fetchData();
  }, [page]);

  const saveTx = async (tx, method, url) => {
    const payload = {
      description: tx.description,
      explanation: tx.explanation || "",
      amount: tx.amount,
      date: tx.date,
      type: tx.type,
      account: { id: tx.accountId },
      category: tx.categoryId ? { id: tx.categoryId } : null,
      parent: tx.parentId ? { id: tx.parentId } : null,
    };
    await axios[method](url, payload);
    fetchData();
  };

  const deleteTx = async (id) => {
    if (!window.confirm("Delete this transaction?")) return;
    await axios.delete(`/api/transactions/${id}`);
    fetchData();
  };

  const changePage = (delta) => {
    setPage((prev) => Math.max(0, Math.min(prev + delta, totalPages - 1)));
  };

  return (
    <div className="p-4 space-y-4">
      <div className="flex justify-between items-center">
        <h2 className="text-2xl font-semibold">💸 Transactions</h2>
        <button
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
          onClick={() => alert("Add Transaction modal here")}
        >
          + Add Transaction
        </button>
      </div>

      {/* Pagination Top */}
      <div className="flex justify-between items-center mb-2">
        <button
          onClick={() => changePage(-1)}
          disabled={page === 0}
          className="px-4 py-1 border rounded bg-gray-100 hover:bg-gray-200 disabled:opacity-50"
        >
          Prev
        </button>
        <div>
          Page {page + 1} of {totalPages}
        </div>
        <button
          onClick={() => changePage(1)}
          disabled={page >= totalPages - 1}
          className="px-4 py-1 border rounded bg-gray-100 hover:bg-gray-200 disabled:opacity-50"
        >
          Next
        </button>
      </div>

      {/* Transaction List */}
      <div className="space-y-2">
        {transactions.map((tx) => (
          <div
            key={tx.id}
            className="grid grid-cols-1 sm:grid-cols-[2fr_1fr_1.5fr_1fr_max-content] gap-2 py-2 px-3 bg-white rounded border border-gray-200 items-center"
          >
            <div>
              <div className="truncate">{tx.description}</div>
              <div className="text-xs text-gray-500">{tx.explanation}</div>
            </div>
            <div className="text-gray-700">
              ₹{tx.amount} <span className="uppercase ml-2">{tx.type}</span>
              <br />
              <span className="text-sm">{tx.account?.name}</span>
            </div>
            <select
              className="w-full border rounded px-2 py-1 text-sm"
              value={tx.category?.id || ""}
              onChange={(e) => {
                saveTx({ ...tx, accountId: tx.account?.id, categoryId: e.target.value }, "put", `/api/transactions/${tx.id}`);
              }}
            >
              <option value="">— Category —</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            <div className="text-sm text-gray-500">
              {tx.date.replace("T", " ").slice(0, 19)}
            </div>
            <div className="flex items-center space-x-2 text-sm">
              <button className="text-purple-600 hover:underline" onClick={() => alert("Split Modal")}>Split</button>
              <button className="text-red-600 hover:underline" onClick={() => deleteTx(tx.id)}>Delete</button>
              <button className="text-blue-600 hover:underline" onClick={() => alert("Update Modal")}>Update</button>
            </div>
          </div>
        ))}
      </div>

      {/* Pagination Bottom */}
      <div className="flex justify-between items-center mt-4">
        <button
          onClick={() => changePage(-1)}
          disabled={page === 0}
          className="px-4 py-1 border rounded bg-gray-100 hover:bg-gray-200 disabled:opacity-50"
        >
          Prev
        </button>
        <div>
          Page {page + 1} of {totalPages}
        </div>
        <button
          onClick={() => changePage(1)}
          disabled={page >= totalPages - 1}
          className="px-4 py-1 border rounded bg-gray-100 hover:bg-gray-200 disabled:opacity-50"
        >
          Next
        </button>
      </div>
    </div>
  );
}
