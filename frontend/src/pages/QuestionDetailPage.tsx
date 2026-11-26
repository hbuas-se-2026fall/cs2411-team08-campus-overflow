import ReactMarkdown from 'react-markdown';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError, NetworkError } from '../api';
import { useAuth } from '../auth';
import { ErrorState, LoadingState } from '../components/AsyncState';
import type { Answer, Bounty, Comment, QuestionDetail, ReputationProfile } from '../types';

export function QuestionDetailPage() {
  const { id } = useParams();
  const { me } = useAuth();
  return <Detail key={String(me?.id) + ':' + id} questionId={Number(id)} />;
}

function Detail({ questionId }: { questionId: number }) {
  const { me } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [q, setQ] = useState<QuestionDetail | null>(null);
  const [error, setError] = useState('');
  const [draft, setDraft] = useState('');
  const [editingId, setEditingId] = useState<number | null>(null);
  const [preview, setPreview] = useState(false);
  const [busy, setBusy] = useState<string[]>([]);
  const pending = useRef(new Set<string>());
  const generation = useRef(0);
  const alive = useRef(true);
  const [answerLimit, setAnswerLimit] = useState(20);
  const [bounty, setBounty] = useState<Bounty | null>(null);
  const [bountyError, setBountyError] = useState('');
  const [now, setNow] = useState(Date.now());
  const checkedExpiry = useRef<number | null>(null);
  const [bountyDialog, setBountyDialog] = useState(false);
  const [bountyPoints, setBountyPoints] = useState('10');
  const [bountyDays, setBountyDays] = useState('1');
  const [availablePoints, setAvailablePoints] = useState<number | null>(null);
  const [actionDialog, setActionDialog] = useState<{ kind: 'review' | 'revoke-endorsement'; answerId?: number } | null>(null);
  const [actionReason, setActionReason] = useState('');
  const from = (location.state as { from?: string } | null)?.from;
  const returnTo = typeof from === 'string' && /^\/(?:\?|courses\/\d+\/questions(?:\?|$)|$)/.test(from) ? from : '/';

  const load = useCallback(async () => {
    const version = ++generation.current;
    try {
      const result = await api.question(questionId);
      if (alive.current && version === generation.current) setQ(result);
    } catch (reason) {
      if (alive.current && version === generation.current && reason instanceof ApiError && [401, 403, 404].includes(reason.status)) setQ(null);
      throw reason;
    }
  }, [questionId]);

  useEffect(() => {
    alive.current = true;
    if (!Number.isSafeInteger(questionId) || questionId < 1) { setError('问题地址无效'); return; }
    load().catch((reason) => { if (alive.current) setError((reason as Error).message); });
    return () => { alive.current = false; generation.current++; };
  }, [load, questionId]);

  const loadBounty = useCallback(async () => {
    try { setBounty((await api.getBounty(questionId)) ?? null); setBountyError(''); }
    catch (reason) { setBountyError((reason as Error).message); }
  }, [questionId]);

  useEffect(() => { void loadBounty(); }, [loadBounty]);
  useEffect(() => {
    if (!bounty || bounty.status !== 'OPEN') return;
    checkedExpiry.current = null;
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, [bounty?.id, bounty?.status]);
  useEffect(() => {
    if (!bounty || bounty.status !== 'OPEN') return;
    const expires = new Date(bounty.expiresAt).getTime();
    if (expires <= now && checkedExpiry.current !== bounty.id) { checkedExpiry.current = bounty.id; void loadBounty(); }
  }, [bounty, loadBounty, now]);
  useEffect(() => {
    if (!me) { setAvailablePoints(null); return; }
    let active = true;
    api.reputation(me.id).then((profile: ReputationProfile) => { if (active) setAvailablePoints(profile.availablePoints ?? null); }).catch(() => undefined);
    return () => { active = false; };
  }, [me]);

  useEffect(() => {
    if (!q || !location.hash) return;
    const anchor = location.hash.slice(1);
    const index = q.answers.findIndex((answer) => 'answer-' + answer.id === anchor || answer.comments.some((comment) => 'comment-' + comment.id === anchor));
    if (index >= answerLimit) { setAnswerLimit(index + 1); return; }
    document.getElementById(anchor)?.scrollIntoView({ block: 'center' });
  }, [q, location.hash, answerLimit]);

  const act = async (key: string, fn: () => Promise<unknown>) => {
    if (pending.current.has(key)) return;
    pending.current.add(key); setBusy([...pending.current]); setError('');
    try { await fn(); if (alive.current) { await load(); await loadBounty(); if (me) void api.reputation(me.id).then((profile) => setAvailablePoints(profile.availablePoints ?? null)).catch(() => undefined); } }
    catch (reason) { if (alive.current) setError((reason as Error).message); }
    finally { pending.current.delete(key); if (alive.current) setBusy([...pending.current]); }
  };
  const isBusy = (key: string) => busy.includes(key);

  const castVote = async (targetType: 'QUESTION' | 'ANSWER', targetId: number, current: number, requested: 1 | -1) => {
    const key = targetType === 'QUESTION' ? 'question-vote' : 'vote-' + targetId;
    if (pending.current.has(key) || !q) return;
    const next = current === requested ? 0 : requested;
    const snapshot = q;
    pending.current.add(key); setBusy([...pending.current]); setError('');
    setQ((value) => value && (targetType === 'QUESTION'
      ? { ...value, myVote: next, score: value.score + next - current }
      : { ...value, answers: value.answers.map((answer) => answer.id === targetId ? { ...answer, myVote: next, score: answer.score + next - current } : answer) }));
    try { await api.vote(targetType, targetId, next); await load(); }
    catch (reason) { if (alive.current) { setQ(snapshot); setError((reason as Error).message); } }
    finally { pending.current.delete(key); if (alive.current) setBusy([...pending.current]); }
  };

  const submitBounty = async () => {
    const points = Number(bountyPoints); const days = Number(bountyDays);
    if (!Number.isInteger(points) || points < 10 || points > 500 || !Number.isInteger(days) || days < 1 || days > 14) {
      setError('悬赏积分须为 10–500 的整数，有效天数须为 1–14 的整数'); return;
    }
    const key = 'bounty';
    if (pending.current.has(key)) return;
    pending.current.add(key); setBusy([...pending.current]); setError('');
    try {
      await api.bounty(questionId, points, days);
      setBountyDialog(false); await loadBounty();
      if (me) { const profile = await api.reputation(me.id); setAvailablePoints(profile.availablePoints ?? null); }
    } catch (reason) {
      if (reason instanceof NetworkError && reason.timedOut) {
        try {
          const current = await api.getBounty(questionId);
          if (current) { setBounty(current); setBountyDialog(false); }
          else setError('请求超时，查询后尚未发现悬赏，请确认状态后再重试');
        } catch { setError('请求超时且无法确认悬赏状态，请稍后刷新后再决定是否重试'); }
      } else setError((reason as Error).message);
    } finally { pending.current.delete(key); if (alive.current) setBusy([...pending.current]); }
  };

  if (!q) return error ? <ErrorState message={error} onRetry={() => { setError(''); void load().catch((reason) => setError((reason as Error).message)); }} /> : <LoadingState label="正在加载问题" />;

  const human = q.answers.filter((answer) => answer.source !== 'AI').sort((a, b) => Number(!!b.endorsedBy) - Number(!!a.endorsedBy)
    || Number(q.acceptedAnswerId === b.id) - Number(q.acceptedAnswerId === a.id) || b.score - a.score || a.id - b.id);
  const ai = q.answers.filter((answer) => answer.source === 'AI');
  const capabilities = {
    canAccept: q.permissions.canAccept,
    canReviewAcceptance: Boolean(q.canReview && q.permissions.canManage && typeof q.version === 'number'),
    canVerify: Boolean(q.canEndorse && q.permissions.canManage),
    canRevokeVerification: Boolean(q.canEndorse && q.permissions.canManage),
    canReport: false,
  };
  const renderAnswer = (answer: Answer) => <div className={'post' + (q.acceptedAnswerId === answer.id ? ' is-accepted' : '')} key={answer.id} id={'answer-' + answer.id}>
    <div className="votebox">
      <button className={answer.myVote === 1 ? 'active-vote' : ''} aria-label="赞同回答" disabled={!me || answer.source === 'AI' || isBusy('vote-' + answer.id)} onClick={() => void castVote('ANSWER', answer.id, answer.myVote, 1)}>▲</button>
      <b>{answer.score}</b>
      <button className={answer.myVote === -1 ? 'active-vote' : ''} aria-label="反对回答" disabled={!me || answer.source === 'AI' || isBusy('vote-' + answer.id)} onClick={() => void castVote('ANSWER', answer.id, answer.myVote, -1)}>▼</button>
      {q.acceptedAnswerId === answer.id && <span className="check-mark">已采纳</span>}
    </div>
    <div className="body">
      <ReactMarkdown skipHtml>{answer.body}</ReactMarkdown>
      <p className="muted"><Link to={'/users/' + answer.author.id}>{answer.author.displayName}</Link> · {answer.source === 'AI' ? 'AI 参考' : answer.source === 'AI_ASSISTED' ? 'AI 辅助' : answer.source === 'HUMAN' ? '人工回答' : '来源未标记'} · {new Date(answer.createdAt).toLocaleString('zh-CN')}
        {answer.endorsedBy && <span className="endorsed">课程回答认证 · {answer.endorsedBy.displayName}</span>}
      </p>
      <div className="actions">
        {answer.source !== 'AI' && capabilities.canAccept && q.acceptedAnswerId !== answer.id && <button disabled={isBusy('accept')} onClick={() => {
          if (q.acceptedAnswerId && !window.confirm('更换已采纳回答？原采纳状态及关联奖励将由后端重新处理。')) return;
          void act('accept', () => api.accept(q.id, answer.id));
        }}>{q.acceptedAnswerId ? '更换采纳' : '采纳此回答'}</button>}
        {answer.source !== 'AI' && capabilities.canAccept && q.acceptedAnswerId === answer.id && <button disabled={isBusy('unaccept')} onClick={() => { if (window.confirm('撤销此采纳？相关奖励和悬赏将由后端处理。')) void act('unaccept', () => api.unaccept(q.id)); }}>撤销采纳</button>}
        {answer.source !== 'AI' && q.acceptedAnswerId === answer.id && capabilities.canReviewAcceptance && <button disabled={isBusy('review')} onClick={() => { setActionReason(''); setActionDialog({ kind: 'review' }); }}>教师复核采纳</button>}
        {answer.source !== 'AI' && capabilities.canVerify && !answer.endorsedBy && <button disabled={isBusy('endorse-' + answer.id)} onClick={() => void act('endorse-' + answer.id, () => api.endorse(answer.id))}>课程回答认证</button>}
        {answer.source !== 'AI' && capabilities.canRevokeVerification && answer.endorsedBy && <button disabled={isBusy('revoke-endorsement-' + answer.id)} onClick={() => { setActionReason(''); setActionDialog({ kind: 'revoke-endorsement', answerId: answer.id }); }}>撤销认证</button>}
        {answer.canEdit && <button disabled={isBusy('answer')} onClick={() => { if (draft.trim() && !window.confirm('替换当前未提交的回答？')) return; setDraft(answer.body); setEditingId(answer.id); setPreview(false); }}>编辑回答</button>}
        {(answer.canDelete ?? answer.canEdit) && <button disabled={isBusy('delete-' + answer.id)} onClick={() => { if (window.confirm('确定删除此回答吗？关联采纳、认证和奖励状态可能变化。')) void act('delete-' + answer.id, async () => { await api.deleteAnswer(answer.id); if (editingId === answer.id) { setEditingId(null); setDraft(''); } }); }}>删除回答</button>}
      </div>
      <CommentList comments={answer.comments} targetType="ANSWER" targetId={answer.id} onDone={load} canComment={!!me} />
    </div>
  </div>;

  return <div className="layout"><article>
    <Link to={returnTo}>返回问题列表</Link>
    <h1>{q.title}</h1>
    <p className="muted"><Link to={'/users/' + q.author.id}>{q.author.displayName}</Link> · {new Date(q.createdAt).toLocaleString('zh-CN')} · {q.viewCount} 次浏览 · {q.course ? q.course.name : '历史未关联课程'} · {q.acceptedAnswerId ? '已采纳' : '未采纳'}</p>
    {bounty && <section className="panel bounty-summary"><h2>悬赏</h2><p>{bounty.sponsorName} 发布 {bounty.points} 积分 · 截止 {new Date(bounty.expiresAt).toLocaleString('zh-CN')} · {bounty.status}{bounty.status === 'OPEN' && ' · ' + formatRemaining(new Date(bounty.expiresAt).getTime() - now)}</p>{bounty.answerId && <Link to={'/questions/' + q.id + '#answer-' + bounty.answerId}>查看获奖回答</Link>}</section>}
    {bountyError && <p className="error" role="alert">悬赏状态暂不可用：{bountyError}</p>}
    <div className="post"><div className="votebox">
      <button className={q.myVote === 1 ? 'active-vote' : ''} aria-label="赞同问题" disabled={!me || isBusy('question-vote')} onClick={() => void castVote('QUESTION', q.id, q.myVote, 1)}>▲</button><b>{q.score}</b>
      <button className={q.myVote === -1 ? 'active-vote' : ''} aria-label="反对问题" disabled={!me || isBusy('question-vote')} onClick={() => void castVote('QUESTION', q.id, q.myVote, -1)}>▼</button>
    </div><div className="body"><ReactMarkdown skipHtml>{q.body}</ReactMarkdown>
      <div className="tagrow">{q.tags.map((tag) => <span className="tag" key={tag}>{tag}</span>)}</div>
      <div className="actions">
        {me && !bounty && q.status !== 'CLOSED' && q.permissions.canManage && <button onClick={() => setBountyDialog(true)}>发布悬赏</button>}
        {q.permissions.canEdit && <Link to={'/questions/' + q.id + '/edit'}>编辑问题</Link>}
        {q.permissions.canDelete && <button disabled={isBusy('delete-question')} onClick={() => { if (window.confirm('确定删除问题吗？关联回答将不可访问，采纳和认证等状态可能变化。')) void act('delete-question', async () => { await api.deleteQuestion(q.id); navigate(returnTo, { replace: true }); }); }}>删除问题</button>}
      </div>
      <CommentList comments={q.comments} targetType="QUESTION" targetId={q.id} onDone={load} canComment={!!me} />
    </div></div>
    <h2>{q.answerCount} 个回答</h2>
    {human.length === 0 && <p className="muted">暂无非 AI 参考回答。</p>}
    {human.slice(0, answerLimit).map(renderAnswer)}
    {human.length > answerLimit && <button onClick={() => setAnswerLimit((limit) => limit + 20)}>显示更多回答</button>}
    {ai.length > 0 && <section><h2>AI 参考</h2>{ai.map(renderAnswer)}</section>}
    {me && <section className="answer-form">
      <h3>{editingId ? '编辑回答' : '写回答'}</h3>
      {editingId && <p className="muted">修改正文后，已有认证可能需要重新审核。</p>}
      <div className="tabs"><button onClick={() => setPreview(false)}>编辑</button><button onClick={() => setPreview(true)}>预览</button></div>
      {preview ? <div className="body"><ReactMarkdown skipHtml>{draft || '暂无内容'}</ReactMarkdown></div> : <textarea aria-label="回答正文" rows={8} maxLength={30000} value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="支持 Markdown" />}
      <button className="primary" disabled={isBusy('answer') || !draft.trim()} onClick={() => void act('answer', async () => {
        if (editingId) await api.updateAnswer(editingId, draft.trim()); else await api.answer(q.id, draft.trim());
        setDraft(''); setEditingId(null); setPreview(false);
      })}>{isBusy('answer') ? '正在保存…' : editingId ? '保存修改' : '提交回答'}</button>
      {editingId && <button disabled={isBusy('answer')} onClick={() => { if (window.confirm('放弃当前修改？')) { setDraft(''); setEditingId(null); } }}>取消编辑</button>}
    </section>}
    {error && <p className="error" role="alert">{error}</p>}
    {bountyDialog && <div className="dialog-backdrop" role="presentation"><div className="dialog" role="dialog" aria-modal="true" aria-labelledby="bounty-title">
      <div className="dialog-heading"><h2 id="bounty-title">发布悬赏</h2><button aria-label="关闭" disabled={isBusy('bounty')} onClick={() => setBountyDialog(false)}>×</button></div>
      <p>可用积分：{availablePoints ?? '暂不可用'}</p>
      <label>悬赏积分<input type="number" min="10" max="500" step="1" value={bountyPoints} onChange={(event) => setBountyPoints(event.target.value)} /></label>
      <label>有效天数<input type="number" min="1" max="14" step="1" value={bountyDays} onChange={(event) => setBountyDays(event.target.value)} /></label>
      <p className="muted">发布后积分由后端冻结；采纳触发结算，到期状态由后端处理，浏览器不会自行结算。</p>
      <div className="dialog-actions"><button disabled={isBusy('bounty')} onClick={() => setBountyDialog(false)}>取消</button><button className="primary" disabled={isBusy('bounty')} onClick={() => void submitBounty()}>{isBusy('bounty') ? '正在确认…' : '确认发布'}</button></div>
    </div></div>}
    {actionDialog && <div className="dialog-backdrop" role="presentation"><div className="dialog" role="dialog" aria-modal="true" aria-labelledby="action-title">
      <div className="dialog-heading"><h2 id="action-title">{actionDialog.kind === 'review' ? '教师复核错误采纳' : '撤销回答认证'}</h2><button aria-label="关闭" onClick={() => setActionDialog(null)}>×</button></div>
      <label>原因<textarea rows={4} maxLength={500} value={actionReason} onChange={(event) => setActionReason(event.target.value)} /></label>
      <div className="dialog-actions"><button onClick={() => setActionDialog(null)}>取消</button><button className="primary" disabled={!actionReason.trim() || isBusy(actionDialog.kind === 'review' ? 'review' : 'revoke-endorsement-' + actionDialog.answerId)} onClick={() => {
        if (actionDialog.kind === 'review' && typeof q.version === 'number') void act('review', async () => { await api.reviewAcceptance(q.id, q.version as number, actionReason.trim()); setActionDialog(null); });
        if (actionDialog.kind === 'revoke-endorsement' && actionDialog.answerId) void act('revoke-endorsement-' + actionDialog.answerId, async () => { await api.revokeEndorsement(actionDialog.answerId as number, actionReason.trim()); setActionDialog(null); });
      }}>确认提交</button></div>
    </div></div>}
  </article></div>;
}

function formatRemaining(milliseconds: number) {
  if (milliseconds <= 0) return '正在向后端确认到期状态';
  const minutes = Math.ceil(milliseconds / 60_000);
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  return days > 0 ? `剩余 ${days} 天 ${hours} 小时` : `剩余 ${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`;
}

function CommentList({ comments, targetType, targetId, onDone, canComment }: {
  comments: Comment[]; targetType: 'QUESTION' | 'ANSWER'; targetId: number;
  onDone: () => Promise<void>; canComment: boolean;
}) {
  const [text, setText] = useState('');
  const [open, setOpen] = useState(false);
  const [parentId, setParentId] = useState<number | undefined>();
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  const [error, setError] = useState('');
  const [limit, setLimit] = useState(20);
  const location = useLocation();
  useEffect(() => { const index = comments.findIndex((comment) => '#comment-' + comment.id === location.hash); if (index >= limit) setLimit(index + 1); }, [comments, location.hash, limit]);
  return <div className="comments">
    {comments.slice(0, limit).map((comment) => <div className="comment" key={comment.id} id={'comment-' + comment.id}>
      {comment.parentId && <span className="muted">回复 {comments.find((parent) => parent.id === comment.parentId)?.author.displayName ?? '已不可用的评论'}：</span>}
      <span>{comment.body}</span><span className="muted"> · {comment.author.displayName} · {new Date(comment.createdAt).toLocaleString('zh-CN')}</span>
      {canComment && <button className="link" disabled={saving} onClick={() => { setParentId(comment.id); setOpen(true); }}>回复</button>}
    </div>)}
    {comments.length > limit && <button onClick={() => setLimit((value) => value + 20)}>显示更多评论</button>}
    {canComment && (open ? <div className="comment-form">
      {parentId && <p>回复 {comments.find((comment) => comment.id === parentId)?.author.displayName ?? '已不可用的评论'}<button disabled={saving} onClick={() => setParentId(undefined)}>取消回复</button></p>}
      <input aria-label="评论正文" maxLength={600} value={text} onChange={(event) => setText(event.target.value)} placeholder="发表评论" />
      <button disabled={saving || !text.trim()} onClick={async () => {
        if (pending.current) return;
        pending.current = true; setSaving(true); setError('');
        try {
          await api.comment(targetType, targetId, text.trim(), parentId);
          setText(''); setOpen(false); setParentId(undefined);
          try { await onDone(); } catch (reason) { setError('评论已发表，但刷新失败：' + (reason as Error).message); }
        } catch (reason) { setError((reason as Error).message); }
        finally { pending.current = false; setSaving(false); }
      }}>{saving ? '正在发表…' : '发表'}</button>
    </div> : <button className="link" onClick={() => { setParentId(undefined); setOpen(true); }}>添加评论</button>)}
    {error && <p className="error" role="alert">{error}</p>}
  </div>;
}
