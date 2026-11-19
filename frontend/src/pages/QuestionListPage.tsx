import { useEffect, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { useCourse } from '../course';
import { EmptyState, ErrorState, LoadingState } from '../components/AsyncState';
import type { Page, QuestionSummary, SearchResult } from '../types';

const pageSize = 20;
export function QuestionListPage({ courseId: requestedCourseId }: { courseId?: number } = {}) {
  const { currentCourse } = useCourse();
  const location = useLocation();
  const courseId = requestedCourseId ?? currentCourse?.id;
  const { me } = useAuth();
  const [url, setUrl] = useSearchParams();
  const keyword = url.get('q') ?? '';
  const activeTag = url.get('tag') ?? '';
  const requestedSort = url.get('sort') ?? 'newest';
  const sort = ['newest', 'active', 'hot', 'relevance'].includes(requestedSort) ? requestedSort : 'newest';
  const unanswered = url.get('unanswered') === 'true';
  const bounty = url.get('bounty') === 'true';
  const requestedPage = Number(url.get('page') ?? 1);
  const page = Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1;
  const [data, setData] = useState<Page<QuestionSummary | SearchResult> | null>(null);
  const [draft, setDraft] = useState(keyword);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => setDraft(keyword), [keyword]);
  useEffect(() => {
    if (!courseId) return;
    let active = true;
    const controller = new AbortController();
    setLoading(true); setError('');
    const searching = !!keyword.trim() || !!activeTag || unanswered || bounty || sort !== 'newest';
    const request = searching
      ? api.search(keyword.trim(), { courseId, tag: activeTag || undefined, sort, unanswered, bounty, page, size: pageSize, signal: controller.signal })
      : api.questions(page, courseId, pageSize, controller.signal);
    request.then((result) => { if (active) setData(result); })
      .catch((reason) => { if (active) setError((reason as Error).message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [keyword, activeTag, sort, unanswered, bounty, page, courseId]);

  const update = (changes: Record<string, string | null>, resetPage = true) => {
    const next = new URLSearchParams(url);
    Object.entries(changes).forEach(([key, value]) => value ? next.set(key, value) : next.delete(key));
    if (resetPage) next.delete('page');
    setUrl(next, { replace: true });
  };
  const submitSearch = (event: FormEvent) => { event.preventDefault(); update({ q: draft.trim() || null }); };
  const searching = !!keyword.trim() || !!activeTag || unanswered || bounty || sort !== 'newest';
  const totalPages = Math.max(1, Math.ceil((data?.total ?? 0) / pageSize));

  if (!courseId) return <Navigate to='/courses' replace />;

  return <div className={'layout' + (courseId ? ' scoped-list' : '')}>
    <section>
      <form className="toolbar" onSubmit={submitSearch}>
        <input aria-label="搜索问题" placeholder="搜索问题" value={draft} onChange={(event) => setDraft(event.target.value)} />
        <button type="submit">搜索</button>
        <select aria-label="排序方式" value={sort} onChange={(event) => update({ sort: event.target.value === 'newest' ? null : event.target.value })}>
          <option value="newest">最新</option><option value="active">最近活跃</option><option value="hot">热度</option><option value="relevance">相关度</option>
        </select>
        <label className="check"><input type="checkbox" checked={unanswered} onChange={(event) => update({ unanswered: event.target.checked ? 'true' : null })} />未解决</label>
        <label className="check"><input type="checkbox" checked={bounty} onChange={(event) => update({ bounty: event.target.checked ? 'true' : null })} />有悬赏</label>
        {me && <Link className="primary" to={courseId ? '/ask?courseId=' + courseId : '/ask'}>我要提问</Link>}
      </form>
      {activeTag && <p className="muted">标签：<b>{activeTag}</b> <button type="button" onClick={() => update({ tag: null })}>清除标签</button></p>}
      {searching && <button type="button" onClick={() => { setDraft(''); setUrl(new URLSearchParams(), { replace: true }); }}>清空筛选</button>}
      {error && <ErrorState message={error} />}
      {loading && <LoadingState label="正在加载问题" />}
      {!loading && !error && data && data.items.length > 0 && <ul className="qlist">{data.items.map((question) => <li key={question.id}>
        <div className="stats"><span><b>{question.score}</b> 票</span><span className={question.accepted ? 'accepted' : ''}><b>{question.answerCount}</b> 回答{question.accepted && ' · 已采纳'}</span><span>{question.viewCount} 浏览</span>{'bountyOpen' in question && question.bountyOpen && <span>有悬赏</span>}</div>
        <div className="qmain"><Link className="qtitle" to={'/questions/' + question.id} state={{ from: location.pathname + location.search }}>{highlight(question.title, keyword)}</Link><p className="excerpt">{highlight(question.excerpt, keyword)}</p><div className="tagrow">{question.tags.map((tag) => <button key={tag} className="tag" onClick={() => update({ tag })}>{tag}</button>)}<span className="muted">{question.author.displayName} · {new Date(question.createdAt).toLocaleString('zh-CN')}</span></div></div>
      </li>)}</ul>}
      {!loading && !error && data?.items.length === 0 && <EmptyState title={searching ? '没有搜索结果' : '此课程还没有问题'}><span>{searching ? '试试调整或清空筛选条件。' : '可以提出第一个问题。'}</span></EmptyState>}
      {!loading && !error && data && data.total > pageSize && <nav className="pagination" aria-label="问题分页"><button disabled={page <= 1} onClick={() => update({ page: String(page - 1) }, false)}>上一页</button><span>第 {page} / {totalPages} 页 · 共 {data.total} 个问题</span><button disabled={page >= totalPages} onClick={() => update({ page: String(page + 1) }, false)}>下一页</button></nav>}
    </section>
  </div>;
}

function highlight(text: string, keyword: string) {
  const needle = keyword.trim();
  if (!needle) return text;
  const result = [];
  let from = 0;
  const lower = text.toLocaleLowerCase();
  const query = needle.toLocaleLowerCase();
  for (let index = lower.indexOf(query); index >= 0; index = lower.indexOf(query, from)) {
    result.push(text.slice(from, index), <mark key={index}>{text.slice(index, index + needle.length)}</mark>);
    from = index + needle.length;
  }
  result.push(text.slice(from));
  return result;
}
