import { useCallback, useEffect, useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../api';
import { useAuth } from '../auth';
import { EmptyState, ErrorState, LoadingState } from '../components/AsyncState';
import { useCourse } from '../course';
import type { Course } from '../types';

const courseRoleLabels = { STUDENT: '学生', TA: '助教', TEACHER: '教师' } as const;

export function CoursesPage() {
  const { me, refresh } = useAuth();
  const { currentCourse, selectCourse } = useCourse();
  const navigate = useNavigate();
  const [courses, setCourses] = useState<Course[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const load = useCallback(async () => {
    setLoading(true);
    try { setCourses(await api.courses()); setError(''); }
    catch (reason) { setError((reason as Error).message); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const join = async (course: Course) => {
    setBusyId(course.id);
    try { await api.joinCourse(course.id); await Promise.all([load(), refresh()]); }
    catch (reason) { setError((reason as Error).message); }
    finally { setBusyId(null); }
  };
  const mine = courses.filter((course) => course.myRole);
  const available = courses.filter((course) => !course.myRole);
  const canCreate = me?.role === 'TEACHER' || me?.role === 'ADMIN';

  return <section className="courses-page">
    <div className="page-heading"><div><h1>我的课程</h1><p className="muted">选择课程后，页面会使用对应的课程范围。</p></div>
      {canCreate && <button className="primary" onClick={() => setCreateOpen(true)}>创建课程</button>}
    </div>
    {error && <ErrorState message={error} onRetry={load} />}
    {loading ? <LoadingState label="正在加载课程" /> : <>
      {mine.length === 0 ? <EmptyState title="还没有加入课程"><span>可从下方课程目录选择并加入。</span></EmptyState> :
        <div className="course-grid">{mine.map((course) => <CourseCard key={course.id} course={course} active={currentCourse?.id === course.id}>
          <span className="role">{course.myRole ? courseRoleLabels[course.myRole] : ''}</span>
          <button onClick={() => selectCourse(course)}>{currentCourse?.id === course.id ? '当前课程' : '切换到此课程'}</button>
          <button className="primary" onClick={() => { selectCourse(course); navigate(`/courses/${course.id}/questions`); }}>进入课程</button>
          <Link to={`/courses/${course.id}/members`}>查看成员</Link>
        </CourseCard>)}</div>}
      <div className="section-heading"><h2>课程目录</h2><span className="muted">{available.length} 门可加入课程</span></div>
      {available.length === 0 ? <EmptyState title="暂无其他课程" /> :
        <div className="course-grid">{available.map((course) => <CourseCard key={course.id} course={course} active={false}>
          <button className="primary" disabled={busyId === course.id} onClick={() => join(course)}>{busyId === course.id ? '正在加入…' : '加入课程'}</button>
        </CourseCard>)}</div>}
    </>}
    {createOpen && <CreateCourseDialog onClose={() => setCreateOpen(false)} onCreated={async () => { setCreateOpen(false); await Promise.all([load(), refresh()]); }} />}
  </section>;
}

function CourseCard({ course, active, children }: { course: Course; active: boolean; children: ReactNode }) {
  return <article className={`course-card${active ? ' active' : ''}`}>
    <div className="course-code">{course.code}</div><h3>{course.name}</h3><p>{course.term}</p>
    <p className="muted">授课教师：{course.teacherName}</p><div className="card-actions">{children}</div>
  </article>;
}

function CreateCourseDialog({ onClose, onCreated }: { onClose: () => void; onCreated: () => Promise<void> }) {
  const [form, setForm] = useState({ code: '', name: '', term: '' });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const empty = Object.fromEntries(Object.entries(form).filter(([, value]) => !value.trim()).map(([key]) => [key, '此项不能为空']));
    if (Object.keys(empty).length) { setFieldErrors(empty); return; }
    setSubmitting(true);
    try { await api.createCourse(form); await onCreated(); }
    catch (reason) { if (reason instanceof ApiError) setFieldErrors(reason.fieldErrors); setError((reason as Error).message); }
    finally { setSubmitting(false); }
  };
  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) onClose(); }}>
    <form className="dialog" role="dialog" aria-modal="true" aria-labelledby="create-course-title" onSubmit={submit}>
      <div className="dialog-heading"><h2 id="create-course-title">创建课程</h2><button type="button" aria-label="关闭" onClick={onClose}>×</button></div>
      <CourseField label="课程代码" error={fieldErrors.code}><input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></CourseField>
      <CourseField label="课程名称" error={fieldErrors.name}><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></CourseField>
      <CourseField label="学期" error={fieldErrors.term}><input value={form.term} onChange={(e) => setForm({ ...form, term: e.target.value })} /></CourseField>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="dialog-actions"><button type="button" onClick={onClose}>取消</button><button className="primary" disabled={submitting}>{submitting ? '正在创建…' : '创建'}</button></div>
    </form>
  </div>;
}

function CourseField({ label, error, children }: { label: string; error?: string; children: JSX.Element }) {
  return <label>{label}{children}{error && <span className="field-error">{error}</span>}</label>;
}
