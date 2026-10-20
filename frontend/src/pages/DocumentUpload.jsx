import { useEffect, useState, useRef } from 'react';
import api, { uploadDocument, getDocuments } from '../api/incidentApi';
import {
  Upload,
  FileText,
  BookOpen,
  ExternalLink,
  CheckCircle2,
  XCircle,
  Clock,
  Loader2,
  Inbox,
  Sparkles,
} from 'lucide-react';
import clsx from 'clsx';

const STATUS_CONFIG = {
  UPLOADED: {
    label: 'Uploaded',
    icon: Clock,
    color: 'text-zinc-400',
    bg: 'bg-zinc-500/10',
    ring: 'ring-zinc-500/20',
  },
  PROCESSING: {
    label: 'Processing',
    icon: Loader2,
    color: 'text-amber-400',
    bg: 'bg-amber-500/10',
    ring: 'ring-amber-500/20',
    spin: true,
  },
  INDEXED: {
    label: 'Indexed',
    icon: CheckCircle2,
    color: 'text-emerald-400',
    bg: 'bg-emerald-500/10',
    ring: 'ring-emerald-500/20',
  },
  FAILED: {
    label: 'Failed',
    icon: XCircle,
    color: 'text-rose-400',
    bg: 'bg-rose-500/10',
    ring: 'ring-rose-500/20',
  },
};

function DocStatusBadge({ status }) {
  const config = STATUS_CONFIG[status] || STATUS_CONFIG.UPLOADED;
  const Icon = config.icon;
  return (
    <span className={clsx(
      'inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-semibold ring-1',
      config.bg, config.color, config.ring
    )}>
      <Icon size={12} className={config.spin ? 'animate-spin' : ''} />
      {config.label}
    </span>
  );
}

export default function DocumentUpload() {
  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);
  const [dragOver, setDragOver] = useState(false);
  const fileInputRef = useRef(null);

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

  async function processFile(file) {
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
    }
  }

  function handleFileInput(e) {
    processFile(e.target.files[0]);
    e.target.value = '';
  }

  function handleDrop(e) {
    e.preventDefault();
    setDragOver(false);
    const file = e.dataTransfer.files[0];
    if (file && (file.name.endsWith('.txt') || file.name.endsWith('.md') || file.name.endsWith('.pdf'))) {
      processFile(file);
    } else {
      setError('Only .txt, .md, and .pdf files are supported.');
    }
  }

  return (
    <div className="animate-fade-in">
      {/* Header */}
      <div className="mb-8">
        <div className="flex items-center gap-2 mb-1">
          <div className="p-1.5 rounded-lg bg-violet-500/10">
            <BookOpen size={18} className="text-violet-400" />
          </div>
          <h1 className="text-2xl font-bold text-zinc-50 tracking-tight">Knowledge Base</h1>
        </div>
        <p className="text-sm text-zinc-500 mt-2 max-w-xl">
          Upload postmortems and runbooks to train the AI. When new incidents occur, it will suggest resolutions based on similar past events.
        </p>
      </div>

      {/* Upload area */}
      <div
        onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleDrop}
        onClick={() => !uploading && fileInputRef.current?.click()}
        className={clsx(
          'relative bg-zinc-900 rounded-xl border-2 border-dashed p-8 mb-8 transition-all duration-200 text-center cursor-pointer',
          dragOver
            ? 'border-indigo-500/50 bg-indigo-500/[0.05]'
            : 'border-white/[0.08] hover:border-white/[0.15]'
        )}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".txt,.md,.text,.pdf,application/pdf"
          onChange={handleFileInput}
          disabled={uploading}
          className="hidden"
        />

        <div className="flex flex-col items-center">
          <div className={clsx(
            'w-14 h-14 rounded-2xl flex items-center justify-center mb-4 transition-colors',
            dragOver ? 'bg-indigo-500/20' : 'bg-zinc-800'
          )}>
            {uploading ? (
              <Loader2 size={24} className="text-indigo-400 animate-spin" />
            ) : (
              <Upload size={24} className={dragOver ? 'text-indigo-400' : 'text-zinc-500'} />
            )}
          </div>

          {uploading ? (
            <p className="text-sm text-zinc-400">Uploading and indexing...</p>
          ) : (
            <>
              <p className="text-sm font-medium text-zinc-300 mb-1">
                Click to upload or drop a file here
              </p>
              <p className="text-xs text-zinc-600">Supports .txt, .md, and .pdf files</p>
            </>
          )}
        </div>

        {/* Feedback messages */}
        {success && (
          <div className="mt-4 p-3 rounded-lg bg-emerald-500/10 border border-emerald-500/20 inline-flex items-center gap-2">
            <CheckCircle2 size={14} className="text-emerald-400" />
            <span className="text-sm text-emerald-400">{success}</span>
          </div>
        )}
        {error && (
          <div className="mt-4 p-3 rounded-lg bg-rose-500/10 border border-rose-500/20 inline-flex items-center gap-2">
            <XCircle size={14} className="text-rose-400" />
            <span className="text-sm text-rose-400">{error}</span>
          </div>
        )}
      </div>

      {/* Documents list */}
      <div className="bg-zinc-900 rounded-xl border border-white/[0.06] overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-white/[0.06]">
          <h3 className="text-sm font-semibold text-zinc-300 flex items-center gap-2">
            <Sparkles size={15} className="text-violet-400" />
            Indexed Documents
            {documents.length > 0 && (
              <span className="text-[11px] font-bold px-2 py-0.5 rounded-full bg-zinc-800 text-zinc-400">
                {documents.length}
              </span>
            )}
          </h3>
        </div>

        {documents.length === 0 ? (
          <div className="flex flex-col items-center py-16 text-center">
            <div className="p-3 rounded-full bg-zinc-800 mb-3">
              <Inbox size={24} className="text-zinc-500" />
            </div>
            <p className="text-sm font-medium text-zinc-400">No documents yet</p>
            <p className="text-xs text-zinc-600 mt-1">Upload postmortems and runbooks to get AI-powered suggestions</p>
          </div>
        ) : (
          <div className="divide-y divide-white/[0.04]">
            {documents.map((doc) => (
              <div key={doc.id} className="flex items-center gap-4 px-5 py-3.5 hover:bg-white/[0.02] transition-colors">
                <div className="w-9 h-9 rounded-lg bg-zinc-800 flex items-center justify-center shrink-0">
                  <FileText size={16} className="text-zinc-400" />
                </div>
                <div className="flex-1 min-w-0">
                  <a
                    href="#"
                    onClick={(e) => {
                      e.preventDefault();
                      api.get(`/documents/${doc.id}/download`)
                        .then((res) => window.open(res.data.url, '_blank'));
                    }}
                    className="text-sm font-medium text-zinc-200 hover:text-indigo-400 transition-colors inline-flex items-center gap-1.5"
                  >
                    {doc.originalFilename}
                    <ExternalLink size={12} className="text-zinc-600" />
                  </a>
                  <p className="text-[11px] text-zinc-600 mt-0.5">
                    {new Date(doc.uploadedAt).toLocaleString('en-US', {
                      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
                    })}
                  </p>
                </div>
                <DocStatusBadge status={doc.status} />
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
