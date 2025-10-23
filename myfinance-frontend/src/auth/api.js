import axios from 'axios';
import { showModal } from './modalHandler';

const api = axios.create({
	baseURL: '/api',
	withCredentials: true, // required for session cookies
});

api.interceptors.request.use(
	(config) => {
		const token = localStorage.getItem('authToken');
		if (token) {
			config.headers.Authorization = `Bearer ${token}`;
		}
		return config;
	},
	(error) => Promise.reject(error)
);

api.interceptors.response.use(
	(response) => {
		const newToken = response.headers['x-auth-token'];
		if (newToken) {
			localStorage.setItem('authToken', newToken);
		}
		return response;
	},
	(error) => {
		console.error("AXIOS ERROR", error);
		if (error.response?.status === 401) {
			localStorage.removeItem('authToken');
			showModal('Session expired. Please log in again.');
			setTimeout(() => {
				window.location.href = '/login';
			}, 1);
			return Promise.reject(error);
		}
		if (error.response?.status === 400) {
			// Handle validation errors from backend
			const message = error.response.data?.error || error.response.data?.message || error.response.data || 'Bad request';
			showModal(`Validation Error: ${message}`);
			return Promise.reject(error);
		}
		if (
			error.code === 'ECONNREFUSED' ||
			error.message === 'Network Error' ||
			(!error.response && error.request)
		) {
			showModal('Unable to reach the server. Please try again later.');
		}
		if (error.response?.status === 500) {
			const message = error.response.data?.message || 'Server error';
			showModal(`Error: ${message}`);
			return Promise.resolve();
		}
		return Promise.reject(error);
	}
);
export default api;
