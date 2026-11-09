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

// Same 401 handling as the incident API: an expired token (or one issued before organizations
// existed, which the document service rejects) sends the user back to sign in.
documentApi.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  },
);

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
