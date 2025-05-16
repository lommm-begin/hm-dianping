# 黑马点评

* 把redis消息队列换成了rabbitmq，加了spring Security
* html是放在nginx的，所以单独放出来了
---
在使用Security的时候发现前端有点问题，首页有个获取当前用户是否点赞的请求，无论是否登录都会请求，所以一直被拦截，所以加了个判断是否本地的token是否为空，为空就不发送
