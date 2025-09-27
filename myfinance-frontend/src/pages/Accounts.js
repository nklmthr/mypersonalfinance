import React, { useEffect, useState, useCallback } from "react";
import api from "./../auth/api";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { useNavigate } from "react-router-dom";

const inputClass = "border px-3 py-2 rounded w-full text-sm";

// Separate AccountModal component to prevent re-renders
const AccountModal = React.memo(({ 
	isEdit, 
	data, 
	setData, 
	onSave, 
	onCancel, 
	institutions, 
	accountTypes 
}) => {
	useEffect(() => {
		const listener = (e) => {
			if (e.key === "Escape") {
				onCancel();
			}
			if (e.key === "Enter" && e.target.tagName !== "INPUT" && e.target.tagName !== "TEXTAREA" && e.target.tagName !== "SELECT") {
				e.preventDefault();
				onSave();
			}
		};
		document.addEventListener("keydown", listener);
		return () => document.removeEventListener("keydown", listener);
	}, [onSave, onCancel]);

	return (
		<div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
			<div className="bg-white p-6 rounded w-full max-w-lg space-y-4">
				<h3 className="text-lg font-semibold">{isEdit ? "Edit" : "Add"} Account</h3>
				<form onSubmit={(e) => { e.preventDefault(); onSave(); }} className="space-y-4">

					<input
						className={inputClass}
						placeholder="Name"
						value={data.name}
						onChange={(e) => setData({ ...data, name: e.target.value })}
					/>
					<div className="grid grid-cols-1 md:grid-cols-3 gap-3">
						<select className={inputClass} value={data.institutionId} onChange={(e) => setData({ ...data, institutionId: e.target.value })}>
							<option value="">Institution</option>
							{institutions.map((inst) => (
								<option key={inst.id} value={inst.id}>{inst.name}</option>
							))}
						</select>
						<select className={inputClass} value={data.accountTypeId} onChange={(e) => setData({ ...data, accountTypeId: e.target.value })}>
							<option value="">Account Type</option>
							{accountTypes.map((type) => (
								<option key={type.id} value={type.id}>{type.name}</option>
							))}
						</select>
						<input
							type="text"
							inputMode="decimal"
							autoFocus
							className={inputClass}
							placeholder="Balance"
							value={data.balance}
							onChange={(e) => setData({ ...data, balance: e.target.value })}
						/>
					</div>

					{/* Account Identifier Fields */}
					<div className="space-y-3 border-t pt-4">
						<h4 className="text-sm font-medium text-gray-700 flex items-center">
							<span className="mr-2">🤖</span> Transaction Matching Identifiers
						</h4>
						<input
							className={inputClass}
							placeholder="Account Number (e.g., XXXX1234, 5678)"
							value={data.accountNumber}
							onChange={(e) => setData({ ...data, accountNumber: e.target.value })}
							title="Account/card numbers that appear in transaction text"
						/>
						<input
							className={inputClass}
							placeholder="Keywords (comma-separated, e.g., hdfc,bank,savings,netbanking)"
							value={data.accountKeywords}
							onChange={(e) => setData({ ...data, accountKeywords: e.target.value })}
							title="Keywords that might appear in transaction text"
						/>
						<input
							className={inputClass}
							placeholder="Aliases (comma-separated, e.g., HDFC SAV,HDFC Savings)"
							value={data.accountAliases}
							onChange={(e) => setData({ ...data, accountAliases: e.target.value })}
							title="Alternative names or abbreviations for this account"
						/>
					</div>

					<div className="flex justify-end space-x-2">
						<button type="button" onClick={onCancel} className="px-4 py-2 border rounded">Cancel</button>
						<button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded">Save</button>
					</div>
				</form>
			</div>
		</div>
	);
});

export default function Accounts() {
	const [accounts, setAccounts] = useState([]);
	const [institutions, setInstitutions] = useState([]);
	const [accountTypes, setAccountTypes] = useState([]);
	const [selectedInstitutionId, setSelectedInstitutionId] = useState("");
	const [selectedAccountTypeId, setSelectedAccountTypeId] = useState("");
	const [loading, setLoading] = useState(false);
	const [showAddModal, setShowAddModal] = useState(false);
	const [showEditModal, setShowEditModal] = useState(false);
	const navigate = useNavigate();

	const [newAccount, setNewAccount] = useState({
		name: "",
		balance: "",
		institutionId: "",
		accountTypeId: "",
		accountNumber: "",
		accountKeywords: "",
		accountAliases: "",
	});

	useEffect(() => {
		const fetchDropdownData = async () => {
			try {
				const [instRes, typeRes] = await Promise.all([
					api.get("/institutions"),
					api.get("/account-types")
				]);
				setInstitutions(instRes.data);
				setAccountTypes(typeRes.data);
			} catch (err) {
				if (err.response?.status === 401) {
					localStorage.removeItem("authToken");
					navigate("/");
				} else {
					console.error("Failed to fetch user profile:", err);
				}
			}
		};
		fetchDropdownData();
	}, []);


	const [editData, setEditData] = useState({
		id: null,
		name: "",
		balance: "",
		institutionId: "",
		accountTypeId: "",
		accountNumber: "",
		accountKeywords: "",
		accountAliases: "",
	});

	NProgress.configure({ showSpinner: false });

	useEffect(() => {
		fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
	}, [selectedAccountTypeId, selectedInstitutionId]);

	const fetchAccounts = async (accountTypeId = "", institutionId = "") => {
		setLoading(true);
		NProgress.start();
		try {
			const params = {};
			if (accountTypeId) params.accountTypeId = accountTypeId;
			if (institutionId) params.institutionId = institutionId;
			const res = await api.get("/accounts/filter", { params });
			setAccounts(res.data);
		} catch (err) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to fetch user profile:", err);
			}
		}
		finally {
			NProgress.done();
			setLoading(false);
		}
	};

	const handleAdd = useCallback(async () => {
		setLoading(true);
		NProgress.start();
		try {
			await api.post("/accounts", {
				name: newAccount.name,
				balance: parseFloat(newAccount.balance), // ✅ parse on save
				institution: { id: newAccount.institutionId },
				accountType: { id: newAccount.accountTypeId },
				accountNumber: newAccount.accountNumber || null,
				accountKeywords: newAccount.accountKeywords || null,
				accountAliases: newAccount.accountAliases || null,
			});
			setNewAccount({ name: "", balance: "", institutionId: "", accountTypeId: "", accountNumber: "", accountKeywords: "", accountAliases: "" });
			setShowAddModal(false);
			fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
		} catch (err) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to fetch user profile:", err);
			}
		}
		finally {
			NProgress.done();
			setLoading(false);
		}
	}, [newAccount, selectedAccountTypeId, selectedInstitutionId, navigate]);

	const handleEditClick = (acc) => {
		setEditData({
			id: acc.id,
			name: acc.name,
			balance: acc.balance.toString(),
			institutionId: acc.institution?.id || "",
			accountTypeId: acc.accountType?.id || "",
			accountNumber: acc.accountNumber || "",
			accountKeywords: acc.accountKeywords || "",
			accountAliases: acc.accountAliases || "",
		});
		setShowEditModal(true);
	};

	const handleEditSave = useCallback(async () => {
		setLoading(true);
		NProgress.start();
		try {
			await api.put(`/accounts/${editData.id}`, {
				id: editData.id,
				name: editData.name,
				balance: parseFloat(editData.balance),
				institution: { id: editData.institutionId },
				accountType: { id: editData.accountTypeId },
				accountNumber: editData.accountNumber || null,
				accountKeywords: editData.accountKeywords || null,
				accountAliases: editData.accountAliases || null,
			});
			setShowEditModal(false);
			fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
		} catch (err) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to fetch user profile:", err);
			}
		}
		finally {
			NProgress.done();
			setLoading(false);
		}
	}, [editData, selectedAccountTypeId, selectedInstitutionId, navigate]);

	const handleDelete = async (id) => {
		if (!window.confirm("Delete this account?")) return;
		setLoading(true);
		NProgress.start();
		try {
			await api.delete(`/accounts/${id}`);
			fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
		} catch (err) {
			if (err.response && err.response.status === 409) {
				alert(err.response.data || "Cannot delete: Transactions exist for this account.");
			} else if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to fetch user profile:", err);
			}
		}
		finally {
			NProgress.done();
			setLoading(false);
		}
	};

	return (
		<div className="space-y-6">
			<div className="flex justify-between items-center">
				<h2 className="text-xl font-semibold">💼 Accounts</h2>
				<button onClick={() => setShowAddModal(true)} className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
					➕ Add Account
				</button>
			</div>

			<div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4">
				<div>
					<label className="block text-sm font-medium mb-1">Account Type</label>
					<select className={inputClass} value={selectedAccountTypeId} onChange={(e) => setSelectedAccountTypeId(e.target.value)}>
						<option value="">All</option>
						{accountTypes.map((type) => (
							<option key={type.id} value={type.id}>{type.name}</option>
						))}
					</select>
				</div>
				<div>
					<label className="block text-sm font-medium mb-1">Institution</label>
					<select className={inputClass} value={selectedInstitutionId} onChange={(e) => setSelectedInstitutionId(e.target.value)}>
						<option value="">All</option>
						{institutions.map((inst) => (
							<option key={inst.id} value={inst.id}>{inst.name}</option>
						))}
					</select>
				</div>
			</div>

			<div className="overflow-x-auto rounded shadow bg-white">
				<table className="min-w-full text-sm text-gray-700">
					<thead className="bg-gray-100 text-left">
						<tr>
							<th className="px-4 py-2 border-b">Name</th>
							<th className="px-4 py-2 border-b text-right">Balance</th>
							<th className="px-4 py-2 border-b">Account Type</th>
							<th className="px-4 py-2 border-b">Institution</th>
							<th className="px-4 py-2 border-b">🤖 AI Identifiers</th>
							<th className="px-4 py-2 border-b text-center">Update</th>
							<th className="px-4 py-2 border-b text-center">Delete</th>
						</tr>
					</thead>
					<tbody>
						{accounts.map((acc, index) => (
							<tr key={acc.id} className={index % 2 === 0 ? "bg-white" : "bg-gray-50"}>
								<td className="px-4 py-2 border-b">
									<div className="flex items-center gap-2">
										<button
											onClick={() => navigate(`/transactions?accountId=${acc.id}`)}
											className="text-blue-600 hover:underline"
										>
											{acc.name}
										</button>
										{(acc.accountNumber || acc.accountKeywords || acc.accountAliases) && (
											<span 
												className="text-xs bg-blue-100 text-blue-700 px-1 rounded" 
												title={`AI Matching Configured: ${[
													acc.accountNumber && 'Account Number',
													acc.accountKeywords && 'Keywords', 
													acc.accountAliases && 'Aliases'
												].filter(Boolean).join(', ')}`}
											>
												🤖
											</span>
										)}
									</div>
								</td>
								<td className="px-4 py-2 border-b text-right">
									₹{acc.balance.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
								</td>
								<td className="px-4 py-2 border-b">{acc.accountType?.name}</td>
								<td className="px-4 py-2 border-b">{acc.institution?.name}</td>
								<td className="px-4 py-2 border-b">
									<div className="space-y-1 text-xs">
										{acc.accountNumber && (
											<div className="bg-blue-50 text-blue-700 px-2 py-1 rounded">
												<strong>Number:</strong> {acc.accountNumber}
											</div>
										)}
										{acc.accountKeywords && (
											<div className="bg-green-50 text-green-700 px-2 py-1 rounded">
												<strong>Keywords:</strong> {acc.accountKeywords.length > 30 ? acc.accountKeywords.substring(0, 30) + '...' : acc.accountKeywords}
											</div>
										)}
										{acc.accountAliases && (
											<div className="bg-purple-50 text-purple-700 px-2 py-1 rounded">
												<strong>Aliases:</strong> {acc.accountAliases.length > 30 ? acc.accountAliases.substring(0, 30) + '...' : acc.accountAliases}
											</div>
										)}
										{!acc.accountNumber && !acc.accountKeywords && !acc.accountAliases && (
											<span className="text-gray-400 italic">Not configured</span>
										)}
									</div>
								</td>
								<td className="px-4 py-2 border-b text-center">
									<button onClick={() => handleEditClick(acc)} className="text-blue-600 font-medium hover:underline">Edit</button>
								</td>
								<td className="px-4 py-2 border-b text-center">
									<button onClick={() => handleDelete(acc.id)} className="text-red-600 font-medium hover:underline">Delete</button>
								</td>
							</tr>
						))}
					</tbody>
				</table>
			</div>

			{showAddModal && (
				<AccountModal 
					isEdit={false}
					data={newAccount}
					setData={setNewAccount}
					onSave={handleAdd}
					onCancel={() => setShowAddModal(false)}
					institutions={institutions}
					accountTypes={accountTypes}
				/>
			)}
			{showEditModal && (
				<AccountModal 
					isEdit={true}
					data={editData}
					setData={setEditData}
					onSave={handleEditSave}
					onCancel={() => setShowEditModal(false)}
					institutions={institutions}
					accountTypes={accountTypes}
				/>
			)}
			{loading && (
				<div className="fixed inset-0 bg-white bg-opacity-40 z-50 flex items-center justify-center">
					<div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-500 h-10 w-10 animate-spin"></div>
				</div>
			)}
		</div>

	);
}
