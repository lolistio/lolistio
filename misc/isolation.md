
# namespace级别开关服务网格

kubectl label ns xxx istio-injection=enable --overwrite

Resource	Label	Enabled value	Disabled value
Namespace	istio-injection	enabled	disabled

关闭服务网格
kubectl label namespace default istio-injection-

全集群所有ns查看开启服务网格的namespace
kubectl get namespace -L istio-injection

# Pod级别开关服务网格

可以通过在Pod的label上设置`sidecar.istio.io/inject`为true/false，在Pod级别进行控制是否开启服务网格。

Resource	Label	Enabled value	Disabled value
Pod	sidecar.istio.io/inject	"true"	"false"

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
  - nginx.jd.com
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
