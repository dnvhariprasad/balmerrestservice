import { useState } from 'react'
import './App.css'

// API Base URL - change this based on your environment
const API_BASE_URL = 'http://localhost:8080'

function App() {
  // Authentication state
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [sessionId, setSessionId] = useState(null)
  const [sessionInfo, setSessionInfo] = useState(null)
  const [loginLoading, setLoginLoading] = useState(false)
  const [loginError, setLoginError] = useState(null)

  // API Test state
  const [workitemId, setWorkitemId] = useState('')
  const [processInstanceId, setProcessInstanceId] = useState('')
  const [apiLoading, setApiLoading] = useState(false)
  const [apiError, setApiError] = useState(null)
  const [apiResult, setApiResult] = useState(null)

  // Create PDF Note state
  const [pdfWorkitemId, setPdfWorkitemId] = useState('')
  const [pdfProcessInstanceId, setPdfProcessInstanceId] = useState('')
  const [pdfLoading, setPdfLoading] = useState(false)
  const [pdfError, setPdfError] = useState(null)
  const [pdfResult, setPdfResult] = useState(null)

  // Handle Login
  const handleLogin = async (e) => {
    e.preventDefault()
    setLoginLoading(true)
    setLoginError(null)

    try {
      const response = await fetch(`${API_BASE_URL}/session`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ userName: username, password: password }),
      })

      const data = await response.json()

      if (data.success) {
        setSessionId(data.sessionId)
        setSessionInfo(data)
        setLoginError(null)
      } else {
        setLoginError(data.error || 'Login failed')
        setSessionId(null)
        setSessionInfo(null)
      }
    } catch (err) {
      setLoginError(`Connection error: ${err.message}`)
      setSessionId(null)
      setSessionInfo(null)
    } finally {
      setLoginLoading(false)
    }
  }

  // Handle Logout
  const handleLogout = () => {
    setSessionId(null)
    setSessionInfo(null)
    setApiResult(null)
    setApiError(null)
  }

  // Handle API Call - Supporting Documents List
  const handleGetSupportingDocs = async (e) => {
    e.preventDefault()

    if (!sessionId) {
      setApiError('Please login first to get a session ID')
      return
    }

    setApiLoading(true)
    setApiError(null)
    setApiResult(null)

    try {
      const url = `${API_BASE_URL}/supportingdocs/list?workitemId=${encodeURIComponent(workitemId)}&processInstanceId=${encodeURIComponent(processInstanceId)}`

      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'sessionId': sessionId.toString(),
        },
      })

      const data = await response.json()
      setApiResult(data)

      if (!data.success) {
        setApiError(data.error || 'API call failed')
      }
    } catch (err) {
      setApiError(`Connection error: ${err.message}`)
    } finally {
      setApiLoading(false)
    }
  }

  // Handle Create PDF Note API Call
  const handleCreatePdfNote = async (e) => {
    e.preventDefault()

    setPdfLoading(true)
    setPdfError(null)
    setPdfResult(null)

    try {
      const url = `${API_BASE_URL}/notesheet/createpdfnote?workitemId=${encodeURIComponent(pdfWorkitemId)}&processInstanceId=${encodeURIComponent(pdfProcessInstanceId)}`

      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      })

      const data = await response.json()
      setPdfResult(data)

      if (!data.success) {
        setPdfError(data.error || 'API call failed')
      }
    } catch (err) {
      setPdfError(`Connection error: ${err.message}`)
    } finally {
      setPdfLoading(false)
    }
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Balmer REST API Tester</h1>
        <p>Test the Supporting Documents API endpoint</p>
      </header>

      <main className="app-main">
        {/* Login Section */}
        <section className="card">
          <h2>1. Authentication</h2>
          {!sessionId ? (
            <form onSubmit={handleLogin} className="form">
              <div className="form-group">
                <label htmlFor="username">Username</label>
                <input
                  type="text"
                  id="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Enter username"
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="password">Password</label>
                <input
                  type="password"
                  id="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter password"
                  required
                />
              </div>
              <button type="submit" className="btn btn-primary" disabled={loginLoading}>
                {loginLoading ? 'Logging in...' : 'Login'}
              </button>
              {loginError && <div className="error-message">{loginError}</div>}
            </form>
          ) : (
            <div className="session-info">
              <div className="success-message">Logged in successfully!</div>
              <div className="session-details">
                <p><strong>Session ID:</strong> <code>{sessionId}</code></p>
                <p><strong>User:</strong> {sessionInfo?.userName}</p>
                <p><strong>Remaining:</strong> {sessionInfo?.remainingMinutes} minutes</p>
                <p><strong>Cached:</strong> {sessionInfo?.cached ? 'Yes' : 'No'}</p>
              </div>
              <button onClick={handleLogout} className="btn btn-secondary">
                Logout
              </button>
            </div>
          )}
        </section>

        {/* API Test Section */}
        <section className="card">
          <h2>2. Test Supporting Documents API</h2>
          <div className="api-info">
            <code>GET /supportingdocs/list</code>
          </div>

          <form onSubmit={handleGetSupportingDocs} className="form">
            <div className="form-group">
              <label htmlFor="workitemId">Work Item ID</label>
              <input
                type="text"
                id="workitemId"
                value={workitemId}
                onChange={(e) => setWorkitemId(e.target.value)}
                placeholder="e.g., 1"
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="processInstanceId">Process Instance ID</label>
              <input
                type="text"
                id="processInstanceId"
                value={processInstanceId}
                onChange={(e) => setProcessInstanceId(e.target.value)}
                placeholder="e.g., e-Notes-000000000008-process"
                required
              />
            </div>
            <div className="form-group">
              <label>Session ID (from login)</label>
              <input
                type="text"
                value={sessionId || 'Not logged in'}
                disabled
                className="disabled-input"
              />
            </div>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={apiLoading || !sessionId}
            >
              {apiLoading ? 'Loading...' : 'Get Supporting Documents'}
            </button>
            {!sessionId && (
              <div className="warning-message">Please login first to test the API</div>
            )}
            {apiError && <div className="error-message">{apiError}</div>}
          </form>
        </section>

        {/* Create PDF Note Section */}
        <section className="card">
          <h2>3. Create PDF Note</h2>
          <div className="api-info">
            <code>POST /notesheet/createpdfnote</code>
            <p className="api-description">
              Creates a PDF from the original notesheet with supporting documents table
              and comments history. Uses service account (no login required).
            </p>
          </div>

          <form onSubmit={handleCreatePdfNote} className="form">
            <div className="form-group">
              <label htmlFor="pdfWorkitemId">Work Item ID</label>
              <input
                type="text"
                id="pdfWorkitemId"
                value={pdfWorkitemId}
                onChange={(e) => setPdfWorkitemId(e.target.value)}
                placeholder="e.g., 1"
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="pdfProcessInstanceId">Process Instance ID</label>
              <input
                type="text"
                id="pdfProcessInstanceId"
                value={pdfProcessInstanceId}
                onChange={(e) => setPdfProcessInstanceId(e.target.value)}
                placeholder="e.g., e-Notes-000000000045-process"
                required
              />
            </div>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={pdfLoading}
            >
              {pdfLoading ? 'Creating PDF...' : 'Create PDF Note'}
            </button>
            {pdfError && <div className="error-message">{pdfError}</div>}
          </form>

          {/* PDF Note Result */}
          {pdfResult && (
            <div className="result-section">
              <h3>Result</h3>
              <div className="result-summary">
                <span className={`status-badge ${pdfResult.success ? 'success' : 'error'}`}>
                  {pdfResult.success ? 'Success' : 'Failed'}
                </span>
              </div>

              {pdfResult.success && (
                <div className="pdf-result-details">
                  <table className="details-table">
                    <tbody>
                      <tr>
                        <th>Original Doc Index</th>
                        <td><code>{pdfResult.originalDocIndex}</code></td>
                      </tr>
                      <tr>
                        <th>Note Document Index</th>
                        <td><code>{pdfResult.notedocumentIndex}</code></td>
                      </tr>
                      <tr>
                        <th>New Version</th>
                        <td><strong>{pdfResult.newVersion}</strong></td>
                      </tr>
                      <tr>
                        <th>PDF Path</th>
                        <td><code>{pdfResult.pdfPath}</code></td>
                      </tr>
                      <tr>
                        <th>Comments Path</th>
                        <td><code>{pdfResult.commentsPath}</code></td>
                      </tr>
                      <tr>
                        <th>Annotations Preserved</th>
                        <td>{pdfResult.annotationsPreserved ? 'Yes' : 'No'}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              )}

              <div className="raw-response">
                <h4>Raw JSON Response</h4>
                <pre>{JSON.stringify(pdfResult, null, 2)}</pre>
              </div>
            </div>
          )}
        </section>

        {/* Results Section */}
        {apiResult && (
          <section className="card results-card">
            <h2>4. Supporting Docs API Response</h2>

            {/* Summary */}
            <div className="result-summary">
              <span className={`status-badge ${apiResult.success ? 'success' : 'error'}`}>
                {apiResult.success ? 'Success' : 'Failed'}
              </span>
              {apiResult.count !== undefined && (
                <span className="count-badge">
                  {apiResult.count} document(s) found
                </span>
              )}
            </div>

            {/* Documents Table */}
            {apiResult.documents && apiResult.documents.length > 0 && (
              <div className="documents-table">
                <h3>Documents</h3>
                <table>
                  <thead>
                    <tr>
                      <th>Index</th>
                      <th>Name</th>
                      <th>Type</th>
                      <th>Size</th>
                      <th>Version</th>
                      <th>Owner</th>
                      <th>Created</th>
                    </tr>
                  </thead>
                  <tbody>
                    {apiResult.documents.map((doc, index) => (
                      <tr key={index}>
                        <td><code>{doc.documentIndex}</code></td>
                        <td>{doc.documentName}</td>
                        <td>{doc.documentType}</td>
                        <td>{formatFileSize(doc.documentSize)}</td>
                        <td>{doc.versionNo}</td>
                        <td>{doc.owner}</td>
                        <td>{doc.createdDateTime}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {/* Raw JSON Response */}
            <div className="raw-response">
              <h3>Raw JSON Response</h3>
              <pre>{JSON.stringify(apiResult, null, 2)}</pre>
            </div>
          </section>
        )}
      </main>

      <footer className="app-footer">
        <p>Balmer Lawrie REST Service API Tester</p>
      </footer>
    </div>
  )
}

// Helper function to format file size
function formatFileSize(bytes) {
  if (!bytes || bytes === '') return '-'
  const size = parseInt(bytes, 10)
  if (isNaN(size)) return bytes
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

export default App
