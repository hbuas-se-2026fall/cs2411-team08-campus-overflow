import type {
  Course, CourseMember, CourseRole, LeaderboardEntry, Me, Notification, Page, QuestionDetail,
  QuestionSummary, ReputationProfile, SearchResult,
} from './types';

/** 后端以 RFC 9457 Problem Details 返回错误，这里统一转成带业务码的异常。 */
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string, readonly fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
  }
}

export class NetworkError extends Error {
  constructor(message: string, readonly timedOut = false) {
    super(message);
    this.name = 'NetworkError';
  }
}

function cookie(name: string): string | undefined {
  return document.cookie.split('; ').find((c) => c.startsWith(`${name}=`))?.split('=')[1];
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    // CSRF 双提交：从 XSRF-TOKEN Cookie 取出 token 放进请求头
    let token = cookie('XSRF-TOKEN');
    if (!token) {
      await fetch('/api/v1/auth/csrf', { credentials: 'include' });
      token = cookie('XSRF-TOKEN');
    }
    if (token) headers['X-XSRF-TOKEN'] = decodeURIComponent(token);
  }
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 15_000);
  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method, headers, credentials: 'include', signal: controller.signal,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    const timedOut = error instanceof DOMException && error.name === 'AbortError';
    throw new NetworkError(timedOut ? '请求超时，请确认操作结果后再重试' : '网络连接失败，请检查网络后重试', timedOut);
  } finally {
    window.clearTimeout(timeout);
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: Record<string, unknown> | undefined;
  if (text) {
    try {
      data = JSON.parse(text) as Record<string, unknown>;
    } catch {
      if (res.ok) throw new ApiError(res.status, 'SYS-INVALID-RESPONSE', '服务器返回了无法识别的响应');
    }
  }
  if (!res.ok) {
    if (res.status === 401) window.dispatchEvent(new Event('auth:unauthorized'));
    const errors = Array.isArray(data?.errors) ? data.errors : [];
    const fieldErrors = Object.fromEntries(errors.flatMap((item) => {
      if (!item || typeof item !== 'object') return [];
      const entry = item as Record<string, unknown>;
      return typeof entry.field === 'string' && typeof entry.message === 'string' ? [[entry.field, entry.message]] : [];
    }));
    throw new ApiError(
      res.status,
      typeof data?.code === 'string' ? data.code : 'SYS-000',
      typeof data?.detail === 'string' ? data.detail : statusMessage(res.status),
      fieldErrors,
    );
  }
  return data as T;
}

function statusMessage(status: number) {
  if (status === 401) return '登录已失效，请重新登录';
  if (status === 403) return '你没有权限执行此操作';
  if (status === 404) return '请求的内容不存在或已不可用';
  if (status === 409) return '内容状态已变化，请刷新后重试';
  if (status === 429) return '操作过于频繁，请稍后重试';
  return '请求失败，请稍后重试';
}

const get = <T>(path: string) => request<T>('GET', path);

export const api = {
  csrf: () => get<{ token: string }>('/v1/auth/csrf'),
  register: (body: Record<string, string>) => request<{ id: number }>('POST', '/v1/auth/register', body),
  login: (username: string, password: string) => request<Me>('POST', '/v1/auth/login', { username, password }),
  logout: () => request<void>('POST', '/v1/auth/logout'),
  me: () => get<Me>('/v1/auth/me'),

  courses: () => get<Course[]>('/v1/courses'),
  myCourses: () => get<Course[]>('/v1/courses/mine'),
  createCourse: (body: { code: string; name: string; term: string }) =>
    request<{ id: number }>('POST', '/v1/courses', body),
  joinCourse: (courseId: number) => request<void>('POST', `/v1/courses/${courseId}/join`),
  courseMembers: (courseId: number) => get<CourseMember[]>(`/v1/courses/${courseId}/members`),
  assignCourseRole: (courseId: number, userId: number, role: CourseRole) =>
    request<void>('PUT', `/v1/courses/${courseId}/members/${userId}`, { role }),

  questions: (page = 1, courseId?: number) =>
    get<Page<QuestionSummary>>(`/v1/questions?page=${page}${courseId ? `&courseId=${courseId}` : ''}`),
  question: (id: number) => get<QuestionDetail>(`/v1/questions/${id}`),
  ask: (body: { courseId?: number; title: string; body: string; tags: string[] }) =>
    request<{ id: number }>('POST', '/v1/questions', body),
  deleteQuestion: (id: number) => request<void>('DELETE', `/v1/questions/${id}`),
  answer: (questionId: number, body: string) =>
    request<{ id: number }>('POST', `/v1/questions/${questionId}/answers`, { body }),
  accept: (questionId: number, answerId: number) =>
    request<void>('PUT', `/v1/questions/${questionId}/accepted-answer`, { answerId }),
  endorse: (answerId: number) => request<void>('POST', `/v1/answers/${answerId}/endorsement`, {}),
  vote: (targetType: 'QUESTION' | 'ANSWER', targetId: number, value: number) =>
    request<{ myVote: number; scoreDelta: number }>('PUT', '/v1/votes', { targetType, targetId, value }),
  comment: (targetType: 'QUESTION' | 'ANSWER', targetId: number, body: string, parentId?: number) =>
    request<{ id: number }>('POST', '/v1/comments', { targetType, targetId, body, parentId }),

  search: (q: string, params: { tag?: string; sort?: string; unanswered?: boolean; page?: number } = {}) => {
    const search = new URLSearchParams({ q, page: String(params.page ?? 1) });
    if (params.tag) search.set('tag', params.tag);
    if (params.sort) search.set('sort', params.sort);
    if (params.unanswered) search.set('unanswered', 'true');
    return get<Page<SearchResult>>(`/v1/search/questions?${search}`);
  },
  related: (questionId: number) =>
    get<{ id: number; title: string; answerCount: number; accepted: boolean }[]>(`/v1/questions/${questionId}/related`),
  popularTags: () => get<{ name: string; questionCount: number }[]>('/v1/tags/popular?limit=15'),

  leaderboard: (period: string) => get<LeaderboardEntry[]>(`/v1/leaderboard?period=${period}`),
  reputation: (userId: number) => get<ReputationProfile>(`/v1/users/${userId}/reputation`),
  bounty: (questionId: number, points: number, days: number) =>
    request<{ id: number }>('POST', `/v1/questions/${questionId}/bounty`, { points, days }),

  notifications: () => get<Page<Notification>>('/v1/notifications'),
  unreadCount: () => get<{ count: number }>('/v1/notifications/unread-count'),
  readAll: () => request<{ updated: number }>('POST', '/v1/notifications/read-all', {}),
};
