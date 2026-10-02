import { useState } from 'react'
import { BrainCircuit, ChevronDown, Sparkles } from 'lucide-react'
import { anomalyApi, auditApi, investigatorApi } from '../../api'
import { isDemoSession } from '../../auth/AuthContext'
import { Badge, Button, DataTable, PageHeader, Section } from '../../components/common/UI'
import { EmptyState, ErrorState, InlineAlert, PageSkeleton } from '../../components/common/States'
import { useApi } from '../../hooks/useApi'
import { demoAnomalies, demoAudits } from '../../mock/demoData'
import { formatDateTime } from '../../utils/format'
import type { Investigation } from '../../types/domain'

function formatAnomalyFeature(feature: string) { const labels: Record<string, string> = { event_action_frequency: 'Frequency of this action', actor_event_frequency: 'Recent activity by this actor', actor_action_frequency: 'Frequency of this action by this actor', seconds_since_previous_actor_event: "Time since the actor's previous event", seconds_since_previous_global_event: 'Time since the previous audit event', actor_unique_entities_recently: 'Unique entities involved in recent actor activity', action_unique_entities_recently: 'Unique entities involved in this action' }; return labels[feature] || feature.replace(/_/g, ' '); }

export function AnomaliesPage() {
  const { data, loading, error, retry } = useApi(
    () => isDemoSession()
      ? Promise.resolve({
          content: demoAnomalies,
          page: 0,
          size: 20,
          totalElements: demoAnomalies.length,
          totalPages: 1
        })
      : anomalyApi.list(),
    []
  )

  const [selected, setSelected] = useState(0)
  const anomaly = data?.content[selected]

  const audit = useApi(
    () => anomaly?.eventId
      ? (isDemoSession()
          ? Promise.resolve(demoAudits.find(event => event.id === anomaly.eventId) || null)
          : auditApi.get(anomaly.eventId))
      : Promise.resolve(null),
    [anomaly?.eventId]
  )

  if (loading) return <PageSkeleton />
  if (error) return <ErrorState message={error.message} onRetry={retry} />

  return (
    <>
      <PageHeader
        eyebrow="Explainable detection"
        title="Risk & anomalies"
        description="Review unusual activity using source audit evidence and backend-provided model signals."
      />

      <InlineAlert>
        Model output is an investigative signal, not proof of fraud. Human review and source evidence are required.
      </InlineAlert>

      <div className="risk-layout">
        <Section title="Flagged events">
          {data?.content.length ? (
            <div className="anomaly-list">
              {data.content.map((a, i) => (
                <button
                  className={selected === i ? 'active' : ''}
                  key={a.id}
                  onClick={() => setSelected(i)}
                >
                  <span className="score">{Math.round(a.score * 100)}</span>
                  <div>
                    <strong>{a.employee || 'Unknown employee'}</strong>
                    <small>{a.eventId} � {formatDateTime(a.flaggedAt)}</small>
                  </div>
                  <Badge>{a.riskLevel}</Badge>
                  <ChevronDown />
                </button>
              ))}
            </div>
          ) : (
            <EmptyState
              title="No anomalies found"
              description="The backend returned no model flags."
            />
          )}
        </Section>

        <Section
          title="Why does this event need review?"
          description={anomaly ? `${anomaly.model || 'Backend model'} � anomaly index ${anomaly.score.toFixed(2)}` : 'Select an event'}
        >
          {anomaly ? (
            <>
              <div className="risk-callout">
                <BrainCircuit />
                <div>
                  <h3>Unusual activity detected</h3>
                  <p>
                    The model identified this event as unusual compared with the recent audit baseline.
                    This signal should be reviewed together with the underlying audit evidence.
                  </p>
                </div>
              </div>

              <Section
                title="What happened"
                description="Authoritative audit information associated with the flagged event"
              >
                {audit.loading ? (
                  <p>Loading audit evidence...</p>
                ) : audit.error ? (
                  <InlineAlert tone="warning">
                    Audit event context could not be loaded. The model explanation remains available below.
                  </InlineAlert>
                ) : audit.data ? (
                  <div className="evidence">
                    <strong>{audit.data.action}</strong>
                    <p>
                      This action was recorded for {audit.data.employee || audit.data.entity}
                      {audit.data.actor ? ` by ${audit.data.actor}` : ''}.
                    </p>
                    <small>
                      {audit.data.entity}
                      {audit.data.entityId ? ` � ${audit.data.entityId}` : ''}
                      {' � '}
                      {formatDateTime(audit.data.timestamp)}
                    </small>
                  </div>
                ) : (
                  <EmptyState
                    title="Audit event unavailable"
                    description="The source audit event could not be retrieved for this anomaly."
                  />
                )}
              </Section>

              <Section
                title="Why the model flagged it"
                description="The strongest model signals are shown in business-readable terms. Feature contributions describe model output, not a confirmed risk level."
              >
                {anomaly.indicators?.length ? (
                  <div className="risk-callout">
                    <BrainCircuit />
                    <div>
                      <h3>Model signals considered</h3>
                      <p>
                        {anomaly.indicators
                          .map(formatAnomalyFeature)
                          .join(' � ')}
                      </p>
                    </div>
                  </div>
                ) : (
                  <EmptyState
                    title="No model signals available"
                    description="The backend did not return feature indicators for this finding."
                  />
                )}
              </Section>

              <Section
                title="Technical model details"
                description="Raw SHAP contribution output returned by the ML service"
              >
                {anomaly.explanations?.length ? (
                  <DataTable headers={['Feature', 'Contribution', 'Direction', 'Explanation']}>
                    {anomaly.explanations.map(x => (
                      <tr key={x.feature}>
                        <td>
                          <strong>{formatAnomalyFeature(x.feature)}</strong>
                          <small>{x.feature}</small>
                        </td>
                        <td>
                          <div className="contribution">
                            <i style={{ width: `${Math.min(100, Math.abs(x.contribution) * 200)}%` }} />
                            <span>{x.contribution.toFixed(2)}</span>
                          </div>
                        </td>
                        <td>
                          <Badge>
                            {x.direction.replace('_', ' ')}
                          </Badge>
                        </td>
                        <td>{x.explanation}</td>
                      </tr>
                    ))}
                  </DataTable>
                ) : (
                  <EmptyState
                    title="SHAP output unavailable"
                    description="No feature contributions were returned by the ML service."
                  />
                )}
              </Section>
            </>
          ) : (
            <EmptyState title="Select an event" description="Select a flagged event to review its evidence and model explanation." />
          )}
        </Section>
      </div>
    </>
  )
}
export function InvestigatorPage() {
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<Investigation | null>(null)
  const [error, setError] = useState('')

  const submit = async () => {
    if (!query.trim()) return
    setLoading(true)
    setError('')
    setResult(null)
    if (isDemoSession()) {
      setError('RAG requests are disabled in demo mode. Connect the authorized retrieval and Ollama services to investigate.')
      setLoading(false)
      return
    }
    try {
      setResult(await investigatorApi.investigate(query))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Investigation failed.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <>
      <PageHeader eyebrow="Authorized retrieval" title="RAG investigator" description="Ask evidence-grounded questions across audit events you are authorized to access." />
      <div className="investigator-compose">
        <Sparkles />
        <textarea value={query} onChange={event => setQuery(event.target.value)} placeholder="Ask a question about authorized audit evidence." />
        <Button onClick={submit} disabled={loading || !query.trim()}>{loading ? 'Investigating...' : 'Run investigation'}</Button>
      </div>
      {error && <InlineAlert tone="warning">{error}</InlineAlert>}
      {result ? (
        <div className="investigation-results">
          <Section title="Answer" description={result.answer ? 'Locally generated from authorized retrieved evidence.' : 'Generation is unavailable; retrieved evidence is shown without an invented answer.'}>
            <div className="ai-label">{result.answer ? 'Local model response' : `Generation ${result.status || 'UNAVAILABLE'}`}</div>
            <p className="answer">{result.answer || 'No generated answer is available.'}</p>
            {result.limitations?.map(limitation => <small key={limitation}>{limitation}</small>)}
          </Section>
          <Section title="Evidence" description="Authorized source material returned by semantic retrieval">
            {result.evidence?.length ? result.evidence.map(evidence => (
              <article className="evidence" key={evidence.id}>
                <strong>{evidence.title}</strong>
                <p>{evidence.excerpt}</p>
                <small>{evidence.eventId} 쨌 {formatDateTime(evidence.timestamp)}</small>
              </article>
            )) : <EmptyState title="No evidence returned" />}
          </Section>
        </div>
      ) : (
        <Section><EmptyState title="Begin an evidence-based investigation" description="Answers are not generated until the backend completes authorized ChromaDB retrieval and local LLM generation." /></Section>
      )}
    </>
  )
}
