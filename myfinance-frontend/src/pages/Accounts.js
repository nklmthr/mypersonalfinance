import React, { useEffect, useState } from "react";
import api from "./../auth/api";
import NProgress from "nprogress";
import "nprogress/nprogress.css";
import { useNavigate } from "react-router-dom";

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
		balance: "", // ✅ string to avoid input glitches
		institutionId: "",
		accountTypeId: "",
	});

	const [editData, setEditData] = useState({
		id: null,
		name: "",
		balance: "", 
		institutionId: "",
		accountTypeId: "",
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
			console.error("Fetch accounts error:", err);
		}
		finally {
			NProgress.done();
			setLoading(false);
		}
	};

	const handleAdd = async () => {
		setLoading(true);
		NProgress.start();
		try {
			await api.post("/accounts", {
				name: newAccount.name,
				balance: parseFloat(newAccount.balance), // ✅ parse on save
				institution: { id: newAccount.institutionId },
				accountType: { id: newAccount.accountTypeId },
			});
			setNewAccount({ name: "", balance: "", institutionId: "", accountTypeId: "" });
			setShowAddModal(false);
			fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
		} catch (err) {
			console.error("Add error:", err);
		}
		finally {
			NProgress.done();
			setLoading(false);
		}
	};

	const handleEditClick = (acc) => {
		setEditData({
			id: acc.id,
			name: acc.name,
			balance: acc.balance.toString(),
			institutionId: acc.institution?.id || "",
			accountTypeId: acc.accountType?.id || "",
		});
		setShowEditModal(true);
	};

	const handleEditSave = async () => {
		setLoading(true);
		NProgress.start();
		try {
			await api.put(`/accounts/${editData.id}`, {
				id: editData.id,
				name: editData.name,
				balance: parseFloat(editData.balance),
				institution: { id: editData.institutionId },
				accountType: { id: editData.accountTypeId },
			});
			setShowEditModal(false);
			fetchAccounts(selectedAccountTypeId, selectedInstitutionId);
		} catch (err) {
			console.error("Update error:", err);
		}
		finally {
			NProgress.done();
			setLoading(false);
		}
	};

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
			} else {
				alert("An unexpected error occurred.");
			}
			console.error("Delete error:", err);
		}
		finally {
			NProgress.done();
			setLoading(false);
		}
	};

	const inputClass = "border px-3 py-2 rounded w-full text-sm";

	const AccountModal = ({ isEdit = false }) => {
		const data = isEdit ? editData : newAccount;
		const setData = isEdit ? setEditData : setNewAccount;
		const saveFn = isEdit ? handleEditSave : handleAdd;

		useEffect(() => {
			const listener = (e) => {
				if (e.key === "Escape") {
					isEdit ? setShowEditModal(false) : setShowAddModal(false);
				}
				if (e.key === "Enter" && e.target.tagName !== "INPUT" && e.target.tagName !== "TEXTAREA" && e.target.tagName !== "SELECT") {
					e.preventDefault();
					saveFn();
				}
			};
			document.addEventListener("keydown", listener);
			return () => document.removeEventListener("keydown", listener);
		}, []);


		return (
			<div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
				<div className="bg-white p-6 rounded w-full max-w-lg space-y-4">
					<h3 className="text-lg font-semibold">{isEdit ? "Edit" : "Add"} Account</h3>
					<form onSubmit={(e) => { e.preventDefault(); saveFn(); }} className="space-y-4">

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
						<div className="flex justify-end space-x-2">
							<button type="button" onClick={() => (isEdit ? setShowEditModal(false) : setShowAddModal(false))} className="px-4 py-2 border rounded">Cancel</button>
							<button type="submit" className="px-4 py-2 bg-blue-600 text-white rounded">Save</button>
						</div>
					</form>
				</div>
			</div>
		);
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
							<th className="px-4 py-2 border-b text-center">Update</th>
							<th className="px-4 py-2 border-b text-center">Delete</th>
						</tr>
					</thead>
					<tbody>
						{accounts.map((acc, index) => (
							<tr key={acc.id} className={index % 2 === 0 ? "bg-white" : "bg-gray-50"}>
								<td className="px-4 py-2 border-b">
									<button
										onClick={() => navigate(`/transactions?accountId=${acc.id}`)}
										className="text-blue-600 hover:underline"
									>
										{acc.name}
									</button>
								</td>
								<td className="px-4 py-2 border-b text-right">
									₹{acc.balance.toLocaleString("en-IN", { minimumFractionDigits: 2 })}
								</td>
								<td className="px-4 py-2 border-b">{acc.accountType?.name}</td>
								<td className="px-4 py-2 border-b">{acc.institution?.name}</td>
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

			{showAddModal && <AccountModal />}
			{showEditModal && <AccountModal isEdit key={editData.id || "new"}/>}
			{loading && (
				<div className="fixed inset-0 bg-white bg-opacity-40 z-50 flex items-center justify-center">
					<div className="loader ease-linear rounded-full border-4 border-t-4 border-blue-500 h-10 w-10 animate-spin"></div>
				</div>
			)}
		</div>
		
	);
}
