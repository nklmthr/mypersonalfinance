import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import api from "./../auth/api";
import NProgress from "nprogress";
import "nprogress/nprogress.css";

const inputClass =
  "w-full border rounded px-3 py-2 bg-blue-50 focus:outline-none focus:ring-2 focus:ring-blue-500";

function LabelForm({ label, setLabel, mode, onCancel, onSubmit }) {
  const [errors, setErrors] = useState({});

  useEffect(() => {
    const handler = (e) => e.key === "Escape" && onCancel();
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onCancel]);

  useEffect(() => {
    setErrors({});
  }, [label?.id, mode]);

  const validateAndSubmit = (e) => {
    e.preventDefault();
    const nextErrors = {};
    const trimmedName = label.name?.trim();
    if (!trimmedName) nextErrors.name = "Required";
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors);
      return;
    }
    onSubmit({ ...label, name: trimmedName });
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50">
      <form
        onSubmit={validateAndSubmit}
        className="bg-white p-6 rounded-lg shadow-lg w-full max-w-sm space-y-4"
      >
        <h3 className="text-lg font-semibold text-gray-800">
          {mode === "edit" ? "Edit" : "Add"} Label
        </h3>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Name<span className="text-red-600">*</span>
          </label>
          <input
            name="name"
            placeholder="Label name"
            autoFocus
            className={`${inputClass} ${errors.name ? "border-red-500" : ""}`}
            value={label.name ?? ""}
            onChange={(e) => {
              setLabel((prev) => ({ ...prev, name: e.target.value }));
              setErrors((prev) => ({ ...prev, name: undefined }));
            }}
          />
          {errors.name && <p className="text-xs text-red-600 mt-1">{errors.name}</p>}
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

const emptyLabel = () => ({ id: null, name: "" });

export default function Labels() {
  const [labels, setLabels] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalMode, setModalMode] = useState(null);
  const [modalLabel, setModalLabel] = useState(emptyLabel());

  const API_BASE = "/labels";

  const fetchLabels = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const res = await api.get(API_BASE);
      setLabels(res.data);
    } catch (err) {
      console.error("Fetch error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  useEffect(() => {
    fetchLabels();
  }, []);

  const openAddModal = () => {
    setModalMode("add");
    setModalLabel(emptyLabel());
  };

  const openEditModal = (label) => {
    setModalMode("edit");
    setModalLabel({ id: label.id, name: label.name });
  };

  const closeModal = () => {
    setModalMode(null);
    setModalLabel(emptyLabel());
  };

  const saveLabel = async (payload) => {
    setLoading(true);
    NProgress.start();
    try {
      if (modalMode === "edit") {
        await api.put(`${API_BASE}/${payload.id}`, payload);
      } else {
        await api.post(API_BASE, { name: payload.name });
      }
      closeModal();
      await fetchLabels();
    } catch (err) {
      console.error(`${modalMode === "edit" ? "Update" : "Add"} error:`, err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Delete this label?")) return;
    setLoading(true);
    NProgress.start();
    try {
      await api.delete(`${API_BASE}/${id}`);
      await fetchLabels();
    } catch (err) {
      console.error("Delete error:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  return (
    <div className="space-y-6 p-6 bg-blue-50 min-h-screen">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold text-gray-800">🏷️ Labels</h2>
        <button
          onClick={openAddModal}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          ➕ Add Label
        </button>
      </div>

      <div className="overflow-x-auto rounded shadow bg-white">
        <table className="min-w-full text-sm text-gray-700">
          <thead className="bg-blue-100 text-left">
            <tr>
              <th className="px-4 py-2 border-b">Name</th>
              <th className="px-4 py-2 border-b text-center">Update</th>
              <th className="px-4 py-2 border-b text-center">Delete</th>
            </tr>
          </thead>
          <tbody>
            {labels.length === 0 && !loading && (
              <tr>
                <td colSpan={3} className="px-4 py-6 text-center text-gray-400">
                  No labels yet. Add one above.
                </td>
              </tr>
            )}
            {labels.map((label, idx) => (
              <tr key={label.id} className={idx % 2 === 0 ? "bg-white" : "bg-blue-50"}>
                <td className="px-4 py-2 border-b">
                  <Link
                    to={`/transactions?labelId=${label.id}&noMonth=true`}
                    className="text-blue-600 hover:underline"
                  >
                    {label.name}
                  </Link>
                </td>
                <td className="px-4 py-2 border-b text-center">
                  <button
                    onClick={() => openEditModal(label)}
                    className="text-blue-600 font-medium hover:underline"
                  >
                    Edit
                  </button>
                </td>
                <td className="px-4 py-2 border-b text-center">
                  <button
                    onClick={() => handleDelete(label.id)}
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
        <LabelForm
          label={modalLabel}
          setLabel={setModalLabel}
          mode={modalMode}
          onCancel={closeModal}
          onSubmit={saveLabel}
        />
      )}

      {loading && (
        <div className="fixed inset-0 bg-white bg-opacity-50 flex items-center justify-center z-50">
          <div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-600 h-10 w-10 animate-spin"></div>
        </div>
      )}
    </div>
  );
}
