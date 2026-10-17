const STATUS_STYLES = {
  TRIGGERED: 'bg-red-100 text-red-800',
  ACKNOWLEDGED: 'bg-yellow-100 text-yellow-800',
  INVESTIGATING: 'bg-blue-100 text-blue-800',
  RESOLVED: 'bg-green-100 text-green-800',
  POSTMORTEM: 'bg-gray-100 text-gray-800',
};

export default function StatusBadge({ status }) {
  return (
    <span
      className={`inline-block px-2.5 py-0.5 rounded-full text-xs font-medium ${STATUS_STYLES[status] || 'bg-gray-100 text-gray-800'}`}
    >
      {status}
    </span>
  );
}
