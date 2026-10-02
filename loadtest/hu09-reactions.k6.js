// K6 Load Test — HU09 Reactions System
// Run: k6 run loadtest/hu09-reactions.k6.js
// Env:
//   API_BASE_URL (default http://localhost:8080)
//   JWT_TOKEN (required, user JWT for authenticated requests)
//   POST_ID (optional, target a specific post; defaults to first from feed)
//
// Thresholds enforced:
//   http_req_duration{name:react}: p(95)<80, p(99)<250
//   http_req_failed: rate<0.0005

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const reactDuration = new Trend('reaction_duration_custom', true);

export const options = {
  stages: [
    { duration: '20s', target: 1000 },   // ramp-up to 1k
    { duration: '20s', target: 3000 },   // ramp to 3k
    { duration: '40s', target: 5000 },   // peak 5k
    { duration: '20s', target: 0 },      // ramp-down
  ],
  thresholds: {
    'http_req_duration{name:react}': ['p(95)<80', 'p(99)<250'],
    'http_req_failed': ['rate<0.0005'],
    'errors': ['rate<0.001'],
  },
  // Tag groups so duration thresholds only apply to react endpoint
  tags: { testid: 'hu09-reactions' },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.JWT_TOKEN;

if (!TOKEN) {
  throw new Error('JWT_TOKEN env var is required');
}

const headers = {
  'Authorization': `Bearer ${TOKEN}`,
  'Content-Type': 'application/json',
};

const REACTION_TYPES = ['LIKE', 'LOVE', 'CELEBRATE'];

export default function () {
  // 80% feed reads, 20% reactions (per plan §9.3)
  if (Math.random() < 0.8) {
    group('feed-read', () => {
      const res = http.get(`${BASE_URL}/api/feed?page=1&pageSize=20`, {
        headers,
        tags: { name: 'feed' },
      });
      check(res, {
        'feed status 200': (r) => r.status === 200,
      }) || errorRate.add(1);
    });
  } else {
    group('react', () => {
      // Pick a random reaction type
      const type = REACTION_TYPES[Math.floor(Math.random() * REACTION_TYPES.length)];

      // Pick a target post — in real test, fetch from /api/feed first.
      // For simplicity, use a postId from env or a known seeded post.
      const postId = __ENV.POST_ID || 'pst_seed_1';

      const payload = JSON.stringify({ type });
      const start = Date.now();
      const res = http.post(
        `${BASE_URL}/api/posts/${postId}/react`,
        payload,
        {
          headers,
          tags: { name: 'react' },
        },
      );
      const duration = Date.now() - start;
      reactDuration.add(duration);

      const ok = check(res, {
        'react status 200': (r) => r.status === 200,
        'react has status field': (r) => {
          try {
            return r.json('status') !== undefined;
          } catch {
            return false;
          }
        },
        'react has totalReactions field': (r) => {
          try {
            return typeof r.json('totalReactions') === 'number';
          } catch {
            return false;
          }
        },
      });
      if (!ok) errorRate.add(1);
    });
  }

  sleep(0.1 + Math.random() * 0.2); // jitter 100-300 ms
}
