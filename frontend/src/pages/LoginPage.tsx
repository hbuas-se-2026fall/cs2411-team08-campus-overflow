import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, api } from '../api';
import { useAuth } from '../auth';

const emptyForm = { username: '', password: '', confirmPassword: '', displayName: '', email: '', college: '', className: '' };

export function LoginPage() {
  const { login, me, loading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);
  const set = (key: keyof typeof form) => (event: { target: { value: string } }) => {
    setForm((current) => ({ ...current, [key]: event.target.value }));
    setFieldErrors((current) => ({ ...current, [key]: '' }));
  };

  if (!loading && me) return <Navigate to="/courses" replace />;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    if (!form.username.trim()) nextErrors.username = '请输入学号或工号';
    if (!form.password) nextErrors.password = '请输入密码';
    if (mode === 'register') {
      if (form.username.trim().length < 4 || form.username.trim().length > 20) nextErrors.username = '学号或工号长度应为 4–20 位';
      if (form.password.length < 8 || form.password.length > 64) nextErrors.password = '密码长度应为 8–64 位';
      if (form.password !== form.confirmPassword) nextErrors.confirmPassword = '两次输入的密码不一致';
      if (form.displayName.trim().length < 2 || form.displayName.trim().length > 20) nextErrors.displayName = '昵称长度应为 2–20 位';
      if (!form.email.trim()) nextErrors.email = '请输入校园邮箱';
    }
    if (Object.keys(nextErrors).length) { setFieldErrors(nextErrors); return; }
    setSubmitting(true);
    setError('');
    try {
      if (mode === 'register') {
        const { confirmPassword: _, ...payload } = form;
        await api.register(payload);
      }
      await login(form.username.trim(), form.password);
      const requested = (location.state as { from?: string } | null)?.from;
      navigate(requested?.startsWith('/') && !requested.startsWith('//') ? requested : '/courses', { replace: true });
    } catch (reason) {
      if (reason instanceof ApiError) setFieldErrors(reason.fieldErrors);
      setError((reason as Error).message);
    } finally { setSubmitting(false); }
  };

  return <form className="form-page narrow auth-panel" onSubmit={submit} noValidate>
    <div className="tabs" role="tablist">
      <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setError(''); }}>登录</button>
      <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => { setMode('register'); setError(''); }}>学生注册</button>
    </div>
    <h1>{mode === 'login' ? '登录校园问答平台' : '创建学生账号'}</h1>
    <Field label="学号 / 工号" error={fieldErrors.username}><input autoComplete="username" value={form.username} onChange={set('username')} /></Field>
    <Field label="密码" error={fieldErrors.password}><input type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={form.password} onChange={set('password')} /></Field>
    {mode === 'register' && <>
      <Field label="确认密码" error={fieldErrors.confirmPassword}><input type="password" autoComplete="new-password" value={form.confirmPassword} onChange={set('confirmPassword')} /></Field>
      <Field label="昵称" error={fieldErrors.displayName}><input value={form.displayName} onChange={set('displayName')} /></Field>
      <Field label="校园邮箱" error={fieldErrors.email}><input type="email" autoComplete="email" value={form.email} onChange={set('email')} /></Field>
      <Field label="学院" error={fieldErrors.college}><input value={form.college} onChange={set('college')} /></Field>
      <Field label="班级" error={fieldErrors.className}><input value={form.className} onChange={set('className')} /></Field>
    </>}
    {error && <p className="error" role="alert">{error}</p>}
    <button className="primary" disabled={submitting}>{submitting ? '正在提交…' : mode === 'login' ? '登录' : '注册并登录'}</button>
  </form>;
}

function Field({ label, error, children }: { label: string; error?: string; children: JSX.Element }) {
  return <label>{label}{children}{error && <span className="field-error">{error}</span>}</label>;
}
