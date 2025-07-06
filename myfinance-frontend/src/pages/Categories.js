import React, { useEffect, useState } from "react";
import axios from "axios";

export default function Categories() {
  const [categories, setCategories] = useState([]);
  const [allCategories, setAllCategories] = useState([]);
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [newCategory, setNewCategory] = useState({ name: "", description: "", parentId: "" });
  const [editCategory, setEditCategory] = useState({ id: null, name: "", description: "", parentId: "" });

  const fetchCategories = async () => {
    try {
      const [treeRes, allRes] = await Promise.all([
        axios.get("/api/categories/root"),
        axios.get("/api/categories"),
      ]);
      setCategories(treeRes.data);
      setAllCategories(allRes.data);
    } catch (err) {
      console.error("Fetch categories error:", err);
    }
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  const handleAdd = async () => {
    try {
      const payload = {
        name: newCategory.name,
        description: newCategory.description,
        parent: newCategory.parentId ? { id: newCategory.parentId } : null,
      };
      await axios.post("/api/categories", payload);
      setNewCategory({ name: "", description: "", parentId: "" });
      setShowAddModal(false);
      fetchCategories();
    } catch (err) {
      console.error("Add error:", err);
    }
  };

  const handleEditSave = async () => {
    try {
      const payload = {
        name: editCategory.name,
        description: editCategory.description,
        parent: editCategory.parentId ? { id: editCategory.parentId } : null,
      };
      await axios.put(`/api/categories/${editCategory.id}`, payload);
      setShowEditModal(false);
      fetchCategories();
    } catch (err) {
      console.error("Edit error:", err);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Are you sure you want to delete this category?")) return;
    try {
      await axios.delete(`/api/categories/${id}`);
      fetchCategories();
    } catch (err) {
      console.error("Delete error:", err);
    }
  };

  const inputClass = "border px-3 py-2 rounded w-full text-sm";

  const renderCategoryTree = (nodes, prefix = "") => {
    return nodes.map((cat, index) => (
      <div key={cat.id} className="pl-2 border-l border-gray-300 ml-2">
        <div className="text-sm text-gray-800 flex justify-between items-center hover:bg-gray-100 px-1 py-0.5">
          <div>
            <span className="font-mono">{prefix}├─</span> <strong>{cat.name}</strong>
            <span className="text-xs text-gray-500 ml-2">{cat.description}</span>
          </div>
          <div className="space-x-2 text-xs">
            <button
              onClick={() => {
                setEditCategory({
                  id: cat.id,
                  name: cat.name,
                  description: cat.description,
                  parentId: cat.parent?.id || "",
                });
                setShowEditModal(true);
              }}
              className="text-blue-600 hover:underline"
            >
              Edit
            </button>
            <button
              onClick={() => handleDelete(cat.id)}
              className="text-red-600 hover:underline"
            >
              Delete
            </button>
          </div>
        </div>
        {cat.children && cat.children.length > 0 && renderCategoryTree(cat.children, prefix + "│  ")}
      </div>
    ));
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-semibold">📂 Category Hierarchy</h2>
        <button
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          ➕ Add Category
        </button>
      </div>

      <div className="bg-white shadow rounded p-4 text-sm leading-tight">
        {categories.length === 0 ? (
          <div className="text-gray-500">No categories available.</div>
        ) : (
          renderCategoryTree(categories)
        )}
      </div>

      {/* Add Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-10 bg-black bg-opacity-40 flex items-center justify-center">
          <div className="bg-white p-6 rounded-lg shadow-lg w-full max-w-xl space-y-4">
            <h3 className="text-lg font-semibold">Add Category</h3>
            <div className="grid grid-cols-1 gap-4">
              <input
                placeholder="Name"
                className={inputClass}
                value={newCategory.name}
                onChange={(e) => setNewCategory({ ...newCategory, name: e.target.value })}
              />
              <input
                placeholder="Description"
                className={inputClass}
                value={newCategory.description}
                onChange={(e) => setNewCategory({ ...newCategory, description: e.target.value })}
              />
              <select
                className={inputClass}
                value={newCategory.parentId}
                onChange={(e) => setNewCategory({ ...newCategory, parentId: e.target.value })}
              >
                <option value="">-- No Parent --</option>
                {allCategories.map((cat) => (
                  <option key={cat.id} value={cat.id}>{cat.name}</option>
                ))}
              </select>
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowAddModal(false)}
                className="px-4 py-2 bg-gray-300 rounded hover:bg-gray-400"
              >Cancel</button>
              <button
                onClick={handleAdd}
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
              >Save</button>
            </div>
          </div>
        </div>
      )}

      {/* Edit Modal */}
      {showEditModal && (
        <div className="fixed inset-0 z-10 bg-black bg-opacity-40 flex items-center justify-center">
          <div className="bg-white p-6 rounded-lg shadow-lg w-full max-w-xl space-y-4">
            <h3 className="text-lg font-semibold">Edit Category</h3>
            <div className="grid grid-cols-1 gap-4">
              <input
                placeholder="Name"
                className={inputClass}
                value={editCategory.name}
                onChange={(e) => setEditCategory({ ...editCategory, name: e.target.value })}
              />
              <input
                placeholder="Description"
                className={inputClass}
                value={editCategory.description}
                onChange={(e) => setEditCategory({ ...editCategory, description: e.target.value })}
              />
              <select
                className={inputClass}
                value={editCategory.parentId}
                onChange={(e) => setEditCategory({ ...editCategory, parentId: e.target.value })}
              >
                <option value="">-- No Parent --</option>
                {allCategories.map((cat) => (
                  <option key={cat.id} value={cat.id}>{cat.name}</option>
                ))}
              </select>
            </div>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowEditModal(false)}
                className="px-4 py-2 bg-gray-300 rounded hover:bg-gray-400"
              >Cancel</button>
              <button
                onClick={handleEditSave}
                className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
              >Save Changes</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}