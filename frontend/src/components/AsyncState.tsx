import type { ReactNode } from 'react';

export function LoadingState({ label = '正在加载' }: { label?: string }) {
  return <div className="state" role="status"><span className="spinner" />{label}</div>;
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="state state-error" role="alert">
      <strong>暂时无法显示</strong>
      <span>{message}</span>
      {onRetry && <button onClick={onRetry}>重试</button>}
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="state"><strong>{title}</strong>{children}</div>;
}
