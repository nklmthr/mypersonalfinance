import React, { useEffect, useState } from "react";
import axios from "axios";

export default function Institutions() {
  const [institutions, setInstitutions] = useState([]);
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [newInstitution, setNewInstitution] = useState({ name: "", description: "" });
  const [editData, setEditData] = useState({ id: null, name: "", description: "" });
  const API_BASE = "/api/institutions";

  const fetchInstitutions = async () => {
    try {
      const res = await axios.get(API_BASE);
      setInstitutions(res.data);
    } catch (err) {
      console.error("Fetch error:", err);
    }
  };

  useEffect(() => {
    fetchInstitutions();
  }, []);

  const handleAdd = async () => {
    try {
      await axios.post(API_BASE, newInstitution);
      setNewInstitution({ name: "", description: "" });
      setShowAddModal(false);
      fetchInstitutions();
    } catch (err) {
      console.error("Add error:", err);
    }
  };

  const handleEditClick = (institution) => {
    setEditData(institution);
    setShowEditModal(true);
  };

  const handleEditSave = async () => {
    try {
      await axios.put(`${API_BASE}/${editData.id}`, editData);
      setShowEditModal(false);
      fetchInstitutions();
    } catch (err) {
      console.error("Update error:", err);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this institution?")) return;
    try {
      await axios.delete(`${API_BASE}/${id}`);
      fetchInstitutions();
    } catch (err) {
      console.error("Delete error:", err);
    }
  };

  const inputClass = "border px-2 py-1 rounded w-full";

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold">Institutions</h2>
        <button
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          ➕ Add Institution
        </button>
      </div>

      {/* Table */}
      <div className="overflow-x-auto rounded shadow bg-white">
        <table className="min-w-full text-sm text-gray-700">
          <thead className="bg-gray-100 text-left">
            <tr>
              <th className="px-4 py-2 border-b">Name</th>
              <th className="px-4 py-2 border-b">Description</th>
              <th className="px-4 py-2 border-b text-center">Update</th>
              <th className="px-4 py-2 border-b text-center">Delete</th>
            </tr>
          </thead>
          <tbody>
            {institutions.map((inst) => (
              <tr key={inst.id} className="hover:bg-gray-50">
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

      {/* Add Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-10 bg-black bg-opacity-40 flex items-center justify-center">
          <div className="bg-white p-6 rounded-lg shadow-lg w-full max-w-xl space-y-4">
            <h3 className="text-lg font-semibold">Add New Institution</h3>
            <div className="grid grid-cols-1 gap-4">
              <input
                name="name"
                placeholder="Name"
                className={inputClass}
                value={newInstitution.name}
                onChange={(e) =>
                  setNewInstitution({ ...newInstitution, name: e.target.value })
                }
              />
              <input
                name="description"
                placeholder="Description"
                className={inputClass}
                value={newInstitution.description}
                onChange={(e) =>
                  setNewInstitution({ ...newInstitution, description: e.target.value })
                }
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowAddModal(false)}
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

      {/* Edit Modal */}
      {showEditModal && (
        <div className="fixed inset-0 z-10 bg-black bg-opacity-40 flex items-center justify-center">
          <div className="bg-white p-6 rounded-lg shadow-lg w-full max-w-xl space-y-4">
            <h3 className="text-lg font-semibold">Edit Institution</h3>
            <div className="grid grid-cols-1 gap-4">
              <input
                name="name"
                placeholder="Name"
                className={inputClass}
                value={editData.name}
                onChange={(e) =>
                  setEditData({ ...editData, name: e.target.value })
                }
              />
              <input
                name="description"
                placeholder="Description"
                className={inputClass}
                value={editData.description}
                onChange={(e) =>
                  setEditData({ ...editData, description: e.target.value })
                }
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowEditModal(false)}
                className="px-4 py-2 bg-gray-300 rounded hover:bg-gray-400"
              >
                Cancel
              </button>
              <button
                onClick={handleEditSave}
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
              >
                Save Changes
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
