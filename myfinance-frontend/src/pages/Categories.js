import React, { useEffect, useState } from "react";
import axios from "axios";

export default function Categories() {
  const [categories, setCategories] = useState([]);
  const [allCategories, setAllCategories] = useState([]);
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [newCategory, setNewCategory] = useState({ name: "", parentId: "" });
  const [editCategory, setEditCategory] = useState({ id: "", name: "", parentId: "" });

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    try {
      const res = await axios.get("/api/categories");
      const nestedTree = res.data;
      setCategories(nestedTree);
      setAllCategories(flattenTree(nestedTree));
    } catch (err) {
      console.error("Failed to fetch categories:", err);
    }
  };

  const flattenTree = (nodes) => {
    const flatList = [];
    const recurse = (node) => {
      flatList.push({ id: node.id, name: node.name, parentId: node.parentId });
      if (node.children) {
        node.children.forEach(recurse);
      }
    };
    nodes.forEach(recurse);
    return flatList;
  };

  const handleAdd = async () => {
    try {
      const payload = {
        name: newCategory.name,
        parent: newCategory.parentId ? { id: newCategory.parentId } : null,
      };
      await axios.post("/api/categories", payload);
      setNewCategory({ name: "", parentId: "" });
      setShowAddModal(false);
      fetchCategories();
    } catch (err) {
      console.error("Add category failed:", err);
    }
  };

  const handleEditSave = async () => {
    try {
      const payload = {
        name: editCategory.name,
        parent: editCategory.parentId ? { id: editCategory.parentId } : null,
      };
      await axios.put(`/api/categories/${editCategory.id}`, payload);
      setShowEditModal(false);
      fetchCategories();
    } catch (err) {
      console.error("Edit category failed:", err);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Are you sure you want to delete this category?")) return;
    try {
      await axios.delete(`/api/categories/${id}`);
      fetchCategories();
    } catch (err) {
      console.error("Delete category failed:", err);
    }
  };

  const inputClass = "border px-3 py-2 rounded w-full text-sm";

  const renderCategoryTree = (nodes) => {
    return nodes.map((cat) => (
      <div key={cat.id} className="pl-2 border-l border-gray-300 ml-2 mt-1">
        <div className="text-sm text-gray-800 flex justify-between items-center hover:bg-gray-100 px-1 py-0.5">
          <div className="font-medium">{cat.name}</div>
          <div className="space-x-2 text-xs">
            <button
              className="text-blue-600 hover:underline"
              onClick={() => {
                setEditCategory({
                  id: cat.id,
                  name: cat.name,
                  parentId: cat.parentId || "",
                });
                setShowEditModal(true);
              }}
            >
              Edit
            </button>
            <button
              className="text-red-600 hover:underline"
              onClick={() => handleDelete(cat.id)}
            >
              Delete
            </button>
          </div>
        </div>
        {cat.children && cat.children.length > 0 && renderCategoryTree(cat.children)}
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
          <div className="text-gray-500">No categories found.</div>
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
              <select
                className={inputClass}
                value={newCategory.parentId}
                onChange={(e) => setNewCategory({ ...newCategory, parentId: e.target.value })}
              >
                <option value="">-- No Parent --</option>
                {allCategories.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name}
                  </option>
                ))}
              </select>
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
            <h3 className="text-lg font-semibold">Edit Category</h3>
            <div className="grid grid-cols-1 gap-4">
              <input
                placeholder="Name"
                className={inputClass}
                value={editCategory.name}
                onChange={(e) => setEditCategory({ ...editCategory, name: e.target.value })}
              />
              <select
                className={inputClass}
                value={editCategory.parentId}
                onChange={(e) => setEditCategory({ ...editCategory, parentId: e.target.value })}
              >
                <option value="">-- No Parent --</option>
                {allCategories.map((cat) => (
                  <option key={cat.id} value={cat.id}>
                    {cat.name}
                  </option>
                ))}
              </select>
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
