<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [namespace级别开关服务网格](#namespace%E7%BA%A7%E5%88%AB%E5%BC%80%E5%85%B3%E6%9C%8D%E5%8A%A1%E7%BD%91%E6%A0%BC)
- [Pod级别开关服务网格](#pod%E7%BA%A7%E5%88%AB%E5%BC%80%E5%85%B3%E6%9C%8D%E5%8A%A1%E7%BD%91%E6%A0%BC)
- [exportTo](#exportto)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->


# namespace级别开关服务网格

kubectl label ns xxx istio-injection=enable --overwrite


关闭服务网格
kubectl label namespace default istio-injection-

全集群所有ns查看开启服务网格的namespace
kubectl get namespace -L istio-injection

# Pod级别开关服务网格

可以通过在Pod的label上设置`sidecar.istio.io/inject`为true/false，在Pod级别进行控制是否开启服务网格。

```yaml
  template:
    metadata:
      labels:
        app: nginx
        sidecar.istio.io/inject: "false"
    spec:
      containers:
      - image: nginx
```

从而针对性的对服务开启或关闭服务网格，即使这个namespace整体开启了服务网格。

```
kubectl get pods
NAME                                          READY   STATUS    RESTARTS   AGE
debian-77dc9c5f4f-q2hb2                       2/2     Running   0          14h
nginx-58df487b65-4n2qc                        1/1     Running   0          4m38s
```


# exportTo

istio的策略（service entry, virtual service 等）支持namespace隔离。默认策略是全服务网格集群内可见，用户可以配置`exportTo`，从而限制其可见范围。

如下，将限制该service entry 仅 namespace istio-demo可见。

```yaml
apiVersion: networking.istio.io/v1beta1
kind: ServiceEntry
metadata:
  name: nginx
  namespace: istio-demo
spec:
  exportTo:
  - .
  hosts:
  - nginx.example.com
  location: MESH_INTERNAL
  ports:
  - name: http
    number: 80
    protocol: HTTP
  resolution: STATIC
  workloadSelector:
    labels:
      app: nginx
```

如果需要其他namespace可见，则需要在exportTo中增加对应的namespace。


Ref：

https://istio.io/latest/docs/setup/additional-setup/sidecar-injection/#controlling-the-injection-policy
