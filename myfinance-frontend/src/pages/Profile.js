import React, { useEffect, useState } from "react";
import api from "./../auth/api";
import axios from 'axios';
import { useNavigate } from "react-router-dom";

export default function ProfilePage() {
	const [user, setUser] = useState(null);
	const [gmailConnected, setGmailConnected] = useState(false);
	const [loading, setLoading] = useState(false);

	// Email edit state
	const [editingEmail, setEditingEmail] = useState(false);
	const [emailDraft, setEmailDraft] = useState("");
	const [emailSaving, setEmailSaving] = useState(false);
	const [emailError, setEmailError] = useState(null);
	const [emailSuccess, setEmailSuccess] = useState(null);

	// Change password state
	const [pwForm, setPwForm] = useState({
		currentPassword: "",
		newPassword: "",
		confirmPassword: "",
	});
	const [pwSaving, setPwSaving] = useState(false);
	const [pwError, setPwError] = useState(null);
	const [pwSuccess, setPwSuccess] = useState(null);

	const navigate = useNavigate();

	const fetchProfile = async () => {
		try {
			const res = await api.get("/user/profile");
			setUser(res.data);
		} catch (err) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/login");
			} else {
				console.error("Failed to fetch user profile:", err);
			}
		}
	};

	const fetchGmailStatus = async () => {
		try {
			const res = await api.get("/gmail/status");
			setGmailConnected(res.data.connected);
		} catch (err) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				console.error("Failed to check Gmail status:", err);
			}
		}
	};

	const handleConnectGmail = async () => {
		try {
			const token = localStorage.getItem('authToken');
			const res = await axios.get("/gmail/authorize-url", {
				withCredentials: true,
				headers: token ? { Authorization: `Bearer ${token}` } : {}
			});
			window.location.href = res.data.url;
		} catch (err) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				alert("Failed to initiate Gmail connection");
				console.error(err);
			}
		}
	};

	const handleDisconnectGmail = async () => {
		try {
			setLoading(true);
			await api.post("/gmail/disconnect");
			setGmailConnected(false);
			alert("Gmail disconnected");
		} catch (err) {
			if (err.response?.status === 401) {
				localStorage.removeItem("authToken");
				navigate("/");
			} else {
				alert("Failed to disconnect Gmail");
				console.error(err);
			}
		} finally {
			setLoading(false);
		}
	};

	const startEditEmail = () => {
		setEmailDraft(user?.email || "");
		setEditingEmail(true);
		setEmailError(null);
		setEmailSuccess(null);
	};

	const cancelEditEmail = () => {
		setEditingEmail(false);
		setEmailDraft("");
		setEmailError(null);
	};

	const saveEmail = async () => {
		setEmailError(null);
		setEmailSuccess(null);

		const trimmed = (emailDraft || "").trim();
		const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
		if (!emailRegex.test(trimmed)) {
			setEmailError("Please enter a valid email address.");
			return;
		}

		setEmailSaving(true);
		try {
			const res = await api.put("/user/email", { email: trimmed });
			setUser((prev) => ({ ...prev, email: res.data?.email || trimmed }));
			setEditingEmail(false);
			setEmailSuccess("Email updated successfully.");
		} catch (err) {
			console.error("Failed to update email:", err);
			setEmailError(
				err.response?.data?.error ||
					err.response?.data?.message ||
					"Failed to update email. Please try again."
			);
		} finally {
			setEmailSaving(false);
		}
	};

	const submitChangePassword = async (e) => {
		e.preventDefault();
		setPwError(null);
		setPwSuccess(null);

		if (pwForm.newPassword !== pwForm.confirmPassword) {
			setPwError("New password and confirmation do not match.");
			return;
		}
		if (pwForm.newPassword.length < 6) {
			setPwError("New password must be at least 6 characters.");
			return;
		}
		const strongRegex = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/;
		if (!strongRegex.test(pwForm.newPassword)) {
			setPwError("New password must contain at least one uppercase letter, one lowercase letter, and one number.");
			return;
		}
		if (pwForm.newPassword === pwForm.currentPassword) {
			setPwError("New password must be different from the current password.");
			return;
		}

		setPwSaving(true);
		try {
			await api.post("/user/change-password", {
				currentPassword: pwForm.currentPassword,
				newPassword: pwForm.newPassword,
			});
			setPwSuccess("Password updated successfully.");
			setPwForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
		} catch (err) {
			console.error("Failed to change password:", err);
			setPwError(
				err.response?.data?.error ||
					err.response?.data?.message ||
					"Failed to change password. Please try again."
			);
		} finally {
			setPwSaving(false);
		}
	};

	useEffect(() => {
		fetchProfile();
		fetchGmailStatus();
	}, []);

	return (
		<div className="max-w-3xl mx-auto px-4 py-6">
			<h2 className="text-2xl font-bold text-blue-700 mb-6">👤 Profile</h2>

			{/* User Details */}
			<div className="bg-white shadow rounded p-6 space-y-4 border border-blue-100">
				{user ? (
					<>
						<div>
							<strong className="text-gray-700">Username:</strong>{" "}
							<span className="text-gray-800">{user.username}</span>
						</div>

						{/* Email row with inline edit */}
						<div>
							<strong className="text-gray-700">Email:</strong>{" "}
							{!editingEmail ? (
								<>
									<span className="text-gray-800">{user.email}</span>
									<button
										type="button"
										onClick={startEditEmail}
										className="ml-3 text-xs text-blue-600 hover:underline"
										title="Change email address"
									>
										✏️ Edit
									</button>
								</>
							) : (
								<div className="mt-2 flex flex-wrap items-center gap-2">
									<input
										type="email"
										value={emailDraft}
										onChange={(e) => setEmailDraft(e.target.value)}
										placeholder="you@example.com"
										disabled={emailSaving}
										autoFocus
										className="border px-3 py-1.5 rounded text-sm flex-1 min-w-[220px] focus:outline-none focus:ring-2 focus:ring-blue-500"
									/>
									<button
										type="button"
										onClick={saveEmail}
										disabled={emailSaving}
										className="bg-blue-600 text-white px-3 py-1.5 rounded text-sm hover:bg-blue-700 disabled:opacity-50"
									>
										{emailSaving ? "Saving..." : "Save"}
									</button>
									<button
										type="button"
										onClick={cancelEditEmail}
										disabled={emailSaving}
										className="bg-gray-100 text-gray-700 px-3 py-1.5 rounded text-sm hover:bg-gray-200 disabled:opacity-50"
									>
										Cancel
									</button>
								</div>
							)}
							{emailError && (
								<div className="mt-2 text-sm text-red-700 bg-red-50 border border-red-200 rounded px-3 py-1.5">
									{emailError}
								</div>
							)}
							{emailSuccess && !editingEmail && (
								<div className="mt-2 text-sm text-green-700 bg-green-50 border border-green-200 rounded px-3 py-1.5">
									{emailSuccess}
								</div>
							)}
						</div>

						{user.roles && (
							<div>
								<strong className="text-gray-700">Roles:</strong>{" "}
								<span className="text-gray-800">{user.roles.join(", ")}</span>
							</div>
						)}
					</>
				) : (
					<div className="text-gray-500">Loading user info...</div>
				)}
			</div>

			{/* Gmail Connection */}
			<div className="mt-8 bg-white shadow rounded p-6 border border-blue-100 space-y-4">
				<div className="flex justify-between items-center">
					<div>
						<h3 className="text-lg font-semibold text-blue-700">📧 Gmail Integration</h3>
						<p className="text-sm text-gray-600">
							{gmailConnected
								? "Your Gmail account is connected."
								: "You have not connected your Gmail account."}
						</p>
					</div>

					{gmailConnected ? (
						<button
							disabled={loading}
							onClick={handleDisconnectGmail}
							className="bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700 text-sm"
						>
							{loading ? "Disconnecting..." : "Disconnect Gmail"}
						</button>
					) : (
						<button
							onClick={handleConnectGmail}
							className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 text-sm"
						>
							Connect Gmail
						</button>
					)}
				</div>
			</div>

			{/* Change Password */}
			<div className="mt-8 bg-white shadow rounded p-6 border border-blue-100">
				<h3 className="text-lg font-semibold text-blue-700 mb-1">🔒 Change Password</h3>
				<p className="text-xs text-gray-500 mb-4">
					Must be at least 6 characters and contain at least one uppercase letter, one lowercase letter, and one number.
				</p>
				<form className="space-y-3" onSubmit={submitChangePassword}>
					<input
						type="password"
						placeholder="Current password"
						autoComplete="current-password"
						value={pwForm.currentPassword}
						onChange={(e) => setPwForm((p) => ({ ...p, currentPassword: e.target.value }))}
						disabled={pwSaving}
						required
						className="border px-3 py-2 rounded w-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
					/>
					<input
						type="password"
						placeholder="New password"
						autoComplete="new-password"
						value={pwForm.newPassword}
						onChange={(e) => setPwForm((p) => ({ ...p, newPassword: e.target.value }))}
						disabled={pwSaving}
						required
						className="border px-3 py-2 rounded w-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
					/>
					<input
						type="password"
						placeholder="Confirm new password"
						autoComplete="new-password"
						value={pwForm.confirmPassword}
						onChange={(e) => setPwForm((p) => ({ ...p, confirmPassword: e.target.value }))}
						disabled={pwSaving}
						required
						className="border px-3 py-2 rounded w-full text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
					/>

					{pwError && (
						<div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded px-3 py-2">
							{pwError}
						</div>
					)}
					{pwSuccess && (
						<div className="text-sm text-green-700 bg-green-50 border border-green-200 rounded px-3 py-2">
							{pwSuccess}
						</div>
					)}

					<div className="flex justify-end">
						<button
							type="submit"
							disabled={pwSaving}
							className="bg-blue-600 text-white px-4 py-2 rounded text-sm hover:bg-blue-700 disabled:opacity-50"
						>
							{pwSaving ? "Updating..." : "Change Password"}
						</button>
					</div>
				</form>
			</div>
		</div>
	);
}
