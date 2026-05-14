import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";
const HEALTH_PATH = __ENV.HEALTH_PATH || "/api/v1/health";

export const options = {
  vus: 1,
  duration: "2m",
  thresholds: {
    http_req_failed: ["rate==0"],
    http_req_duration: ["p(95)<1000"],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}${HEALTH_PATH}`, {
    tags: { name: "smoke-health-check" },
    timeout: "5s",
  });

  console.log(`status: ${res.status}`);
  console.log(`url: ${res.url}`);
  console.log(`body: ${res.body?.slice(0, 300)}`);

  check(res, {
    "health endpoint status is 200": (r) => r.status === 200
  });

  sleep(1);
}
