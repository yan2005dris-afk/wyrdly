import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// Custom performance metrics
const feedDuration = new Trend('feed_response_time', true);
const cursorFeedDuration = new Trend('feed_cursor_response_time', true);
const feedErrors = new Rate('feed_error_rate');

export const options = {
  stages: [
    { duration: '10s', target: 5 },   // Ramp-up inicial
    { duration: '20s', target: 15 },  // Carga sostenida
    { duration: '10s', target: 30 },  // Pico de concurrencia
    { duration: '5s', target: 0 },    // Ramp-down
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],       // Tasa de fallos menor al 2%
    http_req_duration: ['p(95)<350'],     // 95% de peticiones debajo de 350ms
    feed_response_time: ['p(95)<300'],    // Feed principal responde en menos de 300ms
    feed_error_rate: ['rate<0.01'],       // Menos del 1% de errores en feed
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Setup se ejecuta una sola vez para preparar autenticación y datos
export function setup() {
  const timestamp = Date.now().toString().slice(-6);
  const username = `k6_user_${timestamp}`;
  const email = `k6_${timestamp}@wyrdly.social`;
  const password = 'PasswordSeguro123!';

  const registerPayload = JSON.stringify({
    username: username,
    email: email,
    password: password,
    fullName: `K6 Load Tester ${timestamp}`,
    bio: 'Automated performance test runner',
  });

  const headers = { 'Content-Type': 'application/json' };

  // Intentar registro
  const regRes = http.post(`${BASE_URL}/api/auth/register`, registerPayload, { headers });
  let token = null;

  if (regRes.status === 201 || regRes.status === 200) {
    const body = regRes.json();
    token = body.token || (body.data && body.data.token);
  }

  // Si no devolvió token directo en registro o ya existía, hacer login
  if (!token) {
    const loginPayload = JSON.stringify({
      usernameOrEmail: username,
      password: password,
    });
    const loginRes = http.post(`${BASE_URL}/api/auth/login`, loginPayload, { headers });
    if (loginRes.status === 200) {
      const body = loginRes.json();
      token = body.token || (body.data && body.data.token);
    }
  }

  if (!token) {
    console.warn('Advertencia: No se pudo obtener token dinámico; usando sesión pública o mock.');
  }

  return { token: token, baseUrl: BASE_URL };
}

export default function (data) {
  const headers = {
    'Accept': 'application/json',
  };

  if (data.token) {
    headers['Authorization'] = `Bearer ${data.token}`;
  }

  // 1. Consulta del Feed inicial (primera página)
  const feedStartTime = Date.now();
  const feedRes = http.get(`${data.baseUrl}/api/feed?limit=10`, { headers });
  feedDuration.add(Date.now() - feedStartTime);

  const isFeedOk = check(feedRes, {
    'feed status es 200': (r) => r.status === 200,
    'feed contiene lista data': (r) => {
      try {
        const body = r.json();
        return Array.isArray(body.data) || Array.isArray(body);
      } catch (e) {
        return false;
      }
    },
  });

  feedErrors.add(!isFeedOk);

  // 2. Si el feed tiene cursor para la siguiente página, probar cursor pagination
  let nextCursor = null;
  try {
    const body = feedRes.json();
    if (body.meta && body.meta.nextCursor) {
      nextCursor = body.meta.nextCursor;
    }
  } catch (e) {
    // Ignorar si no hay JSON
  }

  if (nextCursor) {
    sleep(0.3); // Pausa realista entre scrolls del usuario
    const cursorStartTime = Date.now();
    const cursorRes = http.get(
      `${data.baseUrl}/api/feed?cursor=${encodeURIComponent(nextCursor)}&limit=10`,
      { headers }
    );
    cursorFeedDuration.add(Date.now() - cursorStartTime);

    check(cursorRes, {
      'cursor feed status es 200': (r) => r.status === 200,
    });
  }

  sleep(1); // Think time entre iteraciones de usuario
}
