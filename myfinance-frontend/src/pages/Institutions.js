import React, { useEffect, useState } from "react";
import api from "./../auth/api";
import NProgress from "nprogress";
import "nprogress/nprogress.css";

export default function Institutions() {
  const [institutions, setInstitutions] = useState([]);
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [newInstitution, setNewInstitution] = useState({ name: "", description: "" });
  const [editData, setEditData] = useState({ id: null, name: "", description: "" });
  const [loading, setLoading] = useState(false);
  const API_BASE = "/institutions";

  const fetchInstitutions = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const res = await api.get(API_BASE);
      setInstitutions(res.data);
    } catch (err) {
      console.error("Fetch error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  useEffect(() => {
    fetchInstitutions();
  }, []);

  const handleAdd = async () => {
    setLoading(true);
    NProgress.start();
    try {
      await api.post(API_BASE, newInstitution);
      setNewInstitution({ name: "", description: "" });
      setShowAddModal(false);
      fetchInstitutions();
    } catch (err) {
      console.error("Add error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const handleEditClick = (institution) => {
    setEditData(institution);
    setShowEditModal(true);
  };

  const handleEditSave = async () => {
    setLoading(true);
    NProgress.start();
    try {
      await api.put(`${API_BASE}/${editData.id}`, editData);
      setShowEditModal(false);
      fetchInstitutions();
    } catch (err) {
      console.error("Update error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this institution?")) return;
    setLoading(true);
    NProgress.start();
    try {
      await api.delete(`${API_BASE}/${id}`);
      fetchInstitutions();
    } catch (err) {
      console.error("Delete error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const inputClass = "border px-2 py-2 rounded w-full bg-blue-50 focus:outline-none focus:ring-2 focus:ring-blue-500";

  const renderModal = (isEdit = false) => {
    const data = isEdit ? editData : newInstitution;
    const setData = isEdit ? setEditData : setNewInstitution;
    const saveFn = isEdit ? handleEditSave : handleAdd;

    return (
      <div className="fixed inset-0 z-50 bg-black bg-opacity-40 flex items-center justify-center">
        <div className="bg-white p-6 rounded-lg shadow-lg w-full max-w-xl space-y-4">
          <h3 className="text-lg font-semibold">{isEdit ? "Edit Institution" : "Add New Institution"}</h3>
          <div className="grid grid-cols-1 gap-4">
            <input
              placeholder="Name"
              className={inputClass}
              value={data.name}
              onChange={(e) => setData({ ...data, name: e.target.value })}
            />
            <input
              placeholder="Description"
              className={inputClass}
              value={data.description}
              onChange={(e) => setData({ ...data, description: e.target.value })}
            />
          </div>
          <div className="flex justify-end gap-2">
            <button
              onClick={() => (isEdit ? setShowEditModal(false) : setShowAddModal(false))}
              className="px-4 py-2 bg-gray-300 rounded hover:bg-gray-400"
            >
              Cancel
            </button>
            <button
              onClick={saveFn}
              className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
            >
              Save{isEdit ? " Changes" : ""}
            </button>
          </div>
        </div>
      </div>
    );
  };

  return (
    <div className="space-y-6 p-6 bg-blue-50 min-h-screen">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold text-gray-800">🏛️ Institutions</h2>
        <button
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 shadow"
        >
          ➕ Add Institution
        </button>
      </div>

      <div className="overflow-x-auto rounded shadow bg-white">
        <table className="min-w-full text-sm text-gray-700">
          <thead className="bg-blue-100 text-left">
            <tr>
              <th className="px-4 py-2 border-b">Name</th>
              <th className="px-4 py-2 border-b">Description</th>
              <th className="px-4 py-2 border-b text-center">Update</th>
              <th className="px-4 py-2 border-b text-center">Delete</th>
            </tr>
          </thead>
          <tbody>
            {institutions.map((inst, idx) => (
              <tr key={inst.id} className={idx % 2 === 0 ? "bg-white" : "bg-blue-50"}>
                <td className="px-4 py-2 border-b">{inst.name}</td>
                <td className="px-4 py-2 border-b">{inst.description}</td>
                <td className="px-4 py-2 border-b text-center">
                  <button
                    onClick={() => handleEditClick(inst)}
                    className="text-blue-600 font-medium hover:underline"
                  >
                    Edit
                  </button>
                </td>
                <td className="px-4 py-2 border-b text-center">
                  <button
                    onClick={() => handleDelete(inst.id)}
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

      {showAddModal && renderModal(false)}
      {showEditModal && renderModal(true)}

      {loading && (
        <div className="fixed inset-0 bg-white bg-opacity-50 flex items-center justify-center z-50">
          <div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-600 h-10 w-10 animate-spin"></div>
        </div>
      )}
    </div>
  );
}
