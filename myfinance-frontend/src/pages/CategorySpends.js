import React, { useState, useEffect } from "react";
import axios from "axios";
import { motion, AnimatePresence } from "framer-motion";

const months = [
  "January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December"
];

export default function CategorySpendSummary() {
  const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth() + 1);
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear());
  const [spendData, setSpendData] = useState(null);
  const [expanded, setExpanded] = useState({});

  const fetchData = async () => {
    try {
      const res = await axios.get("/api/category-spends", {
        params: { month: selectedMonth, year: selectedYear },
      });
      setSpendData(res.data);
    } catch (err) {
      console.error("Fetch error:", err);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const toggleExpand = (id) => {
    setExpanded((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const renderCategoryTree = (node, level = 0) => {
    if (!node) return null;

    const indent = level * 16;
    const hasChildren = node.children && node.children.length > 0;
    const isExpanded = expanded[node.categoryId];
    const isNegative = node.amount < 0;

    return (
      <div key={node.categoryId}>
        <div
          style={{ paddingLeft: indent }}
          className="flex items-center cursor-pointer group"
          onClick={() => hasChildren && toggleExpand(node.categoryId)}
        >
          {hasChildren && (
            <span className="mr-1 text-gray-500 group-hover:text-gray-800">
              {isExpanded ? "▼" : "▶"}
            </span>
          )}
		  <motion.div
		    initial={{ opacity: 0 }}
		    animate={{ opacity: 1 }}
		    transition={{ duration: 0.3 }}
		    className={`${isNegative ? "text-red-600" : "text-green-600"} font-medium`}
		  >
		    {node.categoryName}: ₹{Number.isFinite(node.amount) ? Math.abs(node.amount).toFixed(2) : "0.00"}
		  </motion.div>
        </div>
        <AnimatePresence>
          {isExpanded && node.children && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              exit={{ opacity: 0, height: 0 }}
              transition={{ duration: 0.3 }}
            >
              {node.children.map((child) => renderCategoryTree(child, level + 1))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    );
  };

  return (
    <div className="max-w-3xl mx-auto">
      <h2 className="text-2xl font-bold flex items-center mb-6">
        📊 <span className="ml-2">Category Spend Summary</span>
      </h2>

      <div className="flex gap-4 mb-4">
        <select
          className="border rounded px-3 py-2"
          value={selectedMonth}
          onChange={(e) => setSelectedMonth(e.target.value)}
        >
          {months.map((m, i) => (
            <option key={i} value={i + 1}>{m}</option>
          ))}
        </select>
        <input
          type="number"
          className="border rounded px-3 py-2 w-24"
          value={selectedYear}
          onChange={(e) => setSelectedYear(e.target.value)}
        />
        <button
          className="bg-indigo-600 text-white px-4 py-2 rounded hover:bg-indigo-700"
          onClick={fetchData}
        >
          🔄 Refresh
        </button>
      </div>

      <div className="bg-white p-6 rounded-xl shadow-lg border border-gray-200">
	  {spendData
	    ? Object.values(spendData).map((node) => renderCategoryTree(node))
	    : <p>Loading...</p>}
      </div>
    </div>
  );
}