지금 이 프로젝트에서 추가 기능을 구현하고 싶어.
경매 시작 시작이 되면 사용자가 어느 페이지에 있든 관계없이 경매의 status가 upcomming -> ongoing으로 변경되면서 Auction Entity의 startPrice를 Redis에 저장하고 싶어. 이러는 이유는 아래와 같아.
1.  사용자 경매 참가 여부와 관계없이 돌아가야 해.
2. 사용자의 경매 초기 입찰 시 Redis에 저장된 Db의 startPrice를 가져와서 이 둘의 가격 중 최고가를 Redis에 반영하기 위함이야. 즉, 초기 입찰 시 비교군이 필요하고 이 비교군을 Redis에서 가져오고 싶어

결과적으로 정리하면 아래와 같아.
DB의 각 경매에 등록된 startPrice 시간이 되면 사용자가 브라우저의 어느 곳에 있든 관계없이 경매 상태가 upcomming이 ongoing으로 변하면서, DB의 startPrice가 redis에 저장되어야 되고, redis에 저장된 key-value에 TTL을 설정해서 경매가 끝나는 시간에 redis에서 삭제되게  할꺼야.

추가로, 현재 내 프로젝트 코드를 보면 입찰 데이터에 대해 TTL을 설정하는 로직이 있는데 이를 참고했으면 해.

아래의 내용은 위의 추가 기능을 구현하기 위해 우리가 생각한 방법이야.
상품 등록 (POST)
- 관리자가 새로운 상품을 등록했을 때 (경매 시작 시간(2025-03-12 18:00)과 종료 시간(2025-03-12 23:00)도 함께 설정)
- Redis 메모리 상에 현재 시간(2025-03-12 09:00) 기준으로, 경매 시작 시간(2025-03-12 18:00)까지 남은 시간을 TTL로 저장
  - TTL이 0이되어 종료되면 경매가 시작이 되었다는 것을 의미
- TTL이 만료되는 순간(경매 시작 시간 도달(2025-03-12 18:00))했을 때 Redis Key Event Listener 사용 
  - Redis의 Key Event Listener를 활용하여 TTL이 만료되는 이벤트를 감지합니다.
- TTL 만료 시, 해당 상품의 상태를 **upcoming(경매 시작 전) → ongoing(경매 진행중)으로 변경 
- ongoing으로 상태가 변경될 때, 현재 시간 기준(2025-03-12 18:00)으로 경매 종료 시간(2025-03-12 23:00)까지 남은 시간을 TTl 설정 
  - 이 값은 경매가 종료되기까지 남은 시간을 나타냄 
- TTL이 만료되면 경매 종료 처리를 의미하므로 (Finished)로 상태 변경을 진행

---

[Question]
다음은 멘토님께 위의 내용을 바탕으로 드릴 수 있는 최적화된 질문입니다.

---

멘토님, 현재 저희 경매 서비스 프로젝트에서 추가 기능 구현을 고민하고 있습니다.

### **구현하고 싶은 기능:**
- 관리자가 상품을 등록할 때 경매 시작 시간과 종료 시간을 함께 설정합니다.
- 설정된 경매 시작 시간(`Upcoming`)이 되면 자동으로 경매 상태가 `Ongoing`으로 변경되고, 동시에 **DB에 저장된 경매 시작 가격(`startPrice`)을 Redis에 저장**하고 싶습니다.
- Redis에 저장된 이 `startPrice`는 **초기 입찰 시 비교군으로 활용**될 예정입니다.
    - 즉, 사용자가 처음 입찰 시, Redis에 저장된 `startPrice`와 사용자의 입찰 가격을 비교해 **더 높은 가격을 Redis에 갱신하는 구조**입니다.
- 또한, 경매가 끝나는 시간에 Redis에서 자동으로 해당 데이터를 삭제하려 합니다. 이를 위해 **TTL을 활용할 계획**입니다.

### **기능 구현을 위한 설계:**
현재 저희가 생각한 방식은 다음과 같습니다.

- 관리자가 상품을 등록하면, Redis에 경매 시작까지 남은 시간을 TTL로 설정합니다.
    - 예: 현재가 오전 9시이고 경매 시작이 저녁 6시면, TTL을 9시간으로 설정
- 입찰 과정에서 경매 진행중이 아닐 때(시간으로 비교) 예외 처리
- 경매가 종료되었을 때(TTL이 만료되었을 때인데 어떻게 알죠?)
    - TTL : [(관리자)가 경매를 등록한 시간~경매 종료시간] + [경매 리스트에 경매 종료 상태의 경매를 얼마나 띄워줄껀가?]
      - TTL이 끝나기 전에, Winnder Table에 (UserUuid, 낙찰가, 낙찰 시간, 경매 고유ID)를 저장 
      -  TTL : [(관리자)가 경매를 등록한 시간~경매 종료시간] -> 0인지 아닌지 체크하는 명령어
      - 프론트 단에서 타이머 기능으로 경매가 종료되었을 때, 서버한테 알려준다?(될지 안 될 지 잘 모르겠는데 그렇게 하죠~!~!~!~!~!)
      
      - TTL 그대로 유지하되 경매 종료시간 + 여유 시간을 둘거에요.
      - 원래 기존 TTL + 여유 시간(30분)
      - 스케쥴링(AuctionScheduler) 1분마다 체크 : 레디스를 통해 통해 불러온 시간(TTL) - 여유시간 < 0
      - 만료된거네?, winner 테이블에 저장

### **현재 상태 및 질문의 요점:**
- 이미 구현된 로직 중 **입찰 데이터에 대해 TTL을 설정하는 코드가 있어 참고할 수 있습니다.**
- 다만, 이런 구조(특히 Redis의 Key Event Listener 활용)가 실무적으로 적합한 방법인지 궁금합니다.
- 이 방식의 **성능적인 이슈 또는 잠재적인 문제점**이 있을지 조언을 듣고 싶습니다.
- 더불어, 만약 위 방식이 아니라면, 이와 같은 **상태 변경과 Redis 초기값 저장을 위한 더 좋은 대안이나 베스트 프랙티스**가 있다면 알려주셨으면 합니다.

멘토님의 의견을 듣고, 더 나은 방향으로 기능을 구현하고 싶습니다.  
어떻게 접근하면 좋을까요?
