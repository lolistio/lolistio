
# consul介绍

CP模型。

# k8s上部署consul



```
apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    app: consul
  name: consul
spec:
  selector:
    matchLabels:
      app: consul
  template:
    metadata:
      labels:
        app: consul
    spec:
      containers:
      - args:
        - agent
        - -enable-script-checks
        - -dev
        - -client
        - 0.0.0.0
        image: consul:1.8.4
        name: consul
```

# 服务注册

## 配置文件方式

通过 `-config-dir` 指定配置目录。

```bash
consul agent -data-dir=/consul/data -config-dir=/consul/config -dev -client 0.0.0.0
```

在目录下创建配置文件。如下，创建一个名为web的service。

```json
{
  "service": {
    "name": "web",
    "tags": [
      "rails"
    ],
    "port": 80
  }
}
```

consul不会自动监测该目录，需要向consul进程发送SIGHUP信号（`kill -HUP {pid}`），才会重新加载。

也可以通过命令行 `consul reload` 重新读取配置。

```
$ consul reload
Configuration reload triggered
```

# 服务发现

## DNS方式

consul启动后，会同步启动一个dns server，端口监听在8600。注册的服务会增加 `.service.consul` 后缀。

可以请求A记录（IP地址）或者SRV记录（IP+Port）。

```bash
$ dig @10.244.6.25 -p 8600  web.service.consul
...
;; ANSWER SECTION:
web.service.consul.	0	IN	A	127.0.0.1
$ dig @10.244.6.25 -p 8600  web.service.consul SRV
...
;; ANSWER SECTION:
web.service.consul.	0	IN	SRV	1 1 80 consul-6c464f5d4b-gv52l.node.dc1.consul.

;; ADDITIONAL SECTION:
consul-6c464f5d4b-gv52l.node.dc1.consul. 0 IN A	127.0.0.1
consul-6c464f5d4b-gv52l.node.dc1.consul. 0 IN TXT "consul-network-segment="
```

## API方式

也可以通过API方式请求

```bash
$ curl http://127.0.0.1:8500/v1/catalog/services
{
    "consul": [],
    "web": [
        "rails"
    ]
}
$ curl http://127.0.0.1:8500/v1/catalog/service/web
[
    {
        "ID": "0b617f2a-58d6-3779-268a-8583f1d56e1f",
        "Node": "consul-6c464f5d4b-gv52l",
        "Address": "127.0.0.1",
        "Datacenter": "dc1",
...
$ curl 127.0.0.1:8500/v1/health/service/web
[
    {
        "Node": {
            "ID": "291dec46-80c2-cfe4-e13e-10b4521e9ccc",
            "Node": "consul-655bc59456-nj5l8",
            "Address": "127.0.0.1",
            "Datacenter": "dc1",
...
        "Checks": [
            {
                "Node": "consul-655bc59456-nj5l8",
                "CheckID": "serfHealth",
                "Name": "Serf Health Status",
                "Status": "passing",
...
            }
        ]
```


## 健康检测


consul可以为service配置健康监测。注意需要开启 `-enable-script-checks` 。

如下，每10秒检测服务是否正常。如果不正常，则DNS解析不会返回IP地址。

```json
{
  "service": {
    "name": "web",
    "tags": [
      "rails"
    ],
    "port": 80,
    "check": {
      "args": [
        "curl",
        "localhost"
      ],
      "interval": "10s"
    }
  }
}
```

通过API检测状态已经是cirtical。

```bash
$ curl 127.0.0.1:8500/v1/health/service/web
[
    {
        "Node": {
            "ID": "291dec46-80c2-cfe4-e13e-10b4521e9ccc",
            "Node": "consul-655bc59456-nj5l8",
            "Address": "127.0.0.1",
            "Datacenter": "dc1",
            {
                "Node": "consul-655bc59456-nj5l8",
                "CheckID": "service:web",
                "Name": "Service 'web' check",
                "Status": "critical",
...
$ curl http://10.244.6.25:8500/v1/health/service/web?passing
[]
```

