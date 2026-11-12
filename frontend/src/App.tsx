import { useEffect, useState } from 'react';
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { api } from './api';
import { AuthProvider, useAuth } from './auth';
import { ErrorState, LoadingState } from './components/AsyncState';
import { CourseProvider, useCourse } from './course';
import { AskPage } from './pages/AskPage';
import { CourseMembersPage } from './pages/CourseMembersPage';
import { CoursesPage } from './pages/CoursesPage';
import { CourseQuestionsPage } from './pages/CourseQuestionsPage';
import { LeaderboardPage } from './pages/LeaderboardPage';
import { LoginPage } from './pages/LoginPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { QuestionDetailPage } from './pages/QuestionDetailPage';
import { QuestionListPage } from './pages/QuestionListPage';

const roleLabels = { STUDENT: '学生', TA: '助教', TEACHER: '教师', ADMIN: '管理员' } as const;

function Header() {
  const { me, logout } = useAuth();
  const { currentCourse } = useCourse();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    if (!me) { setUnread(0); return; }
    const load = () => api.unreadCount().then((r) => setUnread(r.count)).catch(() => undefined);
    load();
    const timer = window.setInterval(load, 30_000);
    return () => window.clearInterval(timer);
  }, [me]);

  return (
    <header className="topbar">
      <Link className="brand" to="/">CampusOverflow</Link>
      <nav aria-label="主导航">
        <Link to="/">问题</Link>
        {me && <Link to="/courses">课程</Link>}
        <Link to="/leaderboard">排行榜</Link>
        {me && <Link to="/notifications">通知{unread > 0 && <span className="badge">{unread}</span>}</Link>}
      </nav>
      <div className="spacer" />
      {currentCourse && <Link className="course-context" to="/courses">{currentCourse.code} · {currentCourse.name}</Link>}
      {me ? <div className="user">
        <span>{me.displayName}{me.verified && <span className="verified" title="账号身份已认证">已认证</span>}</span>
        <span className="role">{roleLabels[me.role]}</span>
        <button onClick={async () => { await logout(); navigate('/login', { replace: true }); }}>退出</button>
      </div> : <Link className="primary" to="/login">登录 / 注册</Link>}
    </header>
  );
}

function RequireAuth({ children }: { children: JSX.Element }) {
  const { me, loading, sessionError } = useAuth();
  const location = useLocation();
  if (loading) return <LoadingState label="正在确认登录状态" />;
  if (sessionError) return <ErrorState message={sessionError} onRetry={() => window.location.reload()} />;
  return me ? children : <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
}

function NotFoundPage() {
  return <div className="state"><strong>页面不存在</strong><span>地址可能有误，或内容已被移除。</span><Link to="/">返回问题列表</Link></div>;
}

export function App() {
  return <BrowserRouter><AuthProvider><CourseProvider>
    <Header />
    <main><Routes>
      <Route path="/" element={<QuestionListPage />} />
      <Route path="/questions/:id" element={<QuestionDetailPage />} />
      <Route path="/ask" element={<RequireAuth><AskPage /></RequireAuth>} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/courses" element={<RequireAuth><CoursesPage /></RequireAuth>} />
      <Route path="/courses/:courseId/questions" element={<RequireAuth><CourseQuestionsPage /></RequireAuth>} />
      <Route path="/courses/:courseId/members" element={<RequireAuth><CourseMembersPage /></RequireAuth>} />
      <Route path="/leaderboard" element={<LeaderboardPage />} />
      <Route path="/notifications" element={<RequireAuth><NotificationsPage /></RequireAuth>} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes></main>
  </CourseProvider></AuthProvider></BrowserRouter>;
}
