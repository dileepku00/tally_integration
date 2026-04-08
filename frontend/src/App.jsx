import { useState, useEffect } from 'react'
import * as XLSX from 'xlsx'
import { jsPDF } from 'jspdf'
import html2canvas from 'html2canvas'
import './App.css'

function App() {
  const [ledgers, setLedgers] = useState([]);
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0]);
  const [filterGroup, setFilterGroup] = useState('');
  const [filterLedger, setFilterLedger] = useState('');
  const [filterAssigned, setFilterAssigned] = useState('');
  const [filterHasComment, setFilterHasComment] = useState('');
  const [groups, setGroups] = useState([]);
  const [expandedComments, setExpandedComments] = useState({});
  const [newComment, setNewComment] = useState('');
  const [selectedLedgerIndex, setSelectedLedgerIndex] = useState(null);
  const [showCommentModal, setShowCommentModal] = useState(false);
  const [showStartupModal, setShowStartupModal] = useState(false);
  const [copyStatus, setCopyStatus] = useState('');

  useEffect(() => {
    fetchLedgers();
  }, []);

  useEffect(() => {
    const uniqueGroups = [...new Set(ledgers.map(l => l.group))].sort();
    setGroups(uniqueGroups);
  }, [ledgers]);

  const fetchLedgers = async (date = null) => {
    try {
      const url = date ? `/api/ledgers?date=${date}` : '/api/ledgers';
      const response = await fetch(url);
      const data = await response.json();
      setLedgers(data);
    } catch (error) {
      console.error('Error fetching ledgers:', error);
    }
  };

  const handleDateChange = (e) => {
    const date = e.target.value;
    setSelectedDate(date);
    fetchLedgers(date);
  };

  const saveLedgers = async (updatedLedgers) => {
    try {
      await fetch('/api/ledgers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updatedLedgers)
      });
    } catch (error) {
      console.error('Error saving ledgers:', error);
    }
  };

  const handleChange = (index, field, value) => {
    const updated = [...ledgers];
    updated[index][field] = value;
    setLedgers(updated);
    saveLedgers(updated);
  };

  const addComment = (index) => {
    if (!newComment.trim()) return;
    
    const updated = [...ledgers];
    const ledger = updated[index];
    
    // Initialize comments array if not exists
    if (!ledger.comments) {
      ledger.comments = [];
    }
    
    const timestamp = new Date().toLocaleString();
    ledger.comments.push({
      text: newComment,
      timestamp: timestamp
    });
    
    // Update comment field with latest comment
    ledger.comment = newComment;
    ledger.date = new Date().toISOString().split('T')[0];
    
    setLedgers(updated);
    saveLedgers(updated);
    setNewComment('');
    setShowCommentModal(false);
  };

  const toggleCommentHistory = (index) => {
    setExpandedComments(prev => ({
      ...prev,
      [index]: !prev[index]
    }));
  };

  const openCommentModal = (index) => {
    setSelectedLedgerIndex(index);
    setNewComment('');
    setShowCommentModal(true);
  };

  const exportToExcel = () => {
    const dataToExport = filteredLedgers.map(ledger => ({
      'Ledger Name': ledger.name,
      'Group': ledger.group,
      'Opening': ledger.opening,
      'Debit': ledger.debit,
      'Credit': ledger.credit,
      'Closing': ledger.closing,
      'Assigned To': ledger.assigned || '',
      'Latest Comment': ledger.comment || '',
      'Date': ledger.date || ''
    }));

    const worksheet = XLSX.utils.json_to_sheet(dataToExport);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Ledgers');
    XLSX.writeFile(workbook, `Tally_Ledgers_${selectedDate}.xlsx`);
  };

  const exportToPDF = async () => {
    const element = document.getElementById('table-to-export');
    if (!element) return;

    try {
      const canvas = await html2canvas(element);
      const imgData = canvas.toDataURL('image/png');
      const pdf = new jsPDF('l', 'mm', 'a4');
      const imgWidth = 297;
      const imgHeight = (canvas.height * imgWidth) / canvas.width;
      
      pdf.addImage(imgData, 'PNG', 0, 0, imgWidth, imgHeight);
      pdf.save(`Tally_Ledgers_${selectedDate}.pdf`);
    } catch (error) {
      console.error('Error generating PDF:', error);
    }
  };

  const backendCommand = `set BACKEND_PORT=8090\ncd "d:\\git hub\\tally_integration\\backend"\nrun.bat`;
  const frontendCommand = `cd "d:\\git hub\\tally_integration\\frontend"\nnpm run dev`;

  const copyToClipboard = async (text) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopyStatus('Copied to clipboard');
      setTimeout(() => setCopyStatus(''), 2000);
    } catch (error) {
      console.error('Clipboard copy failed:', error);
    }
  };

  const filteredLedgers = ledgers.filter(ledger => {
    if (filterGroup && ledger.group !== filterGroup) return false;
    if (filterLedger && !ledger.name.toLowerCase().includes(filterLedger.toLowerCase())) return false;
    if (filterAssigned && ledger.assigned !== filterAssigned) return false;
    if (filterHasComment === 'yes' && !ledger.comment) return false;
    if (filterHasComment === 'no' && ledger.comment) return false;
    return true;
  });

  const stats = {
    total: ledgers.length,
    filtered: filteredLedgers.length,
    withComment: ledgers.filter(l => l.comment).length,
    assigned: ledgers.filter(l => l.assigned).length,
    withBalance: ledgers.filter(l => l.closing !== 0).length
  };

  return (
    <div className="app-container">
      <div className="header">
        <h1>📊 Tally Ledger Dashboard</h1>
        <p className="subtitle">Manage and resolve ledger balances</p>
      </div>

      <div className="stats-row">
        <div className="stat-card">
          <div className="stat-number">{stats.total}</div>
          <div className="stat-label">Total Ledgers</div>
        </div>
        <div className="stat-card">
          <div className="stat-number">{stats.withBalance}</div>
          <div className="stat-label">With Balance</div>
        </div>
        <div className="stat-card">
          <div className="stat-number">{stats.assigned}</div>
          <div className="stat-label">Assigned</div>
        </div>
        <div className="stat-card">
          <div className="stat-number">{stats.withComment}</div>
          <div className="stat-label">Commented</div>
        </div>
        <div className="stat-card">
          <div className="stat-number">{stats.filtered}</div>
          <div className="stat-label">Filtered</div>
        </div>
      </div>

      <div className="controls-section">
        <div className="control-group">
          <label>As On Date:</label>
          <input
            type="date"
            value={selectedDate}
            onChange={handleDateChange}
          />
          <button className="btn-primary" onClick={() => fetchLedgers()}>🔄 Refresh</button>
          <button className="btn-secondary" onClick={() => setShowStartupModal(true)}>⚙️ Start Helper</button>
          <button className="btn-export" onClick={exportToExcel} title="Download as Excel">📥 Excel</button>
          <button className="btn-export" onClick={exportToPDF} title="Download as PDF">📄 PDF</button>
        </div>
      </div>

      <div className="filter-section">
        <h3>🔍 Filters</h3>
        <div className="filter-row">
          <div className="filter-item">
            <label>Group:</label>
            <select value={filterGroup} onChange={(e) => setFilterGroup(e.target.value)}>
              <option value="">All Groups</option>
              {groups.map(g => (
                <option key={g} value={g}>{g}</option>
              ))}
            </select>
          </div>
          <div className="filter-item">
            <label>Ledger Name:</label>
            <input
              type="text"
              placeholder="Search ledger..."
              value={filterLedger}
              onChange={(e) => setFilterLedger(e.target.value)}
            />
          </div>
          <div className="filter-item">
            <label>Assigned To:</label>
            <input
              type="text"
              placeholder="Person name..."
              value={filterAssigned}
              onChange={(e) => setFilterAssigned(e.target.value)}
            />
          </div>
          <div className="filter-item">
            <label>Has Comment:</label>
            <select value={filterHasComment} onChange={(e) => setFilterHasComment(e.target.value)}>
              <option value="">Any</option>
              <option value="yes">Yes</option>
              <option value="no">No</option>
            </select>
          </div>
          <button className="btn-secondary" onClick={() => {
            setFilterGroup('');
            setFilterLedger('');
            setFilterAssigned('');
            setFilterHasComment('');
          }}>✕ Clear Filters</button>
        </div>
      </div>

      <div className="table-section">
        <div className="table-header">
          <h3>Ledger Details ({filteredLedgers.length})</h3>
        </div>
        <div className="table-wrapper" id="table-to-export">
          <table>
            <thead>
              <tr>
                <th>Ledger Name</th>
                <th>Group</th>
                <th>Opening</th>
                <th>Debit</th>
                <th>Credit</th>
                <th>Closing</th>
                <th>Assigned To</th>
                <th>Comments</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              {filteredLedgers.length > 0 ? (
                filteredLedgers.map((ledger, index) => {
                  const actualIndex = ledgers.indexOf(ledger);
                  return (
                    <tr key={index} className={ledger.closing !== 0 ? 'highlight' : ''}>
                      <td className="ledger-name"><strong>{ledger.name}</strong></td>
                      <td className="group-badge">{ledger.group}</td>
                      <td className="amount">{typeof ledger.opening === 'number' ? ledger.opening.toFixed(2) : ledger.opening}</td>
                      <td className="amount debit">{typeof ledger.debit === 'number' ? ledger.debit.toFixed(2) : ledger.debit}</td>
                      <td className="amount credit">{typeof ledger.credit === 'number' ? ledger.credit.toFixed(2) : ledger.credit}</td>
                      <td className="amount closing"><strong>{typeof ledger.closing === 'number' ? ledger.closing.toFixed(2) : ledger.closing}</strong></td>
                      <td>
                        <input
                          type="text"
                          className="input-field"
                          placeholder="Name..."
                          value={ledger.assigned || ''}
                          onChange={(e) => handleChange(actualIndex, 'assigned', e.target.value)}
                        />
                      </td>
                      <td className="comments-cell">
                        <button 
                          className="btn-comment"
                          onClick={() => openCommentModal(actualIndex)}
                          title="Add comment"
                        >
                          💬 {ledger.comments && ledger.comments.length > 0 ? `(${ledger.comments.length})` : 'Add'}
                        </button>
                        {expandedComments[actualIndex] && ledger.comments && ledger.comments.length > 0 && (
                          <div className="comment-history">
                            {ledger.comments.map((c, i) => (
                              <div key={i} className="comment-item">
                                <div className="comment-text">{c.text}</div>
                                <div className="comment-time">{c.timestamp}</div>
                              </div>
                            ))}
                          </div>
                        )}
                        {ledger.comments && ledger.comments.length > 0 && (
                          <button 
                            className="btn-expand"
                            onClick={() => toggleCommentHistory(actualIndex)}
                          >
                            {expandedComments[actualIndex] ? '▼' : '▶'}
                          </button>
                        )}
                      </td>
                      <td className="date-field">{ledger.date}</td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td colSpan="9" className="no-data">No ledgers match the filters</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {showStartupModal && (
        <div className="modal-overlay" onClick={() => setShowStartupModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Startup Helper</h3>
              <button className="modal-close" onClick={() => setShowStartupModal(false)}>✕</button>
            </div>
            <div className="modal-body">
              <p>Use these commands to start the backend and frontend from your Windows terminal.</p>
              <div className="startup-section">
                <h4>Backend</h4>
                <pre>{backendCommand}</pre>
                <button className="btn-export" onClick={() => copyToClipboard(backendCommand)}>Copy Backend Command</button>
              </div>
              <div className="startup-section">
                <h4>Frontend</h4>
                <pre>{frontendCommand}</pre>
                <button className="btn-export" onClick={() => copyToClipboard(frontendCommand)}>Copy Frontend Command</button>
              </div>
              {copyStatus && <div className="copy-status">{copyStatus}</div>}
            </div>
            <div className="modal-footer">
              <button className="btn-secondary" onClick={() => setShowStartupModal(false)}>
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {showCommentModal && (
        <div className="modal-overlay" onClick={() => setShowCommentModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Add Comment</h3>
              <button className="modal-close" onClick={() => setShowCommentModal(false)}>✕</button>
            </div>
            <div className="modal-body">
              <p><strong>Ledger:</strong> {ledgers[selectedLedgerIndex]?.name}</p>
              {ledgers[selectedLedgerIndex]?.comments && ledgers[selectedLedgerIndex].comments.length > 0 && (
                <div className="comment-history-modal">
                  <h4>Previous Comments:</h4>
                  {ledgers[selectedLedgerIndex].comments.map((c, i) => (
                    <div key={i} className="comment-item">
                      <div className="comment-text">{c.text}</div>
                      <div className="comment-time">{c.timestamp}</div>
                    </div>
                  ))}
                </div>
              )}
              <textarea
                className="comment-input"
                placeholder="Type your comment here..."
                value={newComment}
                onChange={(e) => setNewComment(e.target.value)}
                rows="4"
              />
            </div>
            <div className="modal-footer">
              <button className="btn-primary" onClick={() => addComment(selectedLedgerIndex)}>
                ✓ Add Comment
              </button>
              <button className="btn-secondary" onClick={() => setShowCommentModal(false)}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default App