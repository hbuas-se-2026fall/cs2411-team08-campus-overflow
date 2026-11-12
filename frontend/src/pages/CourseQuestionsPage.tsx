import { Link, useParams } from 'react-router-dom';
import { useAuth } from '../auth';
import { QuestionListPage } from './QuestionListPage';

export function CourseQuestionsPage() {
  const { courseId } = useParams();
  const id = Number(courseId);
  const { me } = useAuth();
  const course = me?.courses.find((item) => item.id === id);
  if (!Number.isFinite(id) || !course) return <div className="state state-error"><strong>无课程访问权限</strong><span>你尚未加入该课程，或课程已不可用。</span><Link to="/courses">返回我的课程</Link></div>;
  return <section>
    <div className="course-banner"><div><span>{course.code} · {course.term}</span><h1>{course.name}</h1><p>授课教师：{course.teacherName} · {course.myRole}</p></div>
      <div className="course-actions"><Link to={`/courses/${course.id}/members`}>课程成员</Link><Link to="/courses">切换课程</Link></div>
    </div>
    <QuestionListPage courseId={course.id} />
  </section>;
}
