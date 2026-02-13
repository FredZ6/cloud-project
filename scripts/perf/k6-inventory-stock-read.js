import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.INVENTORY_BASE_URL || 'http://localhost:8082';
const skuId = __ENV.SKU_ID || 'SKU-PERF-001';

export const options = {
  scenarios: {
    stock_read_traffic: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 60 },
        { duration: '30s', target: 0 }
      ]
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<250']
  }
};

export function setup() {
  const payload = JSON.stringify({ skuId: skuId, availableQty: 5000 });
  const params = { headers: { 'Content-Type': 'application/json' } };
  const res = http.post(`${baseUrl}/api/stocks`, payload, params);
  check(res, {
    'seed stock success': (r) => r.status === 200
  });
}

export default function () {
  const res = http.get(`${baseUrl}/api/stocks/${skuId}`);
  check(res, {
    'stock read success': (r) => r.status === 200,
    'stock has availableQty': (r) => r.json('availableQty') !== null
  });
  sleep(0.1);
}
