# 健康监测
Kubernetes支持command、tcp、http三种健康监测的机制。当应用mesh化后，其中command、tcp的健康监测不受影响，但是http**可能**会受影响。

当用户为服务开启TLS后，由于容器应用的流量被envoy劫持，所以实际对外暴漏的是https服务；但健康监测的http请求是由kubelet发给容器的，其没有istio的certificate信息，仍然是以http协议发起，因此健康监测总是失败。

针对这种情况，istio 会修改 pod spec ，将app container的liveness换掉，并由istio proxy监听新的liveness端口及http path。其中：

- 默认 istio proxy的liveness监听的端口为 15020 （pilot-agent进程），
- 默认的http path规则为 `/app-health/{app container name}/livez`。istio-proxy可以处理pod有多个app container的情况。

kubelet会按照spec的描述，将请求发给应用的网络空间，实际是发送给pilot-agent；而pilot-agent则读取环境变量 `ISTIO_KUBE_APP_PROBERS`，向app container发起真正的探测，响应用户的请求。

# 验证

## 开启STRICT mtls mode
在namespace中配置PeerAuthentication，mtls mode为STRICT，

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: "default"
spec:
  mtls:
    mode: STRICT
```

## 部署服务

参考 [liveness-http](liveness-http.yaml)。

## 查看pod配置

可以看到：

- app container（liveness-http）的livenessProbe被改为了针对15020端口的`/app-health/liveness-http/livez`路径。
- istio-proxy会注入环境变量 `ISTIO_KUBE_APP_PROBERS` ，其值为app container的原始httpProbe。

```yaml
  - image: docker.io/istio/health:example
    imagePullPolicy: IfNotPresent
    name: liveness-http
    livenessProbe:
      failureThreshold: 3
      httpGet:
        path: /app-health/liveness-http/livez
        port: 15020
        scheme: HTTP
...
  - image: docker.io/istio/proxyv2:1.10.0
    imagePullPolicy: IfNotPresent
    name: istio-proxy
    env:
      - name: ISTIO_KUBE_APP_PROBERS
      value: '{"/app-health/liveness-http/livez":{"httpGet":{"path":"/foo","port":8001,"scheme":"HTTP"},"timeoutSeconds":1}}'
```

## 关闭自动

为pod的spec中设置 `sidecar.istio.io/rewriteAppHTTPProbers: "false"` ，istio webhook会跳过更改probe。（用途？）

# 代码实现

注入部分在 [webhook](https://github.com/istio/istio/blob/f37b1cea5ac8cf01b65eabdd4e25fa4f49375bb0/pkg/kube/inject/webhook.go#L600) 中。

监听的实现在 [pilot-agent](https://github.com/istio/istio/blob/1.10.2/pilot/cmd/pilot-agent/status/server.go#L193)。

# redinessProbe、startupProbe

与livenessProbe类似，只是路径改为了 `/app-health/{app container name}/readyz`、`/app-health/{app container name}/startupz`。

