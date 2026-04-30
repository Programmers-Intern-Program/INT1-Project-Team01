#!/bin/bash

set -e

BRANCH=${1:-develop}
COMPOSE="docker compose --env-file .env -f docker/docker-compose.prod.yml"
HEALTH_PATH="/api/v1/health"

echo "======================================"
echo "Blue/Green 배포 시작"
echo "배포 브랜치: $BRANCH"
echo "======================================"

# 프로젝트 루트 확인
if [ ! -f ".env" ]; then
  echo ".env 파일이 없습니다. 프로젝트 루트에서 실행해 주세요."
  exit 1
fi

if [ ! -f "nginx/prod.conf" ]; then
  echo "nginx/prod.conf 파일이 없습니다."
  exit 1
fi

# 1. 현재 active 확인
if grep -qE "^[[:space:]]*server backend-blue:8080;" nginx/prod.conf; then
  CURRENT="blue"
  NEXT="green"
else
  CURRENT="green"
  NEXT="blue"
fi

echo "현재 active 컨테이너: backend-$CURRENT"
echo "새로 배포할 컨테이너: backend-$NEXT"

# 2. 최신 코드 반영
echo "최신 코드 pull"
git fetch origin
git checkout "$BRANCH"
git pull origin "$BRANCH"

# 3. 비활성 컨테이너 빌드 및 실행
echo "backend-$NEXT 빌드"
$COMPOSE build backend-$NEXT

echo "backend-$NEXT 실행"
$COMPOSE up -d --no-deps backend-$NEXT

# 4. Docker network 확인
NETWORK=$(docker inspect team01-nginx-prod \
  -f '{{range $name, $conf := .NetworkSettings.Networks}}{{println $name}}{{end}}' | head -n 1)

if [ -z "$NETWORK" ]; then
  echo "Nginx Docker network를 찾지 못했습니다."
  exit 1
fi

echo "Docker network: $NETWORK"

# 5. 새 컨테이너 health check
echo "backend-$NEXT health check 시작"

for i in {1..30}
do
  if docker run --rm --network "$NETWORK" curlimages/curl:8.10.1 \
    -fsS "http://backend-$NEXT:8080$HEALTH_PATH"; then
    echo ""
    echo "backend-$NEXT health check 성공"
    break
  fi

  echo "health check 재시도 중... ($i/30)"
  sleep 2

  if [ "$i" -eq 30 ]; then
    echo "health check 실패. Nginx 전환 없이 배포 중단"
    exit 1
  fi
done

# 6. Nginx upstream 변경
echo "Nginx upstream 변경: backend-$CURRENT → backend-$NEXT"

cp nginx/prod-$NEXT.conf nginx/prod.conf

# 7. Nginx 설정 검증 후 reload
docker exec team01-nginx-prod nginx -t
docker exec team01-nginx-prod nginx -s reload

echo "======================================"
echo "Blue/Green 배포 완료"
echo "현재 active: backend-$NEXT"
echo "======================================"

