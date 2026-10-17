const SEVERITY_STYLES = {
  P1_CRITICAL: 'bg-red-600 text-white',
  P2_HIGH: 'bg-orange-500 text-white',
  P3_MEDIUM: 'bg-yellow-500 text-white',
  P4_LOW: 'bg-blue-400 text-white',
};

const SEVERITY_LABELS = {
  P1_CRITICAL: 'P1',
  P2_HIGH: 'P2',
  P3_MEDIUM: 'P3',
  P4_LOW: 'P4',
};

export default function SeverityBadge({ severity }) {
  return (
    <span
      className={`inline-block px-2 py-0.5 rounded text-xs font-bold ${SEVERITY_STYLES[severity] || 'bg-gray-400 text-white'}`}
    >
      {SEVERITY_LABELS[severity] || severity}
    </span>
  );
}
