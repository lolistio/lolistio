<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [注入延时](#%E6%B3%A8%E5%85%A5%E5%BB%B6%E6%97%B6)
- [注入故障](#%E6%B3%A8%E5%85%A5%E6%95%85%E9%9A%9C)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->



# 注入延时


```yaml
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: ratings
spec:
  hosts:
  - nginx.example.com
  http:
  - match:
    - headers:
        end-user:
          exact: jason
    fault:
      delay:
        percentage:
          value: 100.0
        fixedDelay: 7s
    route:
    - destination:
        host: nginx.example.com
        subset: vm
  - route:
    - destination:
        host: nginx.example.com
        subset: docker
```

从服务网格上的vm来发起请求，可以看到会有7秒的延迟。

```bash
$ time curl -H "end-user: jason" nginx.example.com
Welcome to nginx on vm204!

real	0m7.015s
user	0m0.000s
sys	0m0.007s
```

查看某个Pod的route配置，可以看到会生成2条route记录，详细的信息可以查看[ratings.json](ratings.json)。

```bash
$ istioctl proxy-config route debian-77dc9c5f4f-qhtnh  --name 80
NAME     DOMAINS                               MATCH     VIRTUAL SERVICE
80       nginx.example.com                          /*        ratings.istio-demo
80       nginx.example.com                          /*        ratings.istio-demo
```




# 注入故障


```yaml
kind: VirtualService
metadata:
  name: ratings
spec:
  hosts:
  - nginx.example.com
  http:
  - match:
    - headers:
        end-user:
          exact: jason
    fault:
      abort:
        httpStatus: 500
        percentage:
          value: 100
    route:
    - destination:
        host: nginx.example.com
        subset: vm
  - route:
    - destination:
        host: nginx.example.com
        subset: docker
```

从服务网格上的vm来发起请求，可以看到会返回500。

```bash
$ curl -i -H "end-user: jason" nginx.example.com
HTTP/1.1 500 Internal Server Error
content-length: 18
content-type: text/plain
date: Mon, 31 May 2021 11:57:46 GMT
server: envoy

fault filter abort
```
