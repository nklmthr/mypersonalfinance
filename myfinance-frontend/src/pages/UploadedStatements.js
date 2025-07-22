import React, { useState, useEffect } from "react";
import api from "../auth/api";
import dayjs from "dayjs";
import { toast } from "react-toastify";

export default function StatementUploadPage() {
  const [statements, setStatements] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [selectedAccountId, setSelectedAccountId] = useState("");
  const [accountFilter, setAccountFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [loading, setLoading] = useState(false);
  const [file, setFile] = useState(null);

  const fetchStatements = async () => {
    setLoading(true);
    try {
      const res = await api.get("/uploaded-statements");
      setStatements(res.data);
    } catch (err) {
      toast.error("Failed to fetch statements");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const fetchAccounts = async () => {
    try {
      const res = await api.get("/accounts");
      setAccounts(res.data);
    } catch (err) {
      toast.error("Failed to fetch accounts");
    }
  };

  useEffect(() => {
    fetchStatements();
    fetchAccounts();
  }, []);

  const handleDeleteStatement = async (id) => {
    if (!window.confirm("Delete this uploaded statement (not just transactions)?")) return;
    try {
      setLoading(true);
      await api.delete(`/uploaded-statements/${id}`);
      toast.success("Statement deleted");
      fetchStatements();
    } catch (err) {
      toast.error("Delete failed");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };
  
  const handleUpload = async () => {
    if (!file || !selectedAccountId) {
      toast.error("File and account are required");
      return;
    }

    const formData = new FormData();
    formData.append("file", file);
    formData.append("accountId", selectedAccountId);

    try {
      setLoading(true);
      await api.post("/uploaded-statements/upload", formData);
      toast.success("Upload successful");
      setFile(null);
      setSelectedAccountId("");
      fetchStatements();
    } catch (err) {
      toast.error("Upload failed");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleProcess = async (id) => {
    try {
      setLoading(true);
      await api.post(`/uploaded-statements/${id}/process`);
      toast.success("Processing complete");
      fetchStatements();
    } catch (err) {
      toast.error("Processing failed");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteTransactions = async (id) => {
    if (!window.confirm("Delete all transactions from this file?")) return;
    try {
      setLoading(true);
      await api.delete(`/uploaded-statements/${id}/transactions`);
      toast.success("Transactions deleted");
      fetchStatements();
    } catch (err) {
      toast.error("Delete failed");
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const filteredStatements = statements.filter((s) => {
    return (!statusFilter || s.status === statusFilter) &&
           (!accountFilter || s.account?.id === accountFilter);
  });

  return (
    <div className="max-w-5xl mx-auto px-4 py-6 space-y-6">
      <h2 className="text-2xl font-bold text-gray-800 flex items-center gap-2">
        📄 Statement Upload & Processing
      </h2>

      {/* Upload Controls */}
      <div className="flex flex-col sm:flex-row items-center gap-4">
        <input
          type="file"
          onChange={(e) => setFile(e.target.files[0])}
          className="border p-2 rounded w-full sm:w-auto"
        />
        <select
          className="border rounded px-3 py-2"
          value={selectedAccountId}
          onChange={(e) => setSelectedAccountId(e.target.value)}
        >
          <option value="">Select Account</option>
          {accounts.map((acc) => (
            <option key={acc.id} value={acc.id}>
              {acc.name}
            </option>
          ))}
        </select>
        <button
          onClick={handleUpload}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          Upload
        </button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4 items-center">
        <select
          className="border rounded px-3 py-2"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
        >
          <option value="">All Status</option>
          <option value="UPLOADED">Uploaded</option>
          <option value="PROCESSED">Processed</option>
        </select>

        <select
          className="border rounded px-3 py-2"
          value={accountFilter}
          onChange={(e) => setAccountFilter(e.target.value)}
        >
          <option value="">All Accounts</option>
          {accounts.map((acc) => (
            <option key={acc.id} value={acc.id}>{acc.name}</option>
          ))}
        </select>
      </div>

      {/* Table */}
      <div className="bg-white border rounded shadow-sm overflow-x-auto">
        <table className="min-w-full text-sm text-gray-800">
          <thead className="bg-gray-100">
            <tr>
              <th className="px-4 py-2 border-b">File Name</th>
              <th className="px-4 py-2 border-b">Account</th>
              <th className="px-4 py-2 border-b">Uploaded At</th>
              <th className="px-4 py-2 border-b">Status</th>
              <th className="px-4 py-2 border-b text-center">Transactions</th>
              <th className="px-4 py-2 border-b text-center">Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredStatements.map((s) => (
              <tr key={s.id} className="border-t hover:bg-gray-50">
                <td className="px-4 py-2">{s.filename}</td>
                <td className="px-4 py-2">{s.account?.name || "—"}</td>
                <td className="px-4 py-2">{dayjs(s.uploadedAt).format("DD MMM YYYY, HH:mm")}</td>
                <td className="px-4 py-2">{s.status}</td>
                <td className="px-4 py-2 text-center">{s.transactionCount ?? 0}</td>
                <td className="px-4 py-2 text-center space-x-2">
                  {s.status === "UPLOADED" && (
                    <button
                      className="text-blue-600 hover:underline"
                      onClick={() => handleProcess(s.id)}
                    >
                      Process
                    </button>
                  )}
                  {s.status === "PROCESSED" && (
                    <button
                      className="text-red-600 hover:underline"
                      onClick={() => handleDeleteTransactions(s.id)}
                    >
                      Delete Txns
                    </button>
                  )}
				  {["UPLOADED", "PROCESSED"].includes(s.status) && (
				    <button
				      className="text-red-500 hover:underline"
				      onClick={() => handleDeleteStatement(s.id)}
				    >
				      Delete
				    </button>
				  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Loading Spinner */}
      {loading && (
        <div className="fixed inset-0 bg-white bg-opacity-50 flex items-center justify-center z-50">
          <div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-600 h-10 w-10 animate-spin"></div>
        </div>
      )}
    </div>
  );
}
