

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

# global ratelimit

global ratelimit的特点是，其限流器是一个公共的gRPC服务，使用同一个限流器的pods，都受这个限流器的限制，并且互相之间是共享一个限流QPS的。

## 部署限流器

首先需要部署一个限流服务。这里使用的是istio示例里指定的[ratelimit](https://github.com/envoyproxy/ratelimit)。

把这个服务部署到 namespace istio-system ，部署后会有一个service ratelimit ，其gRPC端口为8081，这个即为请求的限流API。

## 



Ref：

- [Enabling Rate Limits using Envoy](https://istio.io/latest/docs/tasks/policy-enforcement/rate-limit/)
- [](https://github.com/istio/istio/issues/32381)
