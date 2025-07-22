import React, { useEffect, useState } from "react";
import api from "./../auth/api";
import NProgress from "nprogress";
import "nprogress/nprogress.css";

export default function AccountTypes() {
  const [accountTypes, setAccountTypes] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [loading, setLoading] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editData, setEditData] = useState({});
  const [newAccount, setNewAccount] = useState({
    name: "",
    description: "",
    classification: "",
    balance: 0,
  });

  const API_BASE = "/account-types";

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

  const handleAdd = async () => {
    setLoading(true);
    NProgress.start();
    try {
      await api.post(API_BASE, newAccount);
      setNewAccount({ name: "", description: "", classification: "", balance: 0 });
      setShowModal(false);
      fetchAccountTypes();
    } catch (err) {
      console.error("Add error:", err);
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
      fetchAccountTypes();
    } catch (err) {
      console.error("Delete error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const handleEdit = (account) => {
    setEditingId(account.id);
    setEditData({ ...account });
  };

  const handleUpdate = async () => {
    setLoading(true);
    NProgress.start();
    try {
      await api.put(`${API_BASE}/${editingId}`, editData);
      setEditingId(null);
      fetchAccountTypes();
    } catch (err) {
      console.error("Update error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const handleCancel = () => {
    setEditingId(null);
    setEditData({});
  };

  const inputClass = "border px-2 py-1 rounded w-full bg-blue-50 focus:outline-none focus:ring-2 focus:ring-blue-500";

  return (
    <div className="space-y-6 p-6 bg-blue-50 min-h-screen">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold text-gray-800">🏦 Account Types</h2>
        <button
          onClick={() => setShowModal(true)}
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
                  <td className="px-4 py-2 border-b">
                    {editingId === account.id ? (
                      <input
                        className={inputClass}
                        value={editData.name}
                        onChange={(e) => setEditData({ ...editData, name: e.target.value })}
                      />
                    ) : (
                      account.name
                    )}
                  </td>
                  <td className="px-4 py-2 border-b">
                    {editingId === account.id ? (
                      <input
                        className={inputClass}
                        value={editData.description}
                        onChange={(e) => setEditData({ ...editData, description: e.target.value })}
                      />
                    ) : (
                      account.description
                    )}
                  </td>
                  <td className="px-4 py-2 border-b">
                    {editingId === account.id ? (
                      <input
                        className={inputClass}
                        value={editData.classification}
                        onChange={(e) => setEditData({ ...editData, classification: e.target.value })}
                      />
                    ) : (
                      account.classification
                    )}
                  </td>
                  <td className="px-4 py-2 border-b text-right">
                    {editingId === account.id ? (
                      <input
                        type="number"
                        className={inputClass}
                        value={editData.balance}
                        onChange={(e) => setEditData({ ...editData, balance: e.target.value })}
                      />
                    ) : (
                      account.balance
                    )}
                  </td>
                  <td className="px-4 py-2 border-b text-center">
                    {editingId === account.id ? (
                      <>
                        <button
                          onClick={handleUpdate}
                          className="text-green-600 font-medium hover:underline mr-2"
                        >
                          Save
                        </button>
                        <button
                          onClick={handleCancel}
                          className="text-gray-500 font-medium hover:underline"
                        >
                          Cancel
                        </button>
                      </>
                    ) : (
                      <button
                        onClick={() => handleEdit(account)}
                        className="text-blue-600 font-medium hover:underline"
                      >
                        Edit
                      </button>
                    )}
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

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 bg-black bg-opacity-40 flex items-center justify-center">
          <div className="bg-white p-6 rounded-lg shadow-lg w-full max-w-xl space-y-4">
            <h3 className="text-lg font-semibold">Add New Account Type</h3>
            <div className="grid grid-cols-2 gap-4">
              <input
                name="name"
                placeholder="Name"
                className={inputClass}
                value={newAccount.name}
                onChange={(e) => setNewAccount({ ...newAccount, name: e.target.value })}
              />
              <input
                name="description"
                placeholder="Description"
                className={inputClass}
                value={newAccount.description}
                onChange={(e) => setNewAccount({ ...newAccount, description: e.target.value })}
              />
              <input
                name="classification"
                placeholder="Classification"
                className={inputClass}
                value={newAccount.classification}
                onChange={(e) => setNewAccount({ ...newAccount, classification: e.target.value })}
              />
              <input
                name="balance"
                type="number"
                placeholder="Balance"
                className={inputClass}
                value={newAccount.balance}
                onChange={(e) => setNewAccount({ ...newAccount, balance: e.target.value })}
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowModal(false)}
                className="px-4 py-2 bg-gray-300 rounded hover:bg-gray-400"
              >
                Cancel
              </button>
              <button
                onClick={handleAdd}
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
              >
                Save
              </button>
            </div>
          </div>
        </div>
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
