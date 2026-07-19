import axios from 'axios';

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 15000
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('xniu_admin_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use((response) => {
  const body = response.data as { code?: number; message?: string; data?: unknown };
  if (body?.code === 0) {
    return body.data as never;
  }
  return Promise.reject(new Error(body?.message || '请求失败'));
});
