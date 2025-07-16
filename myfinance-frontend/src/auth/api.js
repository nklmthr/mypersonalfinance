// axios.js or api.js
import axios from 'axios';
import { useNavigate } from 'react-router-dom';

// Create instance
const api = axios.create({
  baseURL: '/api',
  withCredentials: true, // required for session cookies
});

// Add a response interceptor
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      // Clear any auth-related storage if needed
      window.localStorage.removeItem('authToken'); // if using token
      window.location.href = '/login'; // force redirect
    }
    return Promise.reject(error);
  }
);

export default api;
