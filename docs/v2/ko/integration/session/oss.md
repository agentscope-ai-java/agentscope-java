---
title: OSS 상태 저장소
---

<Note>

이 페이지는 [분산 스토리지 — OSS](/v2/ko/integration/distributed/oss)로 대체되었습니다. 아래 내용은 참고용으로 남겨둡니다.

</Note>

`agentscope-extensions-oss`는 AgentScope 에이전트 상태를 Alibaba Cloud Object Storage Service(OSS)에 영속화합니다. 대용량 데이터와 Alibaba Cloud 생태계에 적합합니다.

## 의존성 추가

```xml
<dependency>
    <groupId>io.agentscope</groupId>
    <artifactId>agentscope-extensions-oss</artifactId>
    <version>${agentscope.version}</version>
</dependency>
```

## 빠른 시작

```java
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.oss.OssAgentStateStore;

OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

AgentStateStore stateStore = OssAgentStateStore.builder()
    .ossClient(ossClient)
    .bucketName("my-agentscope-bucket")
    .keyPrefix("agentscope/state/")
    .build();

ReActAgent agent = ReActAgent.builder()
    .name("assistant")
    .model(model)
    .stateStore(stateStore)
    .build();
```

## 키 구성

`(userId, sessionId)` 쌍은 OSS 객체 경로에 포장됩니다:

| 유형 | 키 패턴 |
| --- | --- |
| 단일 값 | `{keyPrefix}{userId}/{sessionId}/{stateKey}.json` |
| 리스트 | `{keyPrefix}{userId}/{sessionId}/{stateKey}.list.json` |
| 리스트 해시 | `{keyPrefix}{userId}/{sessionId}/{stateKey}.list.hash` (변경 감지) |

익명 세션(`userId`가 null)은 사용자 세그먼트로 `__anon__`을 사용합니다.

## 빌더 참조

| 메서드 | 참고 |
| --- | --- |
| `ossClient(OSS)` | 필수. Alibaba Cloud OSS 클라이언트 |
| `bucketName(String)` | 필수. OSS 버킷 이름 |
| `keyPrefix(String)` | 기본값 `agentscope/state/` |

## 보안

- 프로덕션에서는 하드코딩된 AK/SK를 피하고 RAM Role + STS 임시 자격 증명을 사용하세요
- 스토리지 비용을 관리하기 위해 버킷 수명 주기 규칙(예: 7일 자동 만료)을 설정하세요
