import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useAuth } from './auth';
import type { Course } from './types';

interface CourseState {
  currentCourse: Course | null;
  selectCourse: (course: Course | null) => void;
}

const CourseContext = createContext<CourseState | null>(null);

export function CourseProvider({ children }: { children: ReactNode }) {
  const { me } = useAuth();
  const [selection, setSelection] = useState<{ userId: number; courseId: number | null } | null>(null);

  useEffect(() => {
    if (!me) {
      setSelection(null);
      return;
    }
    const storedId = Number(sessionStorage.getItem(`currentCourse:${me.id}`));
    setSelection({ userId: me.id, courseId: me.courses.some((course) => course.id === storedId) ? storedId : null });
  }, [me]);

  const currentCourse = me && selection?.userId === me.id
    ? me.courses.find((course) => course.id === selection.courseId) ?? null
    : null;
  const value = useMemo<CourseState>(() => ({
    currentCourse,
    selectCourse: (course) => {
      if (me) setSelection({ userId: me.id, courseId: course?.id ?? null });
      if (!me) return;
      if (course) sessionStorage.setItem(`currentCourse:${me.id}`, String(course.id));
      else sessionStorage.removeItem(`currentCourse:${me.id}`);
    },
  }), [currentCourse, me, selection]);

  return <CourseContext.Provider value={value}>{children}</CourseContext.Provider>;
}

export function useCourse() {
  const context = useContext(CourseContext);
  if (!context) throw new Error('useCourse 必须在 CourseProvider 内使用');
  return context;
}
