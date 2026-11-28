import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { ErrorState, LoadingState } from '../components/AsyncState';
import type { ReputationProfile, UserProfile } from '../types';

const roleLabels = { STUDENT: '学生', TA: '助教', TEACHER: '教师', ADMIN: '管理员' } as const;

export function UserProfilePage() {
  const { userId } = useParams();
  const { me } = useAuth();
  const id = Number(userId);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [reputation, setReputation] = useState<ReputationProfile | null>(null);
  const [error, setError] = useState('');
  const [reputationError, setReputationError] = useState('');

  useEffect(() => {
    let active = true;
    if (!Number.isSafeInteger(id) || id < 1) { setError('用户地址无效'); return; }
    setProfile(null); setReputation(null); setError(''); setReputationError('');
    api.userProfile(id).then((value) => { if (active) setProfile(value); })
      .catch((reason) => { if (active) setError((reason as Error).message); });
    api.reputation(id).then((value) => { if (active) setReputation(value); })
      .catch((reason) => { if (active) setReputationError((reason as Error).message); });
    return () => { active = false; };
  }, [id]);

  if (error) return <ErrorState message={error} onRetry={() => window.location.reload()} />;
  if (!profile) return <LoadingState label="正在加载个人主页" />;
  const isSelf = me?.id === profile.id;

  return <div className="form-page profile-page">
    <div className="page-heading">
      <div><h1>{profile.displayName}</h1><p className="muted">{roleLabels[profile.role]}{profile.verified ? ' · 账号身份已认证' : ''}</p></div>
      {isSelf && <Link className="primary" to="/me/points">我的积分与流水</Link>}
    </div>
    <section className="profile-grid">
      <div className="panel"><h2>基本资料</h2>
        <dl className="details-list">
          <div><dt>学院</dt><dd>{profile.college || '未填写'}</dd></div>
          <div><dt>加入时间</dt><dd>{new Date(profile.createdAt).toLocaleDateString('zh-CN')}</dd></div>
          {profile.self && <><div><dt>账号</dt><dd>{profile.self.username}</dd></div><div><dt>邮箱</dt><dd>{profile.self.email}</dd></div><div><dt>班级</dt><dd>{profile.self.className || '未填写'}</dd></div></>}
        </dl>
      </div>
      <div className="panel"><h2>声誉</h2>
        {reputation ? <><strong className="metric">{reputation.reputation}</strong><p className="muted">公开声誉</p></> : <p className="error">{reputationError || '正在加载声誉…'}</p>}
      </div>
    </section>
    <section className="panel"><h2>徽章</h2>
      {reputation?.badges.length ? <div className="badge-list">{reputation.badges.map((badge) => <article key={badge.code} className="badge-card"><strong>{badge.name}</strong><span>{badge.description}</span><small>{new Date(badge.awardedAt).toLocaleDateString('zh-CN')}</small></article>)}</div> : <p className="muted">暂无徽章。</p>}
    </section>
    <section className="panel"><h2>提问与回答</h2><p className="muted">当前后端未提供按用户查询提问和回答的接口，因此本页不请求或拼接不完整数据。</p></section>
  </div>;
}
