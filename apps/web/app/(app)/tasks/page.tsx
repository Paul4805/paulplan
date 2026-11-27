'use client'

import { Timestamp } from 'firebase/firestore'
import { useMemo, useState } from 'react'

import { TaskDialog } from '@/components/task-dialog'
import {
  Badge,
  Button,
  EmptyState,
  ErrorNote,
  PageHeader,
  Segmented,
  Select,
  Skeleton,
  cn,
} from '@/components/ui'
import { useAuth } from '@/lib/auth'
import { patchDoc, useCollection, userPath } from '@/lib/db'
import { tagDot } from '@/lib/tag'
import { formatDue } from '@/lib/time'
import { useToast } from '@/lib/toast'
import type { Subject, Task, TaskStatus } from '@/lib/types'

const COLUMNS: Array<{ status: TaskStatus; label: string }> = [
  { status: 'todo', label: 'To do' },
  { status: 'doing', label: 'Doing' },
  { status: 'done', label: 'Done' },
]

function sortByRelevance(tasks: Task[]): Task[] {
  return [...tasks].sort((a, b) => {
    const aDue = a.dueAt?.toMillis() ?? Number.POSITIVE_INFINITY
    const bDue = b.dueAt?.toMillis() ?? Number.POSITIVE_INFINITY
    if (aDue !== bDue) return aDue - bDue
    return (a.order ?? 0) - (b.order ?? 0)
  })
}

export default function TasksPage() {
  const { user } = useAuth()
  const toast = useToast()
  const tasks = useCollection<Task>(user ? userPath(user.uid, 'tasks') : null)
  const subjects = useCollection<Subject>(user ? userPath(user.uid, 'subjects') : null)

  const [view, setView] = useState<'list' | 'board'>('list')
  const [subjectFilter, setSubjectFilter] = useState('')
  const [dialog, setDialog] = useState<{ open: boolean; task: Task | null; status: TaskStatus }>({
    open: false,
    task: null,
    status: 'todo',
  })
  const [dragId, setDragId] = useState<string | null>(null)

  const subjectById = useMemo(
    () => new Map(subjects.items.map((subject) => [subject.id, subject])),
    [subjects.items],
  )

  const visible = useMemo(
    () => tasks.items.filter((task) => !subjectFilter || task.subjectId === subjectFilter),
    [tasks.items, subjectFilter],
  )

  const grouped = useMemo(() => {
    const map: Record<TaskStatus, Task[]> = { todo: [], doing: [], done: [] }
    for (const task of visible) map[task.status].push(task)
    for (const status of ['todo', 'doing', 'done'] as TaskStatus[]) {
      map[status] = sortByRelevance(map[status])
    }
    return map
  }, [visible])

  if (!user) return null

  const path = userPath(user.uid, 'tasks')
  const openCount = visible.filter((task) => task.status !== 'done').length

  async function move(task: Task, status: TaskStatus) {
    if (task.status === status) return
    const target = tasks.items.filter((item) => item.status === status)
    const order =
      target.length > 0
        ? Math.max(...target.map((item) => item.order ?? 0)) + 1000
        : 1000

    try {
      await patchDoc(path, task.id, {
        status,
        order,
        completedAt: status === 'done' ? Timestamp.now() : null,
      })
    } catch (cause) {
      console.error(cause)
      toast({ title: 'Could not move the task', tone: 'danger' })
    }
  }

  async function toggleDone(task: Task) {
    await move(task, task.status === 'done' ? 'todo' : 'done')
  }

  function TaskCard({ task }: { task: Task }) {
    const due = formatDue(task.dueAt)
    const subject = task.subjectId ? subjectById.get(task.subjectId) : undefined
    const priorityTone =
      task.priority === 'high' ? 'danger' : task.priority === 'medium' ? 'warning' : 'neutral'

    return (
      <div
        draggable
        onDragStart={() => setDragId(task.id)}
        onDragEnd={() => setDragId(null)}
        className={cn(
          'group rounded-card border border-border bg-surface p-3 transition-shadow duration-fast',
          dragId === task.id ? 'opacity-60' : 'hover:shadow-popover',
        )}
      >
        <div className="flex items-start gap-2">
          <button
            type="button"
            onClick={() => toggleDone(task)}
            aria-label={task.status === 'done' ? 'Mark as not done' : 'Mark as done'}
            className={cn(
              'mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-[4px] border transition-colors',
              task.status === 'done'
                ? 'border-accent-solid bg-accent-solid text-accent-fg'
                : 'border-border-strong text-transparent hover:border-accent',
            )}
          >
            <svg viewBox="0 0 24 24" className="size-3" fill="none" stroke="currentColor" strokeWidth="3">
              <path d="M5 13l4 4L19 7" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>

          <button
            type="button"
            className="min-w-0 flex-1 text-left"
            onClick={() => setDialog({ open: true, task, status: task.status })}
          >
            <p
              className={cn(
                'text-sm font-medium leading-snug',
                task.status === 'done' ? 'text-ink-muted line-through' : 'text-ink',
              )}
            >
              {task.title}
            </p>
            {task.notes ? (
              <p className="mt-0.5 line-clamp-2 text-xs leading-relaxed text-ink-muted">
                {task.notes}
              </p>
            ) : null}
            <div className="mt-2 flex flex-wrap items-center gap-1.5">
              {subject ? (
                <span className="inline-flex min-w-0 items-center gap-1.5 text-xs text-ink-muted">
                  <span aria-hidden className="size-1.5 shrink-0 rounded-pill" style={tagDot(subject.color)} />
                  <span className="max-w-[10rem] truncate">{subject.name}</span>
                </span>
              ) : null}
              {due ? (
                <Badge
                  tone={
                    due.tone === 'danger' ? 'danger' : due.tone === 'warning' ? 'warning' : 'neutral'
                  }
                >
                  {due.label}
                </Badge>
              ) : null}
              {task.priority !== 'low' ? (
                <Badge tone={priorityTone}>{task.priority}</Badge>
              ) : null}
            </div>
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        title="Tasks"
        description={`${openCount} open ${openCount === 1 ? 'task' : 'tasks'}${subjectFilter ? ' for this subject' : ''}.`}
        actions={
          <Button
            size="sm"
            onClick={() => setDialog({ open: true, task: null, status: 'todo' })}
          >
            New task
          </Button>
        }
      />

      {tasks.error ? (
        <div className="mb-4">
          <ErrorNote error={tasks.error} what="your tasks" />
        </div>
      ) : null}

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Select
            aria-label="Filter by subject"
            value={subjectFilter}
            onChange={(event) => setSubjectFilter(event.target.value)}
            className="w-44"
          >
            <option value="">All subjects</option>
            {subjects.items.map((subject) => (
              <option key={subject.id} value={subject.id}>
                {subject.name}
              </option>
            ))}
          </Select>
        </div>
        <Segmented
          label="Task view"
          value={view}
          onChange={setView}
          options={[
            { value: 'list', label: 'List' },
            { value: 'board', label: 'Board' },
          ]}
        />
      </div>

      {tasks.loading ? (
        <div className="space-y-2">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      ) : visible.length === 0 ? (
        <EmptyState
          title={tasks.items.length === 0 ? 'No tasks yet' : 'Nothing matches that filter'}
          description={
            tasks.items.length === 0
              ? 'Deadlines, problem sets, readings — one list, grouped by what is next.'
              : 'Try a different subject, or clear the filter.'
          }
          action={
            <Button
              size="sm"
              onClick={() => setDialog({ open: true, task: null, status: 'todo' })}
            >
              New task
            </Button>
          }
        />
      ) : view === 'list' ? (
        <div className="space-y-8">
          {COLUMNS.filter((column) => column.status !== 'done' || grouped.done.length > 0).map(
            (column) => (
              <section key={column.status} aria-labelledby={`list-${column.status}`}>
                <div className="mb-3 flex items-center gap-2">
                  <h2 id={`list-${column.status}`} className="text-sm font-medium text-ink">
                    {column.label}
                  </h2>
                  <span className="text-xs tabular-nums text-ink-muted">
                    {grouped[column.status].length}
                  </span>
                </div>

                {grouped[column.status].length === 0 ? (
                  <p className="rounded-card border border-dashed border-border px-3 py-4 text-sm text-ink-muted">
                    Nothing here.
                  </p>
                ) : (
                  <ul className="space-y-2">
                    {grouped[column.status].map((task) => (
                      <li key={task.id}>
                        <TaskCard task={task} />
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            ),
          )}
        </div>
      ) : (
        <div className="grid gap-3 md:grid-cols-3">
          {COLUMNS.map((column) => (
            <section
              key={column.status}
              aria-labelledby={`board-${column.status}`}
              onDragOver={(event) => event.preventDefault()}
              onDrop={(event) => {
                event.preventDefault()
                const task = tasks.items.find((item) => item.id === dragId)
                setDragId(null)
                if (task) void move(task, column.status)
              }}
              className="flex min-h-40 flex-col gap-2 rounded-card border border-border bg-canvas p-2"
            >
              <div className="flex items-center justify-between px-1 py-1">
                <h2 id={`board-${column.status}`} className="text-sm font-medium text-ink">
                  {column.label}
                  <span className="ml-2 text-xs font-normal tabular-nums text-ink-muted">
                    {grouped[column.status].length}
                  </span>
                </h2>
                <button
                  type="button"
                  aria-label={`Add a task to ${column.label}`}
                  onClick={() => setDialog({ open: true, task: null, status: column.status })}
                  className="rounded-chip p-1 text-ink-muted transition-colors hover:bg-surface hover:text-ink"
                >
                  <svg viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="1.8">
                    <path d="M12 5v14M5 12h14" strokeLinecap="round" />
                  </svg>
                </button>
              </div>

              {grouped[column.status].map((task) => (
                <TaskCard key={task.id} task={task} />
              ))}

              {grouped[column.status].length === 0 ? (
                <p className="px-2 py-6 text-center text-xs text-ink-muted">
                  Drag a card here
                </p>
              ) : null}
            </section>
          ))}
        </div>
      )}

      <TaskDialog
        open={dialog.open}
        onClose={() => setDialog({ open: false, task: null, status: 'todo' })}
        subjects={subjects.items}
        initial={dialog.task}
        defaultStatus={dialog.status}
      />
    </div>
  )
}
