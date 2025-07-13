import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function Logout() {
  const navigate = useNavigate();

  useEffect(() => {
    // Clear local storage or any auth token/session here
    localStorage.removeItem("authToken"); // Or whatever you used to store session
    localStorage.removeItem("username");  // Optional: if you're storing username
    
    // Redirect to login
    navigate("/login");
  }, [navigate]);

  return null;
}
