import ReactMarkdown from 'react-markdown';
import { useEffect, useState } from 'react';
import { Link, useBlocker, useBeforeUnload, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { useCourse } from '../course';

const count = (value: string) => Array.from(value.trim()).length;

export function AskPage() {
  const { me } = useAuth();
  const { currentCourse } = useCourse();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const { id: editParam } = useParams();
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [tags, setTags] = useState('');
  const [courseId, setCourseId] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [preview, setPreview] = useState(false);
  const [dirty, setDirty] = useState(false);
  const [editReady, setEditReady] = useState(false);
  const [similar, setSimilar] = useState<{ id: number; title: string; accepted: boolean }[]>([]);
  const editId = Number(editParam ?? params.get('edit'));
  const blocker = useBlocker(dirty && !saving);
  useEffect(() => {
    if (blocker.state === 'blocked') {
      if (window.confirm('有未保存的内容，确定离开？')) blocker.proceed();
      else blocker.reset();
    }
  }, [blocker.state, blocker.proceed, blocker.reset]);

  useBeforeUnload((event) => { if (dirty && !saving) { event.preventDefault(); event.returnValue = ''; } });
  useEffect(() => {
    const confirmLeave = (event: MouseEvent) => {
      const link = event.target instanceof Element ? event.target.closest('a[href]') : null;
      if (dirty && !saving && link && !window.confirm('有未保存的内容，确定离开？')) { event.preventDefault(); event.stopPropagation(); }
    };
    document.addEventListener('click', confirmLeave, true);
    return () => document.removeEventListener('click', confirmLeave, true);
  }, [dirty, saving]);
  useEffect(() => {
    if (editId || !me?.courses.length) return;
    const requested = Number(params.get('courseId'));
    if (me.courses.some((course) => course.id === requested)) { setCourseId(String(requested)); return; }
    const initial = currentCourse && me.courses.some((course) => course.id === currentCourse.id)
      ? currentCourse.id : me.courses[0].id;
    setCourseId(String(initial));
  }, [currentCourse, me, editId, params]);
  useEffect(() => {
    if (!editId || !Number.isFinite(editId)) return;
    let active = true; setEditReady(false);
    api.question(editId).then((question) => {
      if (!active) return;
      if (!question.permissions.canEdit) { setError('你没有编辑此问题的权限'); return; }
      setTitle(question.title); setBody(question.body); setTags(question.tags.join(' '));
      setCourseId(String(question.course?.id ?? '')); setDirty(false); setEditReady(true);
    }).catch((reason) => { if (active) setError((reason as Error).message); });
    return () => { active = false; };
  }, [editId]);

  useEffect(() => {
    setSimilar([]);
    if (!courseId || title.trim().length < 3) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      api.search(title.trim(), { courseId: Number(courseId), size: 5, sort: 'relevance', signal: controller.signal })
        .then((result) => { if (!controller.signal.aborted) setSimilar(result.items.filter((question) => question.id !== editId)); }).catch(() => undefined);
    }, 400);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [title, body, courseId, editId]);

  const tagList = tags.split(/[,，\s]+/).filter(Boolean);
  const validation = editId && !editReady ? '问题尚未加载或无编辑权限'
    : !me?.courses.some((course) => course.id === Number(courseId)) ? '请选择已加入的课程'
    : !courseId ? '请选择所属课程'
    : count(title) < 10 || count(title) > 150 ? '标题需为 10–150 字'
    : count(body) < 20 || body.length > 30000 ? '正文需为 20–30000 字'
    : tagList.length < 1 || tagList.length > 5 ? '标签需为 1–5 个'
    : new Set(tagList.map((tag) => tag.toLocaleLowerCase())).size !== tagList.length ? '标签不能重复'
    : '';

  const submit = async () => {
    setError('');
    if (validation) { setError(validation); return; }
    setSaving(true);
    try {
      const data = { courseId: Number(courseId), title: title.trim(), body: body.trim(), tags: tagList };
      if (editId) {
        await api.updateQuestion(editId, { title: data.title, body: data.body, tags: data.tags });
        setDirty(false);
        navigate('/questions/' + editId);
      } else {
        const result = await api.ask(data);
        setDirty(false);
        navigate('/questions/' + result.id);
      }
      setDirty(false);
    } catch (reason) { setError((reason as Error).message); }
    finally { setSaving(false); }
  };

  return <div className="form-page">
    <h1>{editId ? '编辑问题' : '提出一个问题'}</h1>
    <label>所属课程
      <select required disabled={!!editId || saving} value={courseId} onChange={(event) => { setCourseId(event.target.value); setDirty(true); }}>
        <option value="">请选择课程</option>
        {me?.courses.map((course) => <option key={course.id} value={course.id}>{course.code} {course.name}</option>)}
      </select>
    </label>
    {!me?.courses.length && <p><Link to='/courses'>请先加入课程</Link></p>}
    {editId > 0 && <p className='muted'>所属课程不可修改；修改正文可能影响认证或引用，需要重新审核。</p>}
    <label>标题（10–150 字）
      <input value={title} maxLength={150} onChange={(event) => { setTitle(event.target.value); setDirty(true); }} />
    </label>
    <div className="tabs" role="tablist">
      <button type="button" className={!preview ? 'active' : ''} onClick={() => setPreview(false)}>编辑</button>
      <button type="button" className={preview ? 'active' : ''} onClick={() => setPreview(true)}>预览</button>
    </div>
    <label>正文（Markdown，至少 20 字；说明尝试过什么、期望结果和具体报错）
      {preview ? <div className="body"><ReactMarkdown skipHtml>{body || '暂无内容'}</ReactMarkdown></div>
        : <textarea rows={12} value={body} onChange={(event) => { setBody(event.target.value); setDirty(true); }} />}
    </label>
    <label>标签（1–5 个，用空格或逗号分隔）
      <input value={tags} onChange={(event) => { setTags(event.target.value); setDirty(true); }} placeholder="数据结构 红黑树" />
    </label>
    {similar.length > 0 && <section><h3>相似问题</h3><ul>{similar.map((question) => <li key={question.id}><Link to={'/questions/' + question.id}>{question.title}</Link> · {question.accepted ? '已采纳' : '未采纳'}</li>)}</ul></section>}
    {error && <p className="error" role="alert">{error}</p>}
    <button className="primary" disabled={saving || !courseId || (!!editId && !editReady)} onClick={submit}>{saving ? '正在保存…' : editId ? '保存修改' : '发布问题'}</button>
  </div>;
}
