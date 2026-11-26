import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorState, LoadingState } from '../components/AsyncState';
import type { Page, ReputationLedgerEntry, ReputationProfile } from '../types';

export function PointsPage() {
  const { me } = useAuth();
  const [profile, setProfile] = useState<ReputationProfile | null>(null);
  const [ledger, setLedger] = useState<Page<ReputationLedgerEntry> | null>(null);
  const [page, setPage] = useState(1);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    if (!me) return;
    setError(''); setLedger(null);
    Promise.all([api.reputation(me.id), api.reputationLedger(me.id, page)])
      .then(([nextProfile, nextLedger]) => { if (active) { setProfile(nextProfile); setLedger(nextLedger); } })
      .catch((reason) => { if (active) setError((reason as Error).message); });
    return () => { active = false; };
  }, [me, page]);

  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />;
  if (!profile || !ledger) return <LoadingState label="正在加载积分与流水" />;
  const pages = Math.max(1, Math.ceil(ledger.total / ledger.size));

  return <div className="form-page">
    <div className="page-heading"><div><h1>我的积分与声誉流水</h1><p className="muted">积分余额与声誉分别展示，不将两者混为同一数值。</p></div><Link to={'/users/' + me?.id}>返回个人主页</Link></div>
    <section className="balance-grid">
      <div className="panel"><span className="muted">可用积分</span><strong className="metric">{profile.availablePoints ?? '—'}</strong></div>
      <div className="panel"><span className="muted">冻结积分</span><strong className="metric">{profile.frozenPoints ?? '—'}</strong></div>
      <div className="panel"><span className="muted">声誉</span><strong className="metric">{profile.reputation}</strong></div>
    </section>
    <section className="panel"><h2>声誉流水</h2>
      <p className="muted">当前后端提供声誉流水；独立积分收支流水接口尚未开放。</p>
      <div className="ledger-list">{ledger.items.map((entry) => <article className="ledger-row" key={entry.id}>
        <div><strong>{entry.reasonLabel || entry.reason}</strong><span className="muted">{new Date(entry.createdAt).toLocaleString('zh-CN')}</span></div>
        <div>{entry.refType === 'QUESTION' ? <Link to={'/questions/' + entry.refId}>问题 #{entry.refId}</Link> : <span>{entry.refType} #{entry.refId}</span>}<strong className={entry.delta < 0 ? 'negative' : 'positive'}>{entry.delta > 0 ? '+' : ''}{entry.delta}{entry.delta < 0 && '（冲正/扣减）'}</strong></div>
      </article>)}</div>
      {ledger.items.length === 0 && <p className="muted">暂无流水。</p>}
      <div className="pagination"><button disabled={page <= 1} onClick={() => setPage((value) => value - 1)}>上一页</button><span>第 {page} / {pages} 页</span><button disabled={page >= pages} onClick={() => setPage((value) => value + 1)}>下一页</button></div>
    </section>
  </div>;
}
