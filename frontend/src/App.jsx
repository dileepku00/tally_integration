import { useEffect, useMemo, useState } from 'react'
import * as XLSX from 'xlsx'
import { jsPDF } from 'jspdf'
import html2canvas from 'html2canvas'
import './App.css'

const TEAM_STATUS_OPTIONS = [
  'work in progress',
  'pending for approval',
  'closed',
  'balance reconcilled',
]

const STATUS_META = {
  'work in progress': { label: 'Work In Progress', color: '#d97706' },
  'pending for approval': { label: 'Pending For Approval', color: '#2563eb' },
  closed: { label: 'Closed', color: '#059669' },
  'balance reconcilled': { label: 'Balance Reconcilled', color: '#7c3aed' },
}

const defaultConnection = { host: 'localhost', port: '9000' }

function App() {
  const [ledgers, setLedgers] = useState([])
  const [selectedDate, setSelectedDate] = useState(new Date().toISOString().split('T')[0])
  const [selectedFirm, setSelectedFirm] = useState('')
  const [firms, setFirms] = useState([])
  const [groups, setGroups] = useState([])
  const [connection, setConnection] = useState(defaultConnection)
  const [tallyPath, setTallyPath] = useState('C:\\Program Files\\TallyPrime\\tally.exe')
  const [filterGroup, setFilterGroup] = useState('')
  const [filterLedger, setFilterLedger] = useState('')
  const [filterAssigned, setFilterAssigned] = useState('')
  const [filterLedgerStatus, setFilterLedgerStatus] = useState('')
  const [filterStatus, setFilterStatus] = useState('')
  const [filterHasComment, setFilterHasComment] = useState('')
  const [expandedComments, setExpandedComments] = useState({})
  const [newComment, setNewComment] = useState('')
  const [selectedLedgerIndex, setSelectedLedgerIndex] = useState(null)
  const [showCommentModal, setShowCommentModal] = useState(false)
  const [showStartupModal, setShowStartupModal] = useState(false)
  const [copyStatus, setCopyStatus] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [banner, setBanner] = useState('')
  const [error, setError] = useState('')
  const [connectionTest, setConnectionTest] = useState(null)

  useEffect(() => {
    initializeApp()
  }, [])

  useEffect(() => {
    const uniqueGroups = [...new Set(ledgers.map((ledger) => ledger.group).filter(Boolean))].sort()
    setGroups(uniqueGroups)
  }, [ledgers])

  const initializeApp = async () => {
    setLoading(true)
    setError('')
    try {
      const settingsResponse = await fetch('/api/settings')
      const settings = await readJson(settingsResponse)
      const nextConnection = {
        host: settings.host || 'localhost',
        port: settings.port || '9000',
      }
      const nextFirm = settings.selectedFirm || ''
      setTallyPath(settings.tallyPath || 'C:\\Program Files\\TallyPrime\\tally.exe')

      setConnection(nextConnection)
      setSelectedFirm(nextFirm)

      const loadedFirms = await loadFirms(nextConnection, nextFirm)
      const firmToUse = nextFirm || loadedFirms[0] || ''
      if (firmToUse) {
        setSelectedFirm(firmToUse)
      }
      await fetchLedgers({ date: selectedDate, firm: firmToUse, connectionOverride: nextConnection })
    } catch (initError) {
      setError('Unable to load dashboard settings.')
    } finally {
      setLoading(false)
    }
  }

  const loadFirms = async (connectionOverride = connection, preferredFirm = selectedFirm) => {
    try {
      const query = new URLSearchParams({
        host: connectionOverride.host,
        port: connectionOverride.port,
      })
      const response = await fetch(`/api/firms?${query.toString()}`)
      const data = await readJson(response)
      if (!response.ok) {
        throw new Error(data.error || 'Unable to fetch firms')
      }

      const nextFirms = data.firms || []
      setFirms(nextFirms)

      if (!preferredFirm && nextFirms.length > 0) {
        setSelectedFirm(nextFirms[0])
        await saveSettings(connectionOverride, nextFirms[0], false)
      }
      return nextFirms
    } catch (firmsError) {
      setFirms([])
      setError(firmsError.message || 'Unable to fetch firms from Tally.')
      return []
    }
  }

  const fetchLedgers = async ({
    date = selectedDate,
    firm = selectedFirm,
    connectionOverride = connection,
  } = {}) => {
    setLoading(true)
    setError('')
    try {
      const query = new URLSearchParams({
        date,
        firm,
        host: connectionOverride.host,
        port: connectionOverride.port,
      })
      const response = await fetch(`/api/ledgers?${query.toString()}`)
      const data = await readJson(response)
      if (!response.ok) {
        throw new Error(data.error || 'Unable to fetch ledgers')
      }
      const source = response.headers.get('X-Tally-Source') || 'live'
      const message = response.headers.get('X-Tally-Message') || ''
      setLedgers(Array.isArray(data) ? data : [])
      setBanner(
        `${message || 'Ledger data loaded.'} Showing ${Array.isArray(data) ? data.length : 0} ledgers for ${firm || 'current company'}.`
          + (source === 'saved' ? ' Source: saved data.' : source === 'empty' ? ' Source: no live data.' : '')
      )
    } catch (fetchError) {
      setLedgers([])
      setError(fetchError.message || 'Unable to fetch ledgers.')
    } finally {
      setLoading(false)
    }
  }

  const saveSettings = async (nextConnection = connection, nextFirm = selectedFirm, showBanner = true) => {
    const response = await fetch('/api/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        host: nextConnection.host,
        port: nextConnection.port,
        selectedFirm: nextFirm,
        tallyPath,
      }),
    })
    const data = await readJson(response)
    if (!response.ok) {
      throw new Error(data.error || 'Unable to save settings')
    }
    if (showBanner) {
      setBanner('Connection settings saved.')
    }
    return data
  }

  const saveLedgers = async (updatedLedgers, date = selectedDate, firm = selectedFirm) => {
    setSaving(true)
    try {
      const query = new URLSearchParams({ date, firm })
      const response = await fetch(`/api/ledgers?${query.toString()}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updatedLedgers),
      })
      if (!response.ok) {
        throw new Error('Unable to save ledger updates')
      }
      setBanner('Changes saved.')
    } catch (saveError) {
      setError(saveError.message || 'Unable to save updates.')
    } finally {
      setSaving(false)
    }
  }

  const handleDateChange = async (event) => {
    const nextDate = event.target.value
    setSelectedDate(nextDate)
    await fetchLedgers({ date: nextDate })
  }

  const handleLedgerChange = (index, field, value) => {
    const updatedLedgers = [...ledgers]
    updatedLedgers[index][field] = value
    setLedgers(updatedLedgers)
    saveLedgers(updatedLedgers)
  }

  const addComment = () => {
    if (selectedLedgerIndex === null || !newComment.trim()) {
      return
    }

    const updatedLedgers = [...ledgers]
    const ledger = updatedLedgers[selectedLedgerIndex]
    const comments = Array.isArray(ledger.comments) ? [...ledger.comments] : []
    comments.push({
      text: newComment.trim(),
      timestamp: new Date().toLocaleString(),
    })

    ledger.comments = comments
    ledger.comment = newComment.trim()
    ledger.date = selectedDate

    setLedgers(updatedLedgers)
    saveLedgers(updatedLedgers)
    setNewComment('')
    setShowCommentModal(false)
  }

  const openCommentModal = (index) => {
    setSelectedLedgerIndex(index)
    setNewComment('')
    setShowCommentModal(true)
  }

  const toggleCommentHistory = (index) => {
    setExpandedComments((current) => ({
      ...current,
      [index]: !current[index],
    }))
  }

  const handleConnectionInput = (field, value) => {
    setConnection((current) => ({ ...current, [field]: value }))
  }

  const handleApplyConnection = async () => {
    try {
      setError('')
      await saveSettings(connection, selectedFirm)
      const nextFirms = await loadFirms(connection, selectedFirm)
      const firmToUse = selectedFirm || nextFirms[0] || ''
      if (firmToUse !== selectedFirm) {
        setSelectedFirm(firmToUse)
      }
      await fetchLedgers({ firm: firmToUse, connectionOverride: connection })
    } catch (connectionError) {
      setError(connectionError.message || 'Unable to save Tally connection.')
    }
  }

  const handleTestConnection = async () => {
    try {
      setError('')
      const query = new URLSearchParams({
        host: connection.host,
        port: connection.port,
      })
      const response = await fetch(`/api/tally/test?${query.toString()}`)
      const data = await readJson(response)
      if (!response.ok) {
        throw new Error(data.error || 'Unable to test Tally connection')
      }
      setConnectionTest(data)
      setBanner(data.message || 'Connection test completed.')
      if (Array.isArray(data.firms)) {
        setFirms(data.firms)
      }
    } catch (testError) {
      setConnectionTest(null)
      setError(testError.message || 'Unable to test Tally connection.')
    }
  }

  const handleLaunchTally = async () => {
    try {
      setError('')
      const response = await fetch('/api/tally/launch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ tallyPath }),
      })
      const data = await readJson(response)
      if (!response.ok) {
        throw new Error(data.error || 'Unable to launch Tally')
      }
      if (data.launched) {
        await saveSettings(connection, selectedFirm, false)
      }
      setBanner(data.message || 'Tally launch request sent.')
    } catch (launchError) {
      setError(launchError.message || 'Unable to launch Tally.')
    }
  }

  const handleFirmChange = async (event) => {
    const nextFirm = event.target.value
    setSelectedFirm(nextFirm)
    try {
      await saveSettings(connection, nextFirm, false)
      await fetchLedgers({ firm: nextFirm })
      setBanner(`Firm changed to ${nextFirm}.`)
    } catch (firmError) {
      setError(firmError.message || 'Unable to switch firm.')
    }
  }

  const filteredLedgers = useMemo(() => {
    return ledgers.filter((ledger) => {
      if (filterGroup && ledger.group !== filterGroup) return false
      if (filterLedger && !ledger.name?.toLowerCase().includes(filterLedger.toLowerCase())) return false
      if (filterAssigned && !(ledger.assigned || '').toLowerCase().includes(filterAssigned.toLowerCase())) return false
      if (filterLedgerStatus && (ledger.status || '') !== filterLedgerStatus) return false
      if (filterStatus && (ledger.accountTeamStatus || '') !== filterStatus) return false
      if (filterHasComment === 'yes' && !ledger.comment) return false
      if (filterHasComment === 'no' && ledger.comment) return false
      return true
    })
  }, [filterAssigned, filterGroup, filterHasComment, filterLedger, filterLedgerStatus, filterStatus, ledgers])

  const dashboardStats = useMemo(() => {
    const withBalance = ledgers.filter((ledger) => Math.abs(Number(ledger.closing || 0)) > 0.01).length
    const commented = ledgers.filter((ledger) => ledger.comment).length
    const assigned = ledgers.filter((ledger) => ledger.assigned).length
    const pending = ledgers.filter((ledger) => (ledger.accountTeamStatus || '') !== 'closed').length
    return {
      total: ledgers.length,
      filtered: filteredLedgers.length,
      withBalance,
      commented,
      assigned,
      pending,
    }
  }, [filteredLedgers.length, ledgers])

  const pendencyBreakdown = useMemo(() => {
    const counts = TEAM_STATUS_OPTIONS.map((status) => ({
      status,
      label: STATUS_META[status].label,
      count: filteredLedgers.filter((ledger) => (ledger.accountTeamStatus || '') === status).length,
      color: STATUS_META[status].color,
    }))
    const maxCount = Math.max(...counts.map((item) => item.count), 1)
    return counts.map((item) => ({
      ...item,
      width: `${(item.count / maxCount) * 100}%`,
    }))
  }, [filteredLedgers])

  const pendingAtBreakdown = useMemo(() => {
    const counts = {}
    filteredLedgers.forEach((ledger) => {
      const key = (ledger.pendingAt || 'Unassigned bucket').trim()
      counts[key] = (counts[key] || 0) + 1
    })
    return Object.entries(counts)
      .map(([label, count]) => ({ label, count }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 5)
  }, [filteredLedgers])

  const exportToExcel = () => {
    const rows = filteredLedgers.map((ledger) => ({
      Firm: ledger.firm || selectedFirm,
      'Ledger Name': ledger.name,
      Group: ledger.group,
      Opening: ledger.opening,
      Debit: ledger.debit,
      Credit: ledger.credit,
      Closing: ledger.closing,
      Status: ledger.status,
      'Account Team Status': ledger.accountTeamStatus,
      'Pending At': ledger.pendingAt,
      'Assigned To': ledger.assigned || '',
      'Latest Comment': ledger.comment || '',
      Date: ledger.date || '',
    }))

    const worksheet = XLSX.utils.json_to_sheet(rows)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Ledgers')
    XLSX.writeFile(workbook, `Tally_Ledgers_${selectedFirm || 'firm'}_${selectedDate}.xlsx`)
  }

  const exportToPDF = async () => {
    const element = document.getElementById('table-to-export')
    if (!element) return

    const canvas = await html2canvas(element, { scale: 1.4 })
    const image = canvas.toDataURL('image/png')
    const pdf = new jsPDF('l', 'mm', 'a4')
    const width = 297
    const height = (canvas.height * width) / canvas.width
    pdf.addImage(image, 'PNG', 0, 0, width, height)
    pdf.save(`Tally_Ledgers_${selectedFirm || 'firm'}_${selectedDate}.pdf`)
  }

  const copyToClipboard = async (text) => {
    await navigator.clipboard.writeText(text)
    setCopyStatus('Copied to clipboard')
    window.setTimeout(() => setCopyStatus(''), 1600)
  }

  const backendCommand = `cd /d "d:\\git hub\\tally_integration"\nstart-dashboard.bat`
  const frontendCommand = `cd /d "d:\\git hub\\tally_integration\\frontend"\nnpm run dev`

  return (
    <div className="app-shell">
      <header className="hero">
        <div>
          <p className="eyebrow">Tally Integration Utility</p>
          <h1>Ledger follow-up with live Tally connection and firm-wise tracking.</h1>
          <p className="hero-copy">
            Connect to a running Tally server, choose the active firm, update team status from a controlled dropdown,
            and review pendency in a cleaner operations view.
          </p>
        </div>
        <div className="hero-actions">
          <button className="btn-primary" onClick={() => fetchLedgers()}>
            Sync from Tally
          </button>
          <button className="btn-secondary" onClick={() => setShowStartupModal(true)}>
            Windows Startup
          </button>
        </div>
      </header>

      {(banner || error || saving) && (
        <div className={`message-strip ${error ? 'error' : 'info'}`}>
          <span>{error || banner || (saving ? 'Saving changes...' : '')}</span>
          {saving && <span className="saving-dot">Saving...</span>}
        </div>
      )}

      <section className="panel-grid">
        <div className="panel connection-panel">
          <div className="panel-heading">
            <h2>Tally Connection</h2>
            <span className="panel-note">User-friendly setup for any Windows machine</span>
          </div>
          <div className="connection-form">
            <label>
              IP / Host
              <input
                type="text"
                value={connection.host}
                onChange={(event) => handleConnectionInput('host', event.target.value)}
                placeholder="localhost or 192.168.1.10"
              />
            </label>
            <label>
              Port
              <input
                type="text"
                value={connection.port}
                onChange={(event) => handleConnectionInput('port', event.target.value)}
                placeholder="9000"
              />
            </label>
            <label className="path-field">
              Tally Path
              <input
                type="text"
                value={tallyPath}
                onChange={(event) => setTallyPath(event.target.value)}
                placeholder="C:\\Program Files\\TallyPrime\\tally.exe"
              />
            </label>
            <label className="firm-field">
              Firm
              <select value={selectedFirm} onChange={handleFirmChange}>
                <option value="">Select firm</option>
                {firms.map((firm) => (
                  <option key={firm} value={firm}>
                    {firm}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="connection-actions">
            <button className="btn-primary" onClick={handleApplyConnection}>
              Save And Load Firms
            </button>
            <button className="btn-secondary" onClick={handleTestConnection}>
              Test Connection
            </button>
            <button className="btn-secondary" onClick={handleLaunchTally}>
              Launch Tally
            </button>
            <button className="btn-secondary" onClick={() => loadFirms()}>
              Refresh Firm List
            </button>
          </div>
          {connectionTest && (
            <div className={`test-result ${connectionTest.reachable ? 'success' : 'warning'}`}>
              <strong>{connectionTest.message}</strong>
              <span>
                Host: {connectionTest.host}:{connectionTest.port} | Firms found: {connectionTest.firmCount}
              </span>
            </div>
          )}
        </div>

        <div className="panel status-panel">
          <div className="panel-heading">
            <h2>Pendency Snapshot</h2>
            <span className="panel-note">Professional summary of follow-up load</span>
          </div>
          <div className="status-cards">
            <div className="stat-card">
              <span className="stat-value">{dashboardStats.total}</span>
              <span className="stat-label">Total Ledgers</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{dashboardStats.pending}</span>
              <span className="stat-label">Active Pendency</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{dashboardStats.assigned}</span>
              <span className="stat-label">Assigned</span>
            </div>
            <div className="stat-card">
              <span className="stat-value">{dashboardStats.commented}</span>
              <span className="stat-label">Commented</span>
            </div>
          </div>
          <div className="pendency-layout">
            <div className="chart-card">
              <h3>Account Team Status</h3>
              <div className="bar-chart">
                {pendencyBreakdown.map((item) => (
                  <div key={item.status} className="bar-row">
                    <div className="bar-label">
                      <span>{item.label}</span>
                      <strong>{item.count}</strong>
                    </div>
                    <div className="bar-track">
                      <div className="bar-fill" style={{ width: item.width, backgroundColor: item.color }} />
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <div className="chart-card">
              <h3>Pending At</h3>
              <div className="pending-list">
                {pendingAtBreakdown.map((item) => (
                  <div key={item.label} className="pending-row">
                    <span>{item.label}</span>
                    <strong>{item.count}</strong>
                  </div>
                ))}
                {pendingAtBreakdown.length === 0 && <p className="empty-note">No pendency buckets yet.</p>}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="panel controls-panel">
        <div className="panel-heading">
          <h2>Filters And Export</h2>
          <span className="panel-note">{loading ? 'Loading ledgers...' : `${dashboardStats.filtered} rows in view`}</span>
        </div>
        <div className="toolbar">
          <label>
            As On Date
            <input type="date" value={selectedDate} onChange={handleDateChange} />
          </label>
          <label>
            Group
            <select value={filterGroup} onChange={(event) => setFilterGroup(event.target.value)}>
              <option value="">All Groups</option>
              {groups.map((group) => (
                <option key={group} value={group}>
                  {group}
                </option>
              ))}
            </select>
          </label>
          <label>
            Ledger
            <input
              type="text"
              value={filterLedger}
              onChange={(event) => setFilterLedger(event.target.value)}
              placeholder="Search ledger name"
            />
          </label>
          <label>
            Assigned To
            <input
              type="text"
              value={filterAssigned}
              onChange={(event) => setFilterAssigned(event.target.value)}
              placeholder="Search owner"
            />
          </label>
          <label>
            Ledger Status
            <select value={filterLedgerStatus} onChange={(event) => setFilterLedgerStatus(event.target.value)}>
              <option value="">All ledger statuses</option>
              <option value="open">Open</option>
              <option value="closed">Closed</option>
            </select>
          </label>
          <label>
            Team Status
            <select value={filterStatus} onChange={(event) => setFilterStatus(event.target.value)}>
              <option value="">All statuses</option>
              {TEAM_STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>
                  {STATUS_META[status].label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Has Comment
            <select value={filterHasComment} onChange={(event) => setFilterHasComment(event.target.value)}>
              <option value="">Any</option>
              <option value="yes">Yes</option>
              <option value="no">No</option>
            </select>
          </label>
        </div>
        <div className="toolbar-actions">
          <button
            className="btn-secondary"
            onClick={() => {
              setFilterGroup('')
              setFilterLedger('')
              setFilterAssigned('')
              setFilterLedgerStatus('')
              setFilterStatus('')
              setFilterHasComment('')
            }}
          >
            Clear Filters
          </button>
          <button className="btn-secondary" onClick={exportToExcel}>
            Export Excel
          </button>
          <button className="btn-secondary" onClick={exportToPDF}>
            Export PDF
          </button>
        </div>
      </section>

      <section className="panel table-panel" id="table-to-export">
        <div className="panel-heading">
          <h2>Ledger Worklist</h2>
          <span className="panel-note">{selectedFirm || 'No firm selected'}</span>
        </div>
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Ledger</th>
                <th>Group</th>
                <th>Opening</th>
                <th>Debit</th>
                <th>Credit</th>
                <th>Closing</th>
                <th>Status</th>
                <th>Account Team Status</th>
                <th>Pending At</th>
                <th>Assigned To</th>
                <th>Comments</th>
                <th>Date</th>
              </tr>
            </thead>
            <tbody>
              {filteredLedgers.length > 0 ? (
                filteredLedgers.map((ledger) => {
                  const actualIndex = ledgers.indexOf(ledger)
                  const teamStatus = ledger.accountTeamStatus || 'work in progress'
                  return (
                    <tr key={`${ledger.name}-${ledger.group}`} className={Math.abs(Number(ledger.closing || 0)) > 0.01 ? 'highlight' : ''}>
                      <td className="ledger-cell">
                        <strong>{ledger.name}</strong>
                        <span>{ledger.firm || selectedFirm}</span>
                      </td>
                      <td>
                        <span className="group-pill">{ledger.group}</span>
                      </td>
                      <td className="amount">{formatCurrency(ledger.opening)}</td>
                      <td className="amount debit">{formatCurrency(ledger.debit)}</td>
                      <td className="amount credit">{formatCurrency(ledger.credit)}</td>
                      <td className="amount closing">{formatCurrency(ledger.closing)}</td>
                      <td>
                        <span className={`status-pill ${ledger.status}`}>{ledger.status}</span>
                      </td>
                      <td>
                        <select
                          className="table-input"
                          value={teamStatus}
                          onChange={(event) => handleLedgerChange(actualIndex, 'accountTeamStatus', event.target.value)}
                        >
                          {TEAM_STATUS_OPTIONS.map((status) => (
                            <option key={status} value={status}>
                              {STATUS_META[status].label}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <input
                          className="table-input"
                          type="text"
                          value={ledger.pendingAt || ''}
                          onChange={(event) => handleLedgerChange(actualIndex, 'pendingAt', event.target.value)}
                          placeholder="Pending at"
                        />
                      </td>
                      <td>
                        <input
                          className="table-input"
                          type="text"
                          value={ledger.assigned || ''}
                          onChange={(event) => handleLedgerChange(actualIndex, 'assigned', event.target.value)}
                          placeholder="Owner"
                        />
                      </td>
                      <td className="comments-cell">
                        <button className="comment-button" onClick={() => openCommentModal(actualIndex)}>
                          Add Comment
                        </button>
                        {ledger.comments?.length > 0 && (
                          <button className="history-button" onClick={() => toggleCommentHistory(actualIndex)}>
                            {expandedComments[actualIndex] ? 'Hide' : `History (${ledger.comments.length})`}
                          </button>
                        )}
                        {expandedComments[actualIndex] && ledger.comments?.length > 0 && (
                          <div className="comment-history">
                            {ledger.comments.map((comment, commentIndex) => (
                              <div key={`${comment.timestamp}-${commentIndex}`} className="comment-item">
                                <p>{comment.text}</p>
                                <span>{comment.timestamp}</span>
                              </div>
                            ))}
                          </div>
                        )}
                      </td>
                      <td>{ledger.date || selectedDate}</td>
                    </tr>
                  )
                })
              ) : (
                <tr>
                  <td colSpan="12" className="empty-state">
                    {loading ? 'Loading ledgers...' : 'No ledgers match the current selection.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {showStartupModal && (
        <div className="modal-overlay" onClick={() => setShowStartupModal(false)}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Windows Startup Guide</h3>
              <button className="close-button" onClick={() => setShowStartupModal(false)}>
                x
              </button>
            </div>
            <div className="modal-body">
              <p>For a friendlier Windows setup, start the packaged dashboard from the project root.</p>
              <div className="command-block">
                <h4>Single command</h4>
                <pre>{backendCommand}</pre>
                <button className="btn-secondary" onClick={() => copyToClipboard(backendCommand)}>
                  Copy Command
                </button>
              </div>
              <div className="command-block">
                <h4>Frontend development mode</h4>
                <pre>{frontendCommand}</pre>
                <button className="btn-secondary" onClick={() => copyToClipboard(frontendCommand)}>
                  Copy Dev Command
                </button>
              </div>
              {copyStatus && <p className="copy-status">{copyStatus}</p>}
            </div>
          </div>
        </div>
      )}

      {showCommentModal && (
        <div className="modal-overlay" onClick={() => setShowCommentModal(false)}>
          <div className="modal-card" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <h3>Add Ledger Comment</h3>
              <button className="close-button" onClick={() => setShowCommentModal(false)}>
                x
              </button>
            </div>
            <div className="modal-body">
              <p className="modal-ledger-name">{ledgers[selectedLedgerIndex]?.name}</p>
              {ledgers[selectedLedgerIndex]?.comments?.length > 0 && (
                <div className="comment-history-modal">
                  {ledgers[selectedLedgerIndex].comments.map((comment, index) => (
                    <div key={`${comment.timestamp}-${index}`} className="comment-item">
                      <p>{comment.text}</p>
                      <span>{comment.timestamp}</span>
                    </div>
                  ))}
                </div>
              )}
              <textarea
                value={newComment}
                onChange={(event) => setNewComment(event.target.value)}
                placeholder="Enter follow-up notes, approval remarks, or reconciliation details"
              />
              <div className="modal-actions">
                <button className="btn-primary" onClick={addComment}>
                  Save Comment
                </button>
                <button className="btn-secondary" onClick={() => setShowCommentModal(false)}>
                  Cancel
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

function formatCurrency(value) {
  const number = Number(value || 0)
  return new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(number)
}

async function readJson(response) {
  const contentType = response.headers.get('content-type') || ''
  const text = await response.text()

  if (!text) {
    return {}
  }

  if (!contentType.includes('application/json')) {
    const preview = text.replace(/\s+/g, ' ').slice(0, 120)
    throw new Error(`API returned non-JSON response: ${preview}`)
  }

  try {
    return JSON.parse(text)
  } catch {
    throw new Error('API returned invalid JSON.')
  }
}

export default App
