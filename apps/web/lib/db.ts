'use client'

import {
  addDoc,
  collection,
  deleteDoc,
  doc,
  getDocs,
  onSnapshot,
  serverTimestamp,
  setDoc,
  updateDoc,
  type DocumentData,
} from 'firebase/firestore'
import { useEffect, useState } from 'react'

import { db } from './firebase'

export function userPath(uid: string, suffix?: string): string {
  return suffix ? `users/${uid}/${suffix}` : `users/${uid}`
}

function toItems<T>(docs: { id: string; data: () => DocumentData }[]): T[] {
  return docs.map((entry) => ({ id: entry.id, ...entry.data() }) as T)
}

export interface LiveList<T> {
  items: T[]
  loading: boolean
  error: Error | null
}

/**
 * One snapshot listener on a whole subcollection. Data is small per user, and
 * a full-collection listener behaves correctly offline, whereas a filtered
 * query would surface a partial cache the moment the network drops.
 */
export function useCollection<T extends { id: string }>(path: string | null): LiveList<T> {
  const [state, setState] = useState<LiveList<T>>({ items: [], loading: true, error: null })

  useEffect(() => {
    if (!path) {
      setState({ items: [], loading: false, error: null })
      return
    }

    return onSnapshot(
      collection(db(), path),
      (snapshot) => {
        setState({ items: toItems<T>(snapshot.docs), loading: false, error: null })
      },
      (error) => {
        setState({ items: [], loading: false, error })
      },
    )
  }, [path])

  return state
}

export function useDoc<T extends { id: string }>(path: string | null): {
  item: T | null
  loading: boolean
  error: Error | null
} {
  const [state, setState] = useState<{ item: T | null; loading: boolean; error: Error | null }>({
    item: null,
    loading: true,
    error: null,
  })

  useEffect(() => {
    if (!path) {
      setState({ item: null, loading: false, error: null })
      return
    }

    return onSnapshot(
      doc(db(), path),
      (snapshot) => {
        setState({
          item: snapshot.exists() ? ({ id: snapshot.id, ...snapshot.data() } as T) : null,
          loading: false,
          error: null,
        })
      },
      (error) => setState({ item: null, loading: false, error }),
    )
  }, [path])

  return state
}

/** Re-render on a timer; used for "now" markers and in-page reminders. */
export function useNow(intervalMs = 30_000): Date {
  const [now, setNow] = useState(() => new Date())

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), intervalMs)
    return () => clearInterval(timer)
  }, [intervalMs])

  return now
}

export function createDoc(path: string, values: Record<string, unknown>) {
  return addDoc(collection(db(), path), {
    ...values,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  })
}

export function patchDoc(path: string, id: string, values: Record<string, unknown>) {
  return updateDoc(doc(db(), path, id), { ...values, updatedAt: serverTimestamp() })
}

export function removeDoc(path: string, id: string) {
  return deleteDoc(doc(db(), path, id))
}

export function writeDoc(path: string, values: Record<string, unknown>) {
  return setDoc(doc(db(), path), { ...values, updatedAt: serverTimestamp() }, { merge: true })
}

export async function fetchAll<T>(path: string): Promise<T[]> {
  const snapshot = await getDocs(collection(db(), path))
  return toItems<T>(snapshot.docs)
}
