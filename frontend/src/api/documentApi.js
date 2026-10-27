import axios from "axios";

const documentApi = axios.create({
  baseURL: "/api/documents",
  headers: {
    "Content-Type": "application/json",
  },
});

// Attach JWT Bearer token to every outgoing request
documentApi.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ── Documents ──
export const uploadDocument = (file) => {
  const formData = new FormData();
  formData.append("file", file);
  return documentApi.post("", formData, {
    headers: { "Content-Type": "multipart/form-data" },
  });
};

export const getDocuments = () => documentApi.get("");

export default documentApi;
