/* Slot service worker.
 *
 * Scope: cache the app shell so a reload while offline still boots, and cache
 * Next's hashed static assets after first use. It deliberately does not touch
 * cross-origin requests, so Firestore's own IndexedDB persistence stays in
 * charge of data. There is no push handler: v1 has no server to send push.
 */
const CACHE = 'slot-shell-v1'

const SHELL = [
  '/',
  '/timetable/',
  '/tasks/',
  '/subjects/',
  '/focus/',
  '/settings/',
  '/login/',
  '/manifest.webmanifest',
  '/icon.svg',
  '/offline.html',
]

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      .then((cache) =>
        Promise.all(
          SHELL.map(async (url) => {
            try {
              await cache.add(url)
            } catch (error) {
              // One missing route must not abort the whole install.
              console.warn('SW: could not precache', url, error)
            }
          }),
        ),
      )
      .then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') return

  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone()
          caches.open(CACHE).then((cache) => cache.put(request, copy))
          return response
        })
        .catch(async () => {
          const cached = await caches.match(request)
          if (cached) return cached
          const root = await caches.match('/')
          if (root) return root
          return (await caches.match('/offline.html')) || Response.error()
        }),
    )
    return
  }

  if (url.pathname.startsWith('/_next/static/') || url.pathname === '/icon.svg') {
    event.respondWith(
      caches.match(request).then(
        (hit) =>
          hit ||
          fetch(request).then((response) => {
            const copy = response.clone()
            caches.open(CACHE).then((cache) => cache.put(request, copy))
            return response
          }),
      ),
    )
  }
})
