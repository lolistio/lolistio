<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [local ratelimit](#local-ratelimit)
  - [针对service](#%E9%92%88%E5%AF%B9service)
  - [针对pod](#%E9%92%88%E5%AF%B9pod)
  - [针对特定route](#%E9%92%88%E5%AF%B9%E7%89%B9%E5%AE%9Aroute)
  - [针对url路径/方法/源IP](#%E9%92%88%E5%AF%B9url%E8%B7%AF%E5%BE%84%E6%96%B9%E6%B3%95%E6%BA%90ip)
- [global ratelimit](#global-ratelimit)
  - [部署限流器](#%E9%83%A8%E7%BD%B2%E9%99%90%E6%B5%81%E5%99%A8)
  - [配置gateway限流规则](#%E9%85%8D%E7%BD%AEgateway%E9%99%90%E6%B5%81%E8%A7%84%E5%88%99)
  - [部署服务](#%E9%83%A8%E7%BD%B2%E6%9C%8D%E5%8A%A1)
  - [部署gateway](#%E9%83%A8%E7%BD%B2gateway)
  - [部署virtual service](#%E9%83%A8%E7%BD%B2virtual-service)
  - [验证ratelimit生效](#%E9%AA%8C%E8%AF%81ratelimit%E7%94%9F%E6%95%88)
  - [验证global ratelimit生效](#%E9%AA%8C%E8%AF%81global-ratelimit%E7%94%9F%E6%95%88)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->



# local ratelimit

local ratelimit是只在Pod或者vm本地生效的，通过envoy的 `envoy.extensions.filters.http.local_ratelimit.v3.LocalRateLimit` 实现。

## 针对service

在开启了istio的namespace中，创建Deployment nginx、Service nginx。

```bash
$ kubectl create deployment nginx --image nginx
$ kubectl expose deployment nginx --port 80
```

此时，从其他Pod中请求 service nginx ，可以一直请求成功，返回nginx的默认页面。

开启local ratelimit。具体规则见 [filter-local-ratelimit-svc-1.yaml](filter-local-ratelimit-svc-1.yaml)

```
$ kubectl apply -f filter-local-ratelimit-svc-1.yaml
```

该envoy filter会通过`workloadSelector`作用到符合label的workload上，包括容器Pod和虚拟机的workload entry。在本例中是以容器Pod为示例的。

```yaml
      patch:
        operation: INSERT_BEFORE
        value:
          name: envoy.filters.http.local_ratelimit
          typed_config:
            "@type": type.googleapis.com/udpa.type.v1.TypedStruct
            type_url: type.googleapis.com/envoy.extensions.filters.http.local_ratelimit.v3.LocalRateLimit
            value:
              stat_prefix: http_local_rate_limiter
              token_bucket:
                max_tokens: 1
                tokens_per_fill: 1
                fill_interval: 60s
```

如上，envoy filter会patch local_ratelimit，限制为一分钟1个请求。

此时，从其他Pod中请求 service nginx，可以发现，一分钟内，只有第一个请求成功，后续的请求都会返回 `HTTP/1.1 429 Too Many Requests` 。

```bash
$ curl -i nginx.hellobaby
HTTP/1.1 429 Too Many Requests
x-local-rate-limit: true
content-length: 18
content-type: text/plain
date: Wed, 16 Jun 2021 03:52:15 GMT
server: envoy
x-envoy-upstream-service-time: 0
```

## 针对pod

上面是通过service nginx来访问服务，接下来通过pod的ip来访问服务。

```yaml
$ kubectl scale deployment --replicas=2 nginx
```

此时可以验证，通过单个pod ip访问服务时，两个服务之间的限流是独立的，pod 1限流后，pod 2仍然可以访问，方法是先向pod 1发送请求达到限流，然后立即向pod 2发送请求。

## 针对特定route

前面2个例子是针对所有的vhosts/routes。envoy filter也可以针对特定的route设置限流规则。

由于限流是在服务提供方（Provider）的入方向进行配置，因此其match规则为：

```yaml
    - applyTo: HTTP_ROUTE
      match:
        context: SIDECAR_INBOUND
        routeConfiguration:
          vhost:
            name: "inbound|http|80"
            route:
              action: ANY
```

其中，name为istio proxy 的 inbound route 名称。

```bash
$ istioctl pc route nginx-f89759699-khbdr --name "inbound|80||"
inbound|80||                                                  *                                        /*
inbound|80||                                                  *                                        /*
$ istioctl pc cluster nginx-f89759699-khbdr  --direction=inbound
SERVICE FQDN     PORT     SUBSET     DIRECTION     TYPE             DESTINATION RULE
                 80       -          inbound       ORIGINAL_DST
$ istioctl pc route nginx-f89759699-5zv98 --name "inbound|80||" -o yaml
```

具体查看其route表项如下。其中route中的 `cluster: inbound|80||` 为inbound方向的cluster。不过这里很奇怪，为什么有2条一模一样的route记录呢？

```yaml
- name: inbound|80||
  validateClusters: false
  virtualHosts:
  - domains:
    - '*'
    name: inbound|http|80
    routes:
    - decorator:
        operation: nginx.hellobaby.svc.cluster.local:80/*
      match:
        prefix: /
      name: default
      route:
        cluster: inbound|80||
        maxStreamDuration:
          grpcTimeoutHeaderMax: 0s
          maxStreamDuration: 0s
        timeout: 0s
...
```

具体envoy filter配置见 [filter-local-ratelimit-svc-2.yaml](filter-local-ratelimit-svc-2.yaml)。

上述配置生效后，查看pod的名为 "inbound|80||" 的proxy-config route nginx-f89759699-khbdr 规则，可以看到增加了 `typedPerFilterConfig` 部分，这是我们配置的envoy filter中的patch部分。可以看到已经生效了。此时从其他pod中去请求80端口的服务，可以观察到限流生效。

```yaml
- name: inbound|80||
  validateClusters: false
  virtualHosts:
  - domains:
    - '*'
    name: inbound|http|80
    routes:
    - decorator:
        operation: nginx.hellobaby.svc.cluster.local:80/*
      match:
        prefix: /
      name: default
      route:
        cluster: inbound|80||
        maxStreamDuration:
          grpcTimeoutHeaderMax: 0s
          maxStreamDuration: 0s
        timeout: 0s
      typedPerFilterConfig:
        envoy.filters.http.local_ratelimit:
          '@type': type.googleapis.com/udpa.type.v1.TypedStruct
          typeUrl: type.googleapis.com/envoy.extensions.filters.http.local_ratelimit.v3.LocalRateLimit
          value:
            filter_enabled:
              default_value:
                numerator: 100
              runtime_key: local_rate_limit_enabled
            filter_enforced:
              default_value:
                numerator: 100
              runtime_key: local_rate_limit_enforced
            response_headers_to_add:
            - append: false
              header:
                key: x-local-rate-limit
                value: "true"
            stat_prefix: http_local_rate_limiter
            token_bucket:
              fill_interval: 60s
              max_tokens: 1
              tokens_per_fill: 1
...
```

## 针对url路径/方法/源IP

envoy filter可以通过descriptor，设置针对url路径、请求方法、源IP的限流策略。

具体参考 [path](descriptor/path.yaml)、[method](descriptor/method.yaml)、[remote-address](descriptor/remote_address.yaml)。

Ref1: [config-route-v3-ratelimit-action](https://www.envoyproxy.io/docs/envoy/latest/api-v3/config/route/v3/route_components.proto#config-route-v3-ratelimit-action)
Ref2: [Using rate limit descriptors for local rate limiting](https://www.envoyproxy.io/docs/envoy/latest/configuration/http/http_filters/local_rate_limit_filter#using-rate-limit-descriptors-for-local-rate-limiting)

# global ratelimit

global ratelimit的特点是，其限流器是一个公共的gRPC服务，使用同一个限流器的pods，都受这个限流器的限制，并且互相之间是共享一个限流QPS的。

下面通过对gateway配置global限流器，来演示global ratelimit的应用。

## 部署限流器

首先需要部署一个限流服务。这里使用的是istio示例里指定的[ratelimit](https://github.com/envoyproxy/ratelimit)。

相关配置文件为 [ratelimit-config.yaml](global/ratelimit-config.yaml) [ratelimit.yaml](global/ratelimit.yaml)。

服务部署到 namespace istio-system ，部署后会有一个service ratelimit ，其gRPC端口为8081，这个即为请求的限流API。

## 配置gateway限流规则

相关配置文件为 [filter-ratelimit.yaml](global/filter-ratelimit.yaml) 、 [filter-ratelimit-svc.yaml](global/filter-ratelimit-svc.yaml)。

注意 [filter-ratelimit-svc.yaml](global/filter-ratelimit-svc.yaml) 的配置与官网不同，目前(1.10版本)的文档是有问题的，其指定的vhost.name使用了通配符，实际上是不支持的。上面的示例中删除了这部分，这样可以实现vhost.name通配。

也可以将其改为 `httpbin.example.com:80`，但这样就只为host为 `httpbin.example.com` 的请求生效了。

```yaml
      match:
        context: GATEWAY
        routeConfiguration:
          vhost:
            name: "*:80"
            route:
              action: ANY
```

## 部署服务

使用httpbin作为测试服务，配置文件为 [httpbin.yaml](global/httpbin.yaml)。

## 部署gateway

配置文件为 [gateways.yaml](global/gateways.yaml)。设置其hosts为 `httpbin.example.com`。

## 部署virtual service

配置文件为 [virtualservices.yaml](global/virtualservices.yaml)。设置其hosts为 httpbin.example.com，关联gateway httpbin-gateway。

## 验证ratelimit生效

客户端请求 `curl -i -H "Host: httpbin.example.com" http://{ingress gateway external ip}/status/200`，可以观察到，一分钟内，第一个请求返回的是200，后续的请求均返回 `429 Too Many Requests`。

这样可以验证global ratelimit已经生效，但无法验证其是global的。

## 验证global ratelimit生效

将ingress gateway扩容为2副本。


1. 客户端请求 `curl -i -H "Host: httpbin.example.com" http://{ingress gateway pod 1 ip}/status/200`，可以观察到，第一个请求返回的是200，后续的请求均返回 `429 Too Many Requests`。;
2. 在一分钟内，客户端请求 `curl -i -H "Host: httpbin.example.com" http://{ingress gateway pod 2 ip}/status/200`，可以观察到，请求均返回 `429 Too Many Requests`。

这个现象与local ratelimit是不一样的，验证了global属性。

```yaml
[root@debian-77dc9c5f4f-nscvz /]# curl -i -H "Host: httpbin.example.com" 10.244.2.104:8080/status/200
HTTP/1.1 200 OK
server: envoy
date: Wed, 16 Jun 2021 11:49:10 GMT
content-type: text/html; charset=utf-8
access-control-allow-origin: *
access-control-allow-credentials: true
content-length: 0
x-envoy-upstream-service-time: 5

[root@debian-77dc9c5f4f-nscvz /]# curl -i -H "Host: httpbin.example.com" 10.244.6.71:8080/status/200
HTTP/1.1 429 Too Many Requests
x-envoy-ratelimited: true
date: Wed, 16 Jun 2021 11:49:13 GMT
server: envoy
content-length: 0
x-envoy-upstream-service-time: 2
```

Ref：

- [Enabling Rate Limits using Envoy](https://istio.io/latest/docs/tasks/policy-enforcement/rate-limit/)
- [issues 32381](https://github.com/istio/istio/issues/32381)
