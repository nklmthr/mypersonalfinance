// axios.js or api.js
import axios from 'axios';
import { showModal } from './modalHandler';
// Create instance
const api = axios.create({
	baseURL: '/api',
	withCredentials: true, // required for session cookies
});

api.interceptors.response.use(
	(response) => response,
	(error) => {
		if (error.response?.status === 401) {
			localStorage.removeItem('authToken');
			showModal('Session expired. Please log in again.');
			setTimeout(() => {
					window.location.href = '/login';
				}, 1000); 
			return;
		}

		if (
			error.code === 'ECONNREFUSED' ||
			error.message === 'Network Error' ||
			(!error.response && error.request)
		) {
			showModal('Unable to reach the server. Please try again later.');
		}

		if (error.response?.status === 500) {
			showModal('Something went wrong on the server. Please try again later.');
		}
		return Promise.reject(error);
	}
);

export default api;
