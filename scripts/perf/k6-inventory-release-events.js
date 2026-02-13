import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.INVENTORY_BASE_URL || 'http://localhost:8082';
const pageSize = parseInt(__ENV.PAGE_SIZE || '20', 10);
const deepPage = parseInt(__ENV.DEEP_PAGE || '500', 10);

function assertOk(res) {
  check(res, {
    'status is 200': (r) => r.status === 200
  });
}

export const options = {
  scenarios: {
    offset_shallow: {
      executor: 'ramping-vus',
      exec: 'offsetShallow',
      startVUs: 1,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 30 },
        { duration: '20s', target: 0 }
      ]
    },
    offset_deep: {
      executor: 'ramping-vus',
      exec: 'offsetDeep',
      startVUs: 1,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 30 },
        { duration: '20s', target: 0 }
      ]
    },
    cursor_paging: {
      executor: 'ramping-vus',
      exec: 'cursorPaging',
      startVUs: 1,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 30 },
        { duration: '20s', target: 0 }
      ]
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<250']
  }
};

export function offsetShallow() {
  const res = http.get(
    `${baseUrl}/api/stocks/release-events?page=0&size=${pageSize}`,
    { tags: { name: 'release_events_offset_page0' } }
  );
  assertOk(res);
  check(res, {
    'has items array': (r) => Array.isArray(r.json('items'))
  });
  sleep(0.1);
}

export function offsetDeep() {
  const res = http.get(
    `${baseUrl}/api/stocks/release-events?page=${deepPage}&size=${pageSize}`,
    { tags: { name: 'release_events_offset_deep' } }
  );
  assertOk(res);
  check(res, {
    'has items array': (r) => Array.isArray(r.json('items'))
  });
  sleep(0.1);
}

let cursorAfter = null;

export function cursorPaging() {
  let url = `${baseUrl}/api/stocks/release-events/cursor?size=${pageSize}`;
  if (cursorAfter) {
    url += `&after=${encodeURIComponent(cursorAfter)}`;
  }

  const res = http.get(url, { tags: { name: 'release_events_cursor' } });
  assertOk(res);

  const hasMore = res.json('hasMore');
  const nextCursor = res.json('nextCursor');

  check(res, {
    'has items array': (r) => Array.isArray(r.json('items')),
    'nextCursor present when hasMore': () => (hasMore ? nextCursor !== null : true)
  });

  cursorAfter = hasMore && nextCursor ? nextCursor : null;
  sleep(0.1);
}

