import ReactMarkdown from 'react-markdown';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { api, ApiError } from '../api';
import { useAuth } from '../auth';
import { ErrorState, LoadingState } from '../components/AsyncState';
import type { Answer, Comment, QuestionDetail } from '../types';

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
    try { await fn(); if (alive.current) await load(); }
    catch (reason) { if (alive.current) setError((reason as Error).message); }
    finally { pending.current.delete(key); if (alive.current) setBusy([...pending.current]); }
  };
  const isBusy = (key: string) => busy.includes(key);

  if (!q) return error ? <ErrorState message={error} onRetry={() => { setError(''); void load().catch((reason) => setError((reason as Error).message)); }} /> : <LoadingState label="正在加载问题" />;

  const human = q.answers.filter((answer) => answer.source !== 'AI').sort((a, b) => Number(!!b.endorsedBy) - Number(!!a.endorsedBy)
    || Number(q.acceptedAnswerId === b.id) - Number(q.acceptedAnswerId === a.id) || b.score - a.score || a.id - b.id);
  const ai = q.answers.filter((answer) => answer.source === 'AI');
  const renderAnswer = (answer: Answer) => <div className={'post' + (q.acceptedAnswerId === answer.id ? ' is-accepted' : '')} key={answer.id} id={'answer-' + answer.id}>
    <div className="votebox">
      <button aria-label="赞同回答" disabled={!me || answer.source === 'AI' || isBusy('vote-' + answer.id)} onClick={() => void act('vote-' + answer.id, () => api.vote('ANSWER', answer.id, answer.myVote === 1 ? 0 : 1))}>▲</button>
      <b>{answer.score}</b>
      <button aria-label="反对回答" disabled={!me || answer.source === 'AI' || isBusy('vote-' + answer.id)} onClick={() => void act('vote-' + answer.id, () => api.vote('ANSWER', answer.id, answer.myVote === -1 ? 0 : -1))}>▼</button>
      {q.acceptedAnswerId === answer.id && <span className="check-mark">已采纳</span>}
    </div>
    <div className="body">
      <ReactMarkdown skipHtml>{answer.body}</ReactMarkdown>
      <p className="muted">{answer.author.displayName} · {answer.source === 'AI' ? 'AI 参考' : answer.source === 'AI_ASSISTED' ? 'AI 辅助' : answer.source === 'HUMAN' ? '人工回答' : '来源未标记'} · {new Date(answer.createdAt).toLocaleString('zh-CN')}
        {answer.endorsedBy && <span className="endorsed">课程回答认证 · {answer.endorsedBy.displayName}</span>}
      </p>
      <div className="actions">
        {answer.source !== 'AI' && q.permissions.canAccept && !q.acceptedAnswerId && <button disabled={isBusy('accept')} onClick={() => void act('accept', () => api.accept(q.id, answer.id))}>采纳此回答</button>}
        {answer.source !== 'AI' && q.permissions.canManage && !answer.endorsedBy && <button disabled={isBusy('endorse-' + answer.id)} onClick={() => void act('endorse-' + answer.id, () => api.endorse(answer.id))}>课程回答认证</button>}
        {answer.canEdit && <button disabled={isBusy('answer')} onClick={() => { if (draft.trim() && !window.confirm('替换当前未提交的回答？')) return; setDraft(answer.body); setEditingId(answer.id); setPreview(false); }}>编辑回答</button>}
        {(answer.canDelete ?? answer.canEdit) && <button disabled={isBusy('delete-' + answer.id)} onClick={() => { if (window.confirm('确定删除此回答吗？关联采纳、认证和奖励状态可能变化。')) void act('delete-' + answer.id, async () => { await api.deleteAnswer(answer.id); if (editingId === answer.id) { setEditingId(null); setDraft(''); } }); }}>删除回答</button>}
      </div>
      <CommentList comments={answer.comments} targetType="ANSWER" targetId={answer.id} onDone={load} canComment={!!me} />
    </div>
  </div>;

  return <div className="layout"><article>
    <Link to={returnTo}>返回问题列表</Link>
    <h1>{q.title}</h1>
    <p className="muted">{q.author.displayName} · {new Date(q.createdAt).toLocaleString('zh-CN')} · {q.viewCount} 次浏览 · {q.course ? q.course.name : '历史未关联课程'} · {q.acceptedAnswerId ? '已采纳' : '未采纳'}</p>
    <div className="post"><div className="votebox">
      <button aria-label="赞同问题" disabled={!me || isBusy('question-vote')} onClick={() => void act('question-vote', () => api.vote('QUESTION', q.id, q.myVote === 1 ? 0 : 1))}>▲</button><b>{q.score}</b>
      <button aria-label="反对问题" disabled={!me || isBusy('question-vote')} onClick={() => void act('question-vote', () => api.vote('QUESTION', q.id, q.myVote === -1 ? 0 : -1))}>▼</button>
    </div><div className="body"><ReactMarkdown skipHtml>{q.body}</ReactMarkdown>
      <div className="tagrow">{q.tags.map((tag) => <span className="tag" key={tag}>{tag}</span>)}</div>
      <div className="actions">
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
  </article></div>;
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
