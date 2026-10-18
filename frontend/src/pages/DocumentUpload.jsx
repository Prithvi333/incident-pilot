import { useEffect, useState } from 'react';
import { uploadDocument, getDocuments } from '../api/incidentApi';

const STATUS_COLORS = {
  UPLOADED: 'bg-gray-100 text-gray-700',
  PROCESSING: 'bg-yellow-100 text-yellow-700',
  INDEXED: 'bg-green-100 text-green-700',
  FAILED: 'bg-red-100 text-red-700',
};

export default function DocumentUpload() {
  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  async function fetchDocuments() {
    try {
      const res = await getDocuments();
      setDocuments(res.data);
    } catch {
      setError('Failed to load documents.');
    }
  }

  useEffect(() => {
    fetchDocuments();
  }, []);

  async function handleUpload(e) {
    const file = e.target.files[0];
    if (!file) return;

    setUploading(true);
    setError(null);
    setSuccess(null);

    try {
      await uploadDocument(file);
      setSuccess(`"${file.name}" uploaded and indexed successfully.`);
      await fetchDocuments();
    } catch {
      setError('Failed to upload document.');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Knowledge Base</h1>
      <p className="text-gray-600 mb-4">
        Upload past postmortems and runbooks so the AI can suggest resolutions for new incidents.
      </p>

      {/* Upload area */}
      <div className="bg-white rounded-lg shadow p-6 mb-6">
        <label className="block">
          <span className="text-sm font-medium text-gray-700">Upload a document (.txt, .md)</span>
          <input
            type="file"
            accept=".txt,.md,.text"
            onChange={handleUpload}
            disabled={uploading}
            className="mt-2 block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded file:border-0 file:text-sm file:font-medium file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100 disabled:opacity-50"
          />
        </label>
        {uploading && <p className="text-sm text-gray-500 mt-2">Uploading and indexing...</p>}
        {success && <p className="text-sm text-green-600 mt-2">{success}</p>}
        {error && <p className="text-sm text-red-500 mt-2">{error}</p>}
      </div>

      {/* Document list */}
      <h2 className="text-lg font-semibold mb-3">Uploaded Documents</h2>
      {documents.length === 0 ? (
        <p className="text-gray-400 text-sm">No documents uploaded yet.</p>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">File</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Uploaded</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {documents.map((doc) => (
                <tr key={doc.id}>
                  <td className="px-4 py-3 text-sm font-medium">
                    <a href={doc.fileUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">
                      {doc.originalFilename}
                    </a>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-xs font-medium px-2 py-0.5 rounded ${STATUS_COLORS[doc.status] || ''}`}>
                      {doc.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500">
                    {new Date(doc.uploadedAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
