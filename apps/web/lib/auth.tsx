'use client'

import {
  GoogleAuthProvider,
  createUserWithEmailAndPassword,
  onAuthStateChanged,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  updateProfile,
  type User,
} from 'firebase/auth'
import { doc, getDoc, serverTimestamp, setDoc } from 'firebase/firestore'
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

import { auth, db } from './firebase'
import { cleanupAlarmStates } from './db'
import { DEFAULT_PROFILE } from './types'

interface AuthContextValue {
  user: User | null
  loading: boolean
  error: Error | null
  signOutNow: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue>({
  user: null,
  loading: true,
  error: null,
  signOutNow: async () => {},
})

export function useAuth(): AuthContextValue {
  return useContext(AuthContext)
}

/**
 * Creates the two profile documents on first sign-in. Both writes are
 * idempotent merges, so a retry after a flaky connection is harmless.
 */
async function ensureProfile(user: User): Promise<void> {
  const database = db()

  await setDoc(
    doc(database, 'users', user.uid),
    {
      displayName: user.displayName ?? '',
      email: user.email ?? '',
      photoURL: user.photoURL ?? null,
      updatedAt: serverTimestamp(),
    },
    { merge: true },
  )

  const profileRef = doc(database, 'users', user.uid, 'meta', 'profile')
  const snapshot = await getDoc(profileRef)

  if (!snapshot.exists()) {
    await setDoc(profileRef, {
      ...DEFAULT_PROFILE,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC',
      updatedAt: serverTimestamp(),
    })
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<Error | null>(null)

  useEffect(() => {
    let unsubscribe: (() => void) | undefined

    try {
      unsubscribe = onAuthStateChanged(
        auth(),
        (next) => {
          setUser(next)
          setLoading(false)
          if (next) {
            ensureProfile(next).catch((cause) => {
              console.error('Could not create the profile documents', cause)
            })
            cleanupAlarmStates(next.uid).catch((cause) => {
              console.error('Could not prune old alarm state', cause)
            })
          }
        },
        (cause) => {
          setError(cause)
          setLoading(false)
        },
      )
    } catch (cause) {
      setError(cause instanceof Error ? cause : new Error(String(cause)))
      setLoading(false)
    }

    return () => unsubscribe?.()
  }, [])

  const signOutNow = useCallback(async () => {
    await signOut(auth())
  }, [])

  const value = useMemo(
    () => ({ user, loading, error, signOutNow }),
    [user, loading, error, signOutNow],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/* ------------------------------------------------------------ actions --- */

export async function signInWithGoogle(): Promise<void> {
  const provider = new GoogleAuthProvider()
  provider.setCustomParameters({ prompt: 'select_account' })
  await signInWithPopup(auth(), provider)
}

export async function signInWithEmail(email: string, password: string): Promise<void> {
  await signInWithEmailAndPassword(auth(), email, password)
}

export async function registerWithEmail(
  name: string,
  email: string,
  password: string,
): Promise<void> {
  const credential = await createUserWithEmailAndPassword(auth(), email, password)
  if (name.trim()) {
    await updateProfile(credential.user, { displayName: name.trim() })
  }
}

export async function sendReset(email: string): Promise<void> {
  await sendPasswordResetEmail(auth(), email)
}

export function describeAuthError(cause: unknown): string {
  const code =
    typeof cause === 'object' && cause !== null && 'code' in cause
      ? String((cause as { code: unknown }).code)
      : ''

  switch (code) {
    case 'auth/invalid-credential':
    case 'auth/wrong-password':
    case 'auth/user-not-found':
      return 'That email and password combination is not right.'
    case 'auth/email-already-in-use':
      return 'An account with that email already exists. Sign in instead.'
    case 'auth/weak-password':
      return 'Use a password with at least six characters.'
    case 'auth/invalid-email':
      return 'That email address does not look valid.'
    case 'auth/popup-closed-by-user':
      return 'The Google sign-in window was closed.'
    case 'auth/popup-blocked':
      return 'The browser blocked the sign-in popup. Allow popups and try again.'
    case 'auth/network-request-failed':
      return 'No connection. Try again once you are back online.'
    case 'auth/too-many-requests':
      return 'Too many attempts. Wait a minute and try again.'
    default:
      return cause instanceof Error ? cause.message : 'Something went wrong.'
  }
}
