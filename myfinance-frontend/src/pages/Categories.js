import React, { useEffect, useState } from "react";
import api from "./../auth/api";
import NProgress from "nprogress";
import "nprogress/nprogress.css";

export default function Categories() {
  const [categories, setCategories] = useState([]);
  const [allCategories, setAllCategories] = useState([]);
  const [expanded, setExpanded] = useState(new Set());

  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [newCategory, setNewCategory] = useState({ name: "", parentId: "" });
  const [editCategory, setEditCategory] = useState({ id: "", name: "", parentId: "" });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    fetchCategories();
  }, []);

  const fetchCategories = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const res = await api.get("/categories");
      const nestedTree = res.data;
      setCategories(nestedTree);
      setAllCategories(flattenTree(nestedTree));
    } catch (err) {
      console.error("Failed to fetch categories:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const flattenTree = (nodes) => {
    const flatList = [];
    const recurse = (node) => {
      flatList.push({ id: node.id, name: node.name, parentId: node.parentId });
      if (node.children) node.children.forEach(recurse);
    };
    nodes.forEach(recurse);
    return flatList;
  };

  const toggleExpand = (id) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const isExpanded = (id) => expanded.has(id);

  const handleAdd = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const payload = {
        name: newCategory.name,
        parent: newCategory.parentId ? { id: newCategory.parentId } : null,
      };
      await api.post("/categories", payload);
      setNewCategory({ name: "", parentId: "" });
      setShowAddModal(false);
      fetchCategories();
    } catch (err) {
      console.error("Add category failed:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const handleEditSave = async () => {
    setLoading(true);
    NProgress.start();
    try {
      const payload = {
        name: editCategory.name,
        parent: editCategory.parentId ? { id: editCategory.parentId } : null,
      };
      await api.put(`/categories/${editCategory.id}`, payload);
      setShowEditModal(false);
      fetchCategories();
    } catch (err) {
      console.error("Edit category failed:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm("Are you sure you want to delete this category?")) return;
    setLoading(true);
    NProgress.start();
    try {
      await api.delete(`/categories/${id}`);
      fetchCategories();
    } catch (err) {
      console.error("Delete category failed:", err);
    } finally {
      setLoading(false);
      NProgress.done();
    }
  };

  const inputClass =
    "border px-3 py-2 rounded w-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-blue-50";

  const renderCategoryTree = (nodes, depth = 0) =>
    nodes.map((cat) => (
      <div
        key={cat.id}
        className={`ml-${depth * 2} mt-1 border-l border-blue-200 pl-2`}
      >
        <div className="flex justify-between items-center text-sm text-gray-800 hover:bg-blue-50 px-1 py-0.5 rounded">
          <div className="flex items-center gap-1 cursor-pointer" onClick={() => toggleExpand(cat.id)}>
            {cat.children && cat.children.length > 0 && (
              <span className="text-blue-500 text-xs w-4">{isExpanded(cat.id) ? "▼" : "▶"}</span>
            )}
            <span className="font-medium">{cat.name}</span>
          </div>
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
        {cat.children && isExpanded(cat.id) && renderCategoryTree(cat.children, depth + 1)}
      </div>
    ));

  const Modal = ({ title, onClose, children }) => (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-40">
      <div className="bg-white p-6 rounded-lg shadow-lg w-full max-w-xl space-y-4 animate-fadeIn">
        <div className="flex justify-between items-center border-b pb-2">
          <h3 className="text-lg font-semibold">{title}</h3>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-700">&times;</button>
        </div>
        {children}
      </div>
    </div>
  );

  return (
    <div className="p-6 space-y-6 bg-blue-50 min-h-screen">
      <div className="flex justify-between items-center">
        <h2 className="text-2xl font-semibold text-gray-800">📂 Category Hierarchy</h2>
        <button
          onClick={() => setShowAddModal(true)}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 shadow"
        >
          ➕ Add Category
        </button>
      </div>

      <div className="bg-white shadow rounded p-4 text-sm leading-tight border border-blue-200">
        {categories.length === 0 ? (
          <div className="text-gray-500">No categories found.</div>
        ) : (
          renderCategoryTree(categories)
        )}
      </div>

      {showAddModal && (
        <Modal title="Add Category" onClose={() => setShowAddModal(false)}>
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
                <option key={cat.id} value={cat.id}>{cat.name}</option>
              ))}
            </select>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowAddModal(false)}
                className="px-4 py-2 bg-gray-200 rounded hover:bg-gray-300"
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
        </Modal>
      )}

      {showEditModal && (
        <Modal title="Edit Category" onClose={() => setShowEditModal(false)}>
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
                <option key={cat.id} value={cat.id}>{cat.name}</option>
              ))}
            </select>
            <div className="flex justify-end gap-2">
              <button
                onClick={() => setShowEditModal(false)}
                className="px-4 py-2 bg-gray-200 rounded hover:bg-gray-300"
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
        </Modal>
      )}

      {loading && (
        <div className="fixed inset-0 bg-white bg-opacity-50 flex items-center justify-center z-50">
          <div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-600 h-10 w-10 animate-spin"></div>
        </div>
      )}
    </div>
  );
}
