import { getApps, initializeApp, type FirebaseApp } from 'firebase/app'
import { browserLocalPersistence, getAuth, setPersistence, type Auth } from 'firebase/auth'
import {
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from 'firebase/firestore'

/**
 * Firebase is initialised lazily, on first use, inside browser-only code.
 * Static export prerenders every page in Node at build time; touching
 * IndexedDB-backed persistence or browser auth at module scope would break
 * that prerender. Nothing here runs until a client effect asks for it.
 */

interface FirebaseConfig {
  apiKey: string
  authDomain: string
  projectId: string
  storageBucket: string
  messagingSenderId: string
  appId: string
}

function readConfig(): FirebaseConfig {
  const config = {
    apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY ?? '',
    authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN ?? '',
    projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ?? '',
    storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET ?? '',
    messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID ?? '',
    appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID ?? '',
  }

  if (!config.apiKey || !config.projectId) {
    throw new Error(
      'Firebase is not configured. Copy apps/web/.env.example to apps/web/.env.local, fill in the web app config, then restart the dev server.',
    )
  }

  return config
}

let app: FirebaseApp | undefined
let database: Firestore | undefined
let authentication: Auth | undefined

export function firebaseApp(): FirebaseApp {
  if (!app) {
    app = getApps()[0] ?? initializeApp(readConfig())
  }
  return app
}

export function db(): Firestore {
  if (!database) {
    database = initializeFirestore(firebaseApp(), {
      // Offline persistence is the offline-first layer: IndexedDB cache, and
      // one shared cache across tabs so two open tabs cannot fight.
      localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
    })
  }
  return database
}

export function auth(): Auth {
  if (!authentication) {
    authentication = getAuth(firebaseApp())
    void setPersistence(authentication, browserLocalPersistence)
  }
  return authentication
}
