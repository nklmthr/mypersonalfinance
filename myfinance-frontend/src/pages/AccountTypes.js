import React, { useEffect, useState } from "react";
import api from "./../auth/api";
import NProgress from "nprogress";
import "nprogress/nprogress.css";

const inputClass =
  "w-full border rounded px-3 py-2 bg-blue-50 focus:outline-none focus:ring-2 focus:ring-blue-500";

function AccountTypeForm({ account, setAccount, mode, onCancel, onSubmit }) {
  const [errors, setErrors] = useState({});

  useEffect(() => {
    const handler = (e) => e.key === "Escape" && onCancel();
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onCancel]);

  useEffect(() => {
    setErrors({});
  }, [account?.id, mode]);

  const validateAndSubmit = (e) => {
    e.preventDefault();
    const nextErrors = {};
    const trimmedName = account.name?.trim();
    const trimmedClassification = account.classification?.trim();

    if (!trimmedName) nextErrors.name = "Required";
    if (!trimmedClassification) nextErrors.classification = "Required";

    const balanceValue =
      account.accountTypeBalance === "" || account.accountTypeBalance === null || account.accountTypeBalance === undefined
        ? 0
        : parseFloat(account.accountTypeBalance);

    if (Number.isNaN(balanceValue)) {
      nextErrors.balance = "Invalid number";
    }

    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }

    onSubmit({
      ...account,
      name: trimmedName,
      classification: trimmedClassification,
      description: account.description?.trim() || "",
      accountTypeBalance: balanceValue,
    });
  };

  const valueOrEmpty = (value) => (value === null || value === undefined ? "" : value);

  return (
    <div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <form
        onSubmit={validateAndSubmit}
        className="bg-white p-6 rounded-lg shadow-lg w-full max-w-xl space-y-4"
      >
        <h3 className="text-lg font-semibold text-gray-800">
          {mode === "edit" ? "Edit" : "Add"} Account Type
        </h3>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Name<span className="text-red-600">*</span>
            </label>
            <input
              name="name"
              placeholder="Name"
              className={`${inputClass} ${errors.name ? "border-red-500" : ""}`}
              value={valueOrEmpty(account.name)}
              onChange={(e) => {
                setAccount((prev) => ({ ...prev, name: e.target.value }));
                setErrors((prev) => ({ ...prev, name: undefined }));
              }}
            />
            {errors.name && <p className="text-xs text-red-600 mt-1">{errors.name}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Classification<span className="text-red-600">*</span>
            </label>
            <input
              name="classification"
              placeholder="Classification"
              className={`${inputClass} ${errors.classification ? "border-red-500" : ""}`}
              value={valueOrEmpty(account.classification)}
              onChange={(e) => {
                setAccount((prev) => ({ ...prev, classification: e.target.value }));
                setErrors((prev) => ({ ...prev, classification: undefined }));
              }}
            />
            {errors.classification && (
              <p className="text-xs text-red-600 mt-1">{errors.classification}</p>
            )}
          </div>

          <div className="sm:col-span-2">
            <label className="block text-sm font-medium text-gray-700 mb-1">Description</label>
            <input
              name="description"
              placeholder="Description"
              className={inputClass}
              value={valueOrEmpty(account.description)}
              onChange={(e) =>
                setAccount((prev) => ({ ...prev, description: e.target.value }))
              }
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Balance</label>
            <input
              name="balance"
              type="number"
              step="0.01"
              placeholder="Balance"
              className={`${inputClass} ${errors.balance ? "border-red-500" : ""}`}
              value={valueOrEmpty(account.accountTypeBalance)}
              onChange={(e) => {
                setAccount((prev) => ({ ...prev, accountTypeBalance: e.target.value }));
                setErrors((prev) => ({ ...prev, balance: undefined }));
              }}
            />
            {errors.balance && <p className="text-xs text-red-600 mt-1">{errors.balance}</p>}
          </div>
        </div>

        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 bg-gray-300 rounded hover:bg-gray-400"
          >
            Cancel
          </button>
          <button
            type="submit"
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            {mode === "edit" ? "Save" : "Add"}
          </button>
        </div>
      </form>
    </div>
  );
}

export default function AccountTypes() {
  const [accountTypes, setAccountTypes] = useState([]);
  const [loading, setLoading] = useState(false);

  const API_BASE = "/account-types";

  const createEmptyAccount = () => ({
    id: null,
    name: "",
    description: "",
    classification: "",
    accountTypeBalance: 0,
  });

  const [modalMode, setModalMode] = useState(null);
  const [modalAccount, setModalAccount] = useState(createEmptyAccount());

  const fetchAccountTypes = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const res = await api.get(API_BASE);
      setAccountTypes(res.data);
    } catch (err) {
      console.error("Fetch error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  useEffect(() => {
    fetchAccountTypes();
  }, []);

  const openAddModal = () => {
    setModalMode("add");
    setModalAccount(createEmptyAccount());
  };

  const openEditModal = (account) => {
    setModalMode("edit");
    setModalAccount({
      id: account.id,
      name: account.name || "",
      description: account.description || "",
      classification: account.classification || "",
      accountTypeBalance:
        account.accountTypeBalance === null || account.accountTypeBalance === undefined
          ? 0
          : account.accountTypeBalance,
    });
  };

  const closeModal = () => {
    setModalMode(null);
    setModalAccount(createEmptyAccount());
  };

  const saveAccountType = async (accountPayload) => {
    const payload = { ...accountPayload };
    if (modalMode !== "edit") {
      delete payload.id;
    }

    setLoading(true);
    NProgress.start();
    try {
      if (modalMode === "edit") {
        await api.put(`${API_BASE}/${accountPayload.id}`, payload);
      } else {
        await api.post(API_BASE, payload);
      }
      closeModal();
      await fetchAccountTypes();
    } catch (err) {
      console.error(`${modalMode === "edit" ? "Update" : "Add"} error:`, err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this account type?")) return;
    setLoading(true);
    NProgress.start();
    try {
      await api.delete(`${API_BASE}/${id}`);
      await fetchAccountTypes();
    } catch (err) {
      console.error("Delete error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const formatBalance = (value) => {
    if (value === null || value === undefined || value === "") {
      return "—";
    }
    const num = Number(value);
    if (Number.isNaN(num)) {
      return value;
    }
    return num.toLocaleString("en-IN", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  };

  return (
    <div className="space-y-6 p-6 bg-blue-50 min-h-screen">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold text-gray-800">🏦 Account Types</h2>
        <button
          onClick={openAddModal}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          ➕ Add Account Type
        </button>
      </div>

      {/* Table */}
      <div className="overflow-x-auto rounded shadow bg-white">
        <table className="min-w-full text-sm text-gray-700">
          <thead className="bg-blue-100 text-left">
            <tr>
              <th className="px-4 py-2 border-b">Name</th>
              <th className="px-4 py-2 border-b">Description</th>
              <th className="px-4 py-2 border-b">Classification</th>
              <th className="px-4 py-2 border-b text-right">Balance</th>
              <th className="px-4 py-2 border-b text-center">Update</th>
              <th className="px-4 py-2 border-b text-center">Delete</th>
            </tr>
          </thead>
          <tbody>
            {Array.isArray(accountTypes) &&
              accountTypes.map((account, idx) => (
                <tr key={account.id} className={idx % 2 === 0 ? "bg-white" : "bg-blue-50"}>
                  <td className="px-4 py-2 border-b">{account.name}</td>
                  <td className="px-4 py-2 border-b">{account.description || "—"}</td>
                  <td className="px-4 py-2 border-b">{account.classification || "—"}</td>
                  <td className="px-4 py-2 border-b text-right">{formatBalance(account.accountTypeBalance)}</td>
                  <td className="px-4 py-2 border-b text-center">
                    <button
                      onClick={() => openEditModal(account)}
                      className="text-blue-600 font-medium hover:underline"
                    >
                      Edit
                    </button>
                  </td>
                  <td className="px-4 py-2 border-b text-center">
                    <button
                      onClick={() => handleDelete(account.id)}
                      className="text-red-600 font-medium hover:underline"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>

      {modalMode && (
        <AccountTypeForm
          account={modalAccount}
          setAccount={setModalAccount}
          mode={modalMode}
          onCancel={closeModal}
          onSubmit={saveAccountType}
        />
      )}

      {/* Overlay */}
      {loading && (
        <div className="fixed inset-0 bg-white bg-opacity-50 flex items-center justify-center z-50">
          <div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-600 h-10 w-10 animate-spin"></div>
        </div>
      )}
    </div>
  );
}
