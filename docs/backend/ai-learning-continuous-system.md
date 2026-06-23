# AI Learning Continuous System

이 문서는 이전 실험 단계에서 작성된 장기 구상 문서였다. 현재 런타임 구조와 완전히 일치하지 않으므로 설계 권위본으로 사용하지 않는다.

## 현재 상태

- learning 서비스의 현재 권위본은 [`ai-learning.md`](./ai-learning.md)다.
- 시스템 전체 권위본은 [`../ai-user/README.md`](../ai-user/README.md)다.
- 실제 런타임은 `env/docker-compose.ai-user.yml`과 `ai-user/learning/` 코드가 우선한다.

## 이 문서를 보는 이유

다음 경우에만 참고한다.

- 과거 설계 배경이 왜 이렇게 되었는지 추적할 때
- 장기 학습 전략 아이디어를 재검토할 때
- 코드와 무관한 historical context가 필요할 때

## 현재 구조와 다른 점

- 별도 `docker-compose.ai-learning.yml`은 더 이상 사용하지 않는다.
- learning은 dev/prod 개별 컨테이너가 아니라 shared ai-user 스택의 일부다.
- PostgreSQL+pgvector 전제 문장은 현재 운영 truth가 아니다.

현재 운영 정보는 위 권위본 문서를 따른다.
