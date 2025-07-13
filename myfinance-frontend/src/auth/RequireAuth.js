import React, { useState, useEffect } from "react";
import { Navigate, useLocation } from "react-router-dom";

export default function RequireAuth({ children }) {
  const location = useLocation();
  const [checked, setChecked] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  useEffect(() => {
    const token = !!localStorage.getItem("authToken");
    setIsAuthenticated(token);
    setChecked(true);
  }, []);

  if (!checked) return null; // wait for check

  if (!isAuthenticated) {
    console.log("Unauthenticated! Redirecting to /login");
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}
