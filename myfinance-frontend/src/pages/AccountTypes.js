import React, { useEffect, useState } from "react";
import axios from "axios";

export default function AccountTypes() {
  const [accountTypes, setAccountTypes] = useState([]);
  const [showModal, setShowModal] = useState(false);
  const [newAccount, setNewAccount] = useState({
    name: "",
    description: "",
    classification: "",
    balance: 0,
  });
  const [editingId, setEditingId] = useState(null);
  const [editData, setEditData] = useState({});
  const API_BASE = "/api/account-types";

  const fetchAccountTypes = async () => {
    try {
      const res = await axios.get(API_BASE);
      setAccountTypes(res.data);
    } catch (err) {
      console.error("Fetch error:", err);
    }
  };

  useEffect(() => {
    fetchAccountTypes();
  }, []);

  const handleAdd = async () => {
    try {
      await axios.post(API_BASE, newAccount);
      setNewAccount({ name: "", description: "", classification: "", balance: 0 });
      setShowModal(false);
      fetchAccountTypes();
    } catch (err) {
      console.error("Add error:", err);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this account type?")) return;
    try {
      await axios.delete(`${API_BASE}/${id}`);
      fetchAccountTypes();
    } catch (err) {
      console.error("Delete error:", err);
    }
  };

  const handleEdit = (account) => {
    setEditingId(account.id);
    setEditData({ ...account });
  };

  const handleUpdate = async () => {
    try {
      await axios.put(`${API_BASE}/${editingId}`, editData);
      setEditingId(null);
      fetchAccountTypes();
    } catch (err) {
      console.error("Update error:", err);
    }
  };

  const handleCancel = () => {
    setEditingId(null);
    setEditData({});
  };

  const inputClass = "border px-2 py-1 rounded w-full";

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
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
          <thead className="bg-gray-100 text-left">
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
            {Array.isArray(accountTypes) && accountTypes.map((account) => (
              <tr key={account.id} className="hover:bg-gray-50">
                <td className="px-4 py-2 border-b">
                  {editingId === account.id ? (
                    <input
                      className={inputClass}
                      value={editData.name}
                      onChange={(e) =>
                        setEditData({ ...editData, name: e.target.value })
                      }
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
                      onChange={(e) =>
                        setEditData({ ...editData, description: e.target.value })
                      }
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
                      onChange={(e) =>
                        setEditData({ ...editData, classification: e.target.value })
                      }
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
                      onChange={(e) =>
                        setEditData({ ...editData, balance: e.target.value })
                      }
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
        <div className="fixed inset-0 z-10 bg-black bg-opacity-40 flex items-center justify-center">
          <div className="bg-white p-6 rounded-lg shadow-lg w-full max-w-xl space-y-4">
            <h3 className="text-lg font-semibold">Add New Account Type</h3>
            <div className="grid grid-cols-2 gap-4">
              <input
                name="name"
                placeholder="Name"
                className={inputClass}
                value={newAccount.name}
                onChange={(e) =>
                  setNewAccount({ ...newAccount, name: e.target.value })
                }
              />
              <input
                name="description"
                placeholder="Description"
                className={inputClass}
                value={newAccount.description}
                onChange={(e) =>
                  setNewAccount({ ...newAccount, description: e.target.value })
                }
              />
              <input
                name="classification"
                placeholder="Classification"
                className={inputClass}
                value={newAccount.classification}
                onChange={(e) =>
                  setNewAccount({
                    ...newAccount,
                    classification: e.target.value,
                  })
                }
              />
              <input
                name="balance"
                type="number"
                placeholder="Balance"
                className={inputClass}
                value={newAccount.balance}
                onChange={(e) =>
                  setNewAccount({ ...newAccount, balance: e.target.value })
                }
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
    </div>
  );
}
