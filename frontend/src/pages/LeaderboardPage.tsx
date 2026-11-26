import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { ErrorState, LoadingState } from '../components/AsyncState';
import type { LeaderboardEntry } from '../types';

const PERIODS = [['week', '本周'], ['month', '本月'], ['term', '本学期'], ['all', '总榜']] as const;

export function LeaderboardPage() {
  const [period, setPeriod] = useState<string>('week');
  const [rows, setRows] = useState<LeaderboardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    api.leaderboard(period).then((value) => { if (active) setRows(value); })
      .catch((reason) => { if (active) setError((reason as Error).message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [period]);

  if (loading) return <LoadingState label="正在加载排行榜" />;
  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />;

  return (
    <div className="form-page">
      <h1>声誉排行榜</h1>
      <p className="muted">统计范围：{PERIODS.find(([value]) => value === period)?.[1]} · 全平台</p>
      <div className="tabs">
        {PERIODS.map(([value, label]) => (
          <button key={value} className={period === value ? 'active' : ''} onClick={() => setPeriod(value)}>
            {label}
          </button>
        ))}
      </div>
      <table className="board">
        <thead><tr><th>名次</th><th>用户</th><th>声誉</th></tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.userId}>
              <td>{r.rank}</td>
              <td><Link to={'/users/' + r.userId}>{r.displayName}</Link>{r.verified && <span className="verified" title="已认证">✔</span>}</td>
              <td>{r.score}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 && <p className="muted">这个周期还没有数据。</p>}
    </div>
  );
}
