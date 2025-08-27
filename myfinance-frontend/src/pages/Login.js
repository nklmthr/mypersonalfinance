import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../auth/api";

export default function Login() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      console.log("Attempting login with", { username, password });

      await api.post("/auth/login", { username, password }); // <-- uses api instance

	  const response = await api.post("/auth/login", { username, password });
	  const token = response.data.token; 
	  localStorage.setItem("authToken", token);
	  console.log("Login success, token saved");
	  navigate("/");
    } catch (error) {
      alert("Invalid username or password");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex h-screen bg-gray-100 items-center justify-center">
      <div className="bg-white p-10 shadow-lg rounded-lg w-full max-w-md">
        <h2 className="text-2xl font-bold text-center mb-6 text-gray-800">
          Login to myFinance
        </h2>
        <form onSubmit={handleSubmit} className="space-y-4">
          <input
            className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            type="text"
            placeholder="Username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
          <input
            className="w-full px-4 py-2 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 text-white py-2 rounded-lg hover:bg-blue-700 transition"
          >
            {loading ? "Logging in..." : "Log In"}
          </button>
        </form>
        <div className="text-center mt-4">
          <span className="text-gray-600">Don't have an account? </span>
          <button
            onClick={() => navigate("/signup")}
            className="text-blue-600 hover:underline font-semibold"
          >
            Sign Up
          </button>
        </div>
      </div>
    </div>
  );
}
