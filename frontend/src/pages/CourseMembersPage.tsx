import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { EmptyState, ErrorState, LoadingState } from '../components/AsyncState';
import type { CourseMember, CourseRole } from '../types';

const labels: Record<CourseRole, string> = { STUDENT: '学生', TA: '助教', TEACHER: '教师' };

export function CourseMembersPage() {
  const { courseId } = useParams();
  const id = Number(courseId);
  const { me } = useAuth();
  const course = me?.courses.find((item) => item.id === id);
  const [members, setMembers] = useState<CourseMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);
  const canAssign = me?.role === 'ADMIN' || course?.myRole === 'TEACHER';
  const load = useCallback(async () => {
    if (!course) { setLoading(false); return; }
    setLoading(true);
    try { setMembers(await api.courseMembers(id)); setError(''); }
    catch (reason) { setError((reason as Error).message); }
    finally { setLoading(false); }
  }, [course, id]);
  useEffect(() => { load(); }, [load]);

  if (!Number.isFinite(id) || !course) return <div className="state state-error"><strong>无课程访问权限</strong><span>你尚未加入该课程，或课程已不可用。</span><Link to="/courses">返回我的课程</Link></div>;

  const assign = async (member: CourseMember, role: CourseRole) => {
    const action = role === 'TA' ? '任命为助教' : '调整为学生';
    if (!window.confirm(`确认将“${member.displayName}”${action}？`)) return;
    setBusyId(member.userId);
    try { await api.assignCourseRole(id, member.userId, role); await load(); }
    catch (reason) { setError((reason as Error).message); }
    finally { setBusyId(null); }
  };

  return <section>
    <div className="page-heading"><div><Link to="/courses">← 返回课程</Link><h1>{course.code} · {course.name}</h1><p className="muted">课程成员</p></div></div>
    {error && <ErrorState message={error} onRetry={load} />}
    {loading ? <LoadingState label="正在加载成员" /> : members.length === 0 ? <EmptyState title="暂无课程成员" /> :
      <div className="table-wrap"><table className="board"><thead><tr><th>成员</th><th>课程角色</th>{canAssign && <th>操作</th>}</tr></thead><tbody>
        {members.map((member) => <tr key={member.userId}><td>{member.displayName}{member.userId === me?.id && <span className="muted">（我）</span>}</td><td><span className="role">{labels[member.role]}</span></td>
          {canAssign && <td>{member.role === 'STUDENT' ? <button disabled={busyId === member.userId} onClick={() => assign(member, 'TA')}>任命助教</button> : member.role === 'TA' ? <button disabled={busyId === member.userId} onClick={() => assign(member, 'STUDENT')}>调整为学生</button> : <span className="muted">课程教师</span>}</td>}
        </tr>)}
      </tbody></table></div>}
  </section>;
}
