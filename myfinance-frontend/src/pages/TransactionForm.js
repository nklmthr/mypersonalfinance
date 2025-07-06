import React from "react";

export default function TransactionForm({ transaction, setTransaction, onCancel, onSubmit, accounts, categories, transactions, mode = "edit" }) {
  const inputClass = "border px-2 py-1 rounded w-full text-sm";

  return (
    <div className="fixed inset-0 bg-black bg-opacity-30 flex justify-center items-center z-50">
      <div className="bg-white p-6 rounded shadow-md space-y-4 w-80">
        <h3 className="text-lg font-semibold">
          {mode === "add" ? "Add" : mode === "split" ? "Split" : "Edit"} Transaction
        </h3>
        <input
          className={inputClass}
          placeholder="Description"
          value={transaction.description}
          onChange={(e) => setTransaction({ ...transaction, description: e.target.value })}
        />
        <input
          type="number"
          className={inputClass}
          placeholder="Amount"
          value={transaction.amount}
          onChange={(e) => setTransaction({ ...transaction, amount: e.target.value })}
        />
        <input
          type="date"
          className={inputClass}
          value={transaction.date}
          onChange={(e) => setTransaction({ ...transaction, date: e.target.value })}
        />
        <select
          className={inputClass}
          value={transaction.accountId}
          onChange={(e) => setTransaction({ ...transaction, accountId: e.target.value })}
        >
          <option value="">-- Select Account --</option>
          {accounts.map(acc => (
            <option key={acc.id} value={acc.id}>{acc.name}</option>
          ))}
        </select>
        <select
          className={inputClass}
          value={transaction.categoryId}
          onChange={(e) => setTransaction({ ...transaction, categoryId: e.target.value })}
        >
          <option value="">-- Optional Category --</option>
          {categories.map(cat => (
            <option key={cat.id} value={cat.id}>{cat.name}</option>
          ))}
        </select>

        {transaction.parentId && (
          <input
            type="hidden"
            value={transaction.parentId}
            onChange={() => {}}
          />
        )}

        {transaction.parentId && (
          <div className="text-sm text-gray-600">
            Parent: <strong>{transaction.parentName}</strong>
          </div>
        )}

        <div className="flex justify-end space-x-2">
          <button className="bg-gray-300 px-3 py-1 rounded" onClick={onCancel}>Cancel</button>
          <button className="bg-green-600 text-white px-3 py-1 rounded" onClick={onSubmit}>
            {mode === "add" ? "Add" : mode === "split" ? "Split" : "Update"}
          </button>
        </div>
      </div>
    </div>
  );
}
