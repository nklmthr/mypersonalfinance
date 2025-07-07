import React, { useEffect, useState } from "react";
import axios from "axios";

export default function Accounts() {
  const [accounts, setAccounts] = useState([]);
  const [institutions, setInstitutions] = useState([]);
  const [accountTypes, setAccountTypes] = useState([]);

  const [selectedInstitutionId, setSelectedInstitutionId] = useState("");
  const [selectedAccountTypeId, setSelectedAccountTypeId] = useState("");

  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);

  const [newAccount, setNewAccount] = useState({
    name: "",
    description: "",
    balance: 0,
    institutionId: "",
    accountTypeId: "",
  });

  const [editData, setEditData] = useState({
    id: null,
    name: "",
    description: "",
    balance: 0,
    institutionId: "",
    accountTypeId: "",
  });

  const fetchAccounts = async (accountTypeId = "", institutionId = "") => {
    try {
      const params = {};
      if (accountTypeId) params.accountTypeId = accountTypeId;
      if (institutionId) params.institutionId = institutionId;
      const res = await axios.get("/api/accounts/filter", { params });
      setAccounts(res.data);
    } catch (err) {
      console.error("Fetch accounts error:", err);
    }
  };

  const fetchAll = async () => {
    try {
      const [instRes, typeRes] = await Promise.all([
        axios.get("/api/institutions"),
        axios.get("/api/account-types"),
      ]);
      setInstitutions(instRes.data);
      setAccountTypes(typeRes.data);
    } catch (err) {
      console.error("Fetch error:", err);
    }
  };

  useEffect(() => {
    fetchAll();
  }, []);

  useEffect(() => {
    fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
  }, [selectedAccountTypeId, selectedInstitutionId]);

  const handleAdd = async () => {
    try {
      await axios.post("/api/accounts", {
        name: newAccount.name,
        description: newAccount.description,
        balance: newAccount.balance,
        institution: { id: newAccount.institutionId },
        accountType: { id: newAccount.accountTypeId },
      });
      setNewAccount({
        name: "",
        description: "",
        balance: 0,
        institutionId: "",
        accountTypeId: "",
      });
      setShowAddModal(false);
      fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
    } catch (err) {
      console.error("Add error:", err);
    }
  };

  const handleEditClick = (acc) => {
    setEditData({
      id: acc.id,
      name: acc.name,
      description: acc.description,
      balance: acc.balance,
      institutionId: acc.institution?.id || "",
      accountTypeId: acc.accountType?.id || "",
    });
    setShowEditModal(true);
  };

  const handleEditSave = async () => {
    try {
      await axios.put(`/api/accounts/${editData.id}`, {
        id: editData.id,
        name: editData.name,
        description: editData.description,
        balance: editData.balance,
        institution: { id: editData.institutionId },
        accountType: { id: editData.accountTypeId },
      });
      setShowEditModal(false);
      fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
    } catch (err) {
      console.error("Update error:", err);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this account?")) return;
    try {
      await axios.delete(`/api/accounts/${id}`);
      fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
    } catch (err) {
		if (err.response && err.response.status === 409) {
	      alert(err.response.data || "Cannot delete: Transactions exist for this account.");
	    } else {
	      alert("An unexpected error occurred.");
	    }
		console.error("Delete error:", err);
    }
  };

  const inputClass = "border px-3 py-2 rounded w-full text-sm";

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold">💼 Accounts</h2>
        <button
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          ➕ Add Account
        </button>
      </div>

      {/* Filters */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
        <div>
          <label className="block text-sm font-medium mb-1">Account Type</label>
          <select
            className={inputClass}
            value={selectedAccountTypeId}
            onChange={(e) => setSelectedAccountTypeId(e.target.value)}
          >
            <option value="">All</option>
            {accountTypes.map((type) => (
              <option key={type.id} value={type.id}>
                {type.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium mb-1">Institution</label>
          <select
            className={inputClass}
            value={selectedInstitutionId}
            onChange={(e) => setSelectedInstitutionId(e.target.value)}
          >
            <option value="">All</option>
            {institutions.map((inst) => (
              <option key={inst.id} value={inst.id}>
                {inst.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Table */}
      <div className="overflow-x-auto rounded shadow bg-white">
        <table className="min-w-full text-sm text-gray-700">
          <thead className="bg-gray-100 text-left">
            <tr>
              <th className="px-4 py-2 border-b">Name</th>
              <th className="px-4 py-2 border-b">Description</th>
              <th className="px-4 py-2 border-b text-right">Balance</th>
              <th className="px-4 py-2 border-b">Account Type</th>
              <th className="px-4 py-2 border-b">Institution</th>
              <th className="px-4 py-2 border-b text-center">Update</th>
              <th className="px-4 py-2 border-b text-center">Delete</th>
            </tr>
          </thead>
          <tbody>
            {accounts.map((acc) => (
              <tr key={acc.id} className="hover:bg-gray-50">
                <td className="px-4 py-2 border-b">{acc.name}</td>
                <td className="px-4 py-2 border-b">{acc.description}</td>
                <td className="px-4 py-2 border-b text-right">{acc.balance}</td>
                <td className="px-4 py-2 border-b">{acc.accountType?.name}</td>
                <td className="px-4 py-2 border-b">{acc.institution?.name}</td>
                <td className="px-4 py-2 border-b text-center">
                  <button
                    onClick={() => handleEditClick(acc)}
                    className="text-blue-600 font-medium hover:underline"
                  >
                    Edit
                  </button>
                </td>
                <td className="px-4 py-2 border-b text-center">
                  <button
                    onClick={() => handleDelete(acc.id)}
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

      {/* Add Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white p-6 rounded w-full max-w-md">
            <h3 className="text-lg font-semibold mb-4">Add Account</h3>
            <input className={inputClass + " mb-2"} placeholder="Name" value={newAccount.name} onChange={(e) => setNewAccount({ ...newAccount, name: e.target.value })} />
            <input className={inputClass + " mb-2"} placeholder="Description" value={newAccount.description} onChange={(e) => setNewAccount({ ...newAccount, description: e.target.value })} />
            <input type="number" className={inputClass + " mb-2"} placeholder="Balance" value={newAccount.balance} onChange={(e) => setNewAccount({ ...newAccount, balance: parseFloat(e.target.value) })} />
            <select className={inputClass + " mb-2"} value={newAccount.accountTypeId} onChange={(e) => setNewAccount({ ...newAccount, accountTypeId: e.target.value })}>
              <option value="">Select Account Type</option>
              {accountTypes.map(type => <option key={type.id} value={type.id}>{type.name}</option>)}
            </select>
            <select className={inputClass + " mb-4"} value={newAccount.institutionId} onChange={(e) => setNewAccount({ ...newAccount, institutionId: e.target.value })}>
              <option value="">Select Institution</option>
              {institutions.map(inst => <option key={inst.id} value={inst.id}>{inst.name}</option>)}
            </select>
            <div className="flex justify-end space-x-2">
              <button onClick={() => setShowAddModal(false)} className="px-4 py-2 border rounded">Cancel</button>
              <button onClick={handleAdd} className="px-4 py-2 bg-blue-600 text-white rounded">Save</button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Modal */}
      {showEditModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white p-6 rounded w-full max-w-md">
            <h3 className="text-lg font-semibold mb-4">Edit Account</h3>
            <input className={inputClass + " mb-2"} placeholder="Name" value={editData.name} onChange={(e) => setEditData({ ...editData, name: e.target.value })} />
            <input className={inputClass + " mb-2"} placeholder="Description" value={editData.description} onChange={(e) => setEditData({ ...editData, description: e.target.value })} />
            <input type="number" className={inputClass + " mb-2"} placeholder="Balance" value={editData.balance} onChange={(e) => setEditData({ ...editData, balance: parseFloat(e.target.value) })} />
            <select className={inputClass + " mb-2"} value={editData.accountTypeId} onChange={(e) => setEditData({ ...editData, accountTypeId: e.target.value })}>
              <option value="">Select Account Type</option>
              {accountTypes.map(type => <option key={type.id} value={type.id}>{type.name}</option>)}
            </select>
            <select className={inputClass + " mb-4"} value={editData.institutionId} onChange={(e) => setEditData({ ...editData, institutionId: e.target.value })}>
              <option value="">Select Institution</option>
              {institutions.map(inst => <option key={inst.id} value={inst.id}>{inst.name}</option>)}
            </select>
            <div className="flex justify-end space-x-2">
              <button onClick={() => setShowEditModal(false)} className="px-4 py-2 border rounded">Cancel</button>
              <button onClick={handleEditSave} className="px-4 py-2 bg-blue-600 text-white rounded">Save</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
