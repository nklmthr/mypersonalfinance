import React, { useEffect, useState } from "react";
import api from "./../auth/api";
import axios from "axios";

export default function ProfilePage() {
  const [user, setUser] = useState(null);
  const [gmailConnected, setGmailConnected] = useState(false);
  const [loading, setLoading] = useState(false);

  const fetchProfile = async () => {
    try {
      const res = await api.get("/user/profile"); // ← Adjust if needed
      setUser(res.data);
    } catch (err) {
      console.error("Failed to fetch user profile:", err);
    }
  };

  const fetchGmailStatus = async () => {
    try {
      const res = await api.get("/gmail/status");
      setGmailConnected(res.data.connected);
    } catch (err) {
      console.error("Failed to check Gmail status:", err);
    }
  };

  const handleConnectGmail = async () => {
    try {
      const res = await axios.get(window.location.origin + `/oauth/authorize?redirectOrigin`, {
        withCredentials: true,
      });
      window.location.href = res.data.authUrl;
    } catch (err) {
      alert("Failed to connect Gmail");
      console.error(err);
    }
  };

  const handleDisconnectGmail = async () => {
    try {
      setLoading(true);
      await api.post("/gmail/disconnect");
      setGmailConnected(false);
      alert("Gmail disconnected");
    } catch (err) {
      alert("Failed to disconnect Gmail");
      console.error(err);
    } finally {
      setLoading(false);
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
            <div>
              <strong className="text-gray-700">Email:</strong>{" "}
              <span className="text-gray-800">{user.email}</span>
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

      {/* Placeholder for password change */}
      {/* Uncomment when backend is ready
      <div className="mt-8 bg-white shadow rounded p-6 border border-blue-100">
        <h3 className="text-lg font-semibold text-blue-700 mb-4">🔒 Change Password</h3>
        <form className="space-y-4">
          <input
            type="password"
            placeholder="Current Password"
            className="border px-3 py-2 rounded w-full text-sm"
          />
          <input
            type="password"
            placeholder="New Password"
            className="border px-3 py-2 rounded w-full text-sm"
          />
          <input
            type="password"
            placeholder="Confirm New Password"
            className="border px-3 py-2 rounded w-full text-sm"
          />
          <button className="bg-blue-600 text-white px-4 py-2 rounded text-sm hover:bg-blue-700">
            Change Password
          </button>
        </form>
      </div>
      */}
    </div>
  );
}
