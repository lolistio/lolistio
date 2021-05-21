> 本文基于istio 1.10版本。

# 从虚拟机访问服务网格上的应用

在前文[access workload on virtual machine by service entry](service-entry.md)中，说明了如何通过`service entry/workload entry/dns proxy`来从服务网格内访问在虚拟机上的服务。那么如何在虚拟机上，访问服务网格上的服务呢？

istio提供了一个方案：在虚拟机上部署sidecar（主要是pilot-agent和envoy），并且修改虚拟机的iptables，从而达到与Kubernetes上类似的架构，这样就可以在虚拟机上劫持应用的流量，交给envoy来进行流量治理。

# 部署步骤记录

下面简要记录下部署步骤，详细参考[here](https://istio.io/latest/docs/setup/install/virtual-machine/#create-files-to-transfer-to-the-virtual-machine)。


1、 在虚拟机上export如下环境变量。

```bash
VM_APP="<the name of the application this VM will run>"
VM_NAMESPACE="<the name of your service namespace>"
WORK_DIR="<a certificate working directory>"
SERVICE_ACCOUNT="<name of the Kubernetes service account you want to use for your VM>"
CLUSTER_NETWORK=""
VM_NETWORK=""
CLUSTER="Kubernetes"
```

这里仍然选择使用namespace `istio-demo`，service account名为`vm`。注意CLUSTER_NETWORK和VM_NETWORK留空即可。如下是一个示例。

```bash
export VM_APP="nginx-vm"
export VM_NAMESPACE="istio-demo"
export WORK_DIR="/home/bottle/istio/vm"
export SERVICE_ACCOUNT="vm"
export CLUSTER_NETWORK=""
export VM_NETWORK=""
export CLUSTER="Kubernetes"
```

2、创建namespace `istio-demo`，在该namespace下创建sa `vm`。

3、创建文件 `workloadgroup.yaml` ，并在mesh机器上，生成虚拟机上安装sidecar的相关文件。

```yaml
$ cat <<EOF > workloadgroup.yaml
apiVersion: networking.istio.io/v1alpha3
kind: WorkloadGroup
metadata:
  name: "${VM_APP}"
  namespace: "${VM_NAMESPACE}"
spec:
  metadata:
    labels:
      app: "${VM_APP}"
  template:
    serviceAccount: "${SERVICE_ACCOUNT}"
    network: "${VM_NETWORK}"
EOF
$ kubectl get svc -n istio-system istiod
NAME     TYPE           CLUSTER-IP     EXTERNAL-IP    PORT(S)                                                         AGE
istiod   LoadBalancer   10.98.96.243   192.168.0.59   15010:31196/TCP,15012:30204/TCP,443:32366/TCP,15014:32192/TCP   2d3h
$ 
$ istioctl x workload entry configure -f workloadgroup.yaml -o "${WORK_DIR}" --clusterID "${CLUSTER}" --ingressIP 192.168.0.59
```

生成的文件可以参考 vm-with-sidecar 目录下的文件。其中hosts指定了 `istiod.istio-system.svc` 的地址，而在服务网格上的sidecar，也是通过这个域名访问istiod的，只是其域名解析是通过kube-dns/coredns完成的。



> 相对官方文档，这里略过了 servie istiod 的集群外访问方式配置。官方文档说明是用gateway来进行集群外访问，由于我的集群的load balancer类型的svc，是可以从集群外直接访问external IP的，所以在上面通过`--ingressIP`直接指定了istiod的IP地址。

4、虚拟机上配置mesh相关文件

需要将步骤3生成的文件，拷贝到虚拟机上。

虚拟机上的操作主要参考 [configure-the-virtual-machine](https://istio.io/latest/docs/setup/install/virtual-machine/#configure-the-virtual-machine)，记录如下。

```bash
$ sudo mkdir -p /etc/certs
$ sudo cp root-cert.pem  /etc/certs/root-cert.pem
$ sudo  mkdir -p /var/run/secrets/tokens
$ sudo cp istio-token /var/run/secrets/tokens/istio-token
$ curl -LO https://storage.googleapis.com/istio-release/releases/1.10.0/deb/istio-sidecar.deb
$ sudo dpkg -i istio-sidecar.deb
$ sudo cp cluster.env  /var/lib/istio/envoy/cluster.env
$ sudo cp mesh.yaml  /etc/istio/config/mesh
$ sudo sh -c 'cat hosts  >> /etc/hosts'
$ sudo chown -R istio-proxy /var/lib/istio /etc/certs /etc/istio/proxy /etc/istio/config /var/run/secrets /etc/certs/root-cert.pem
$ sudo systemctl start istio
```

虚拟机上的istio启动后，查看进程可以看到pilot-agent和envoy。

```
istio-p+   54820  0.0  0.1  18548  9664 ?        Ss   16:00   0:00 /lib/systemd/systemd --user
istio-p+   54821  0.0  0.0 172064  4736 ?        S    16:00   0:00 (sd-pam)
istio-p+   54826  0.0  0.5 743480 41840 ?        Ssl  16:00   0:01 /usr/local/bin/pilot-agent proxy
istio-p+   54832  0.3  0.7 181672 64560 ?        Sl   16:00   0:04 /usr/local/bin/envoy -c etc/istio/proxy/envoy-rev0.json --restart-epoch 0 --drain-time-s 45 --drain-strategy immediate --parent-shutdown-tim
```

查看日志文件 `/var/log/istio/istio.log`，可以看到pilot-agent的日志 `connected to upstream XDS server`。


```
2021-05-21T16:00:38.243149Z     info    sds     SDS: PUSH       resource=ROOTCA
2021-05-21T16:00:38.243204Z     info    sds     SDS: PUSH       resource=default
2021-05-21T16:00:38.243256Z     info    cache   returned workload trust anchor from cache       ttl=23h59m59.756746488s
2021-05-21T16:00:38.243775Z     info    sds     SDS: PUSH       resource=ROOTCA
2021-05-21T16:31:55.336107Z     info    xdsproxy        connected to upstream XDS server: istiod.istio-system.svc:15012
```

5、验证

这里借用前文[access workload on virtual machine by service entry](service-entry.md)，其部署了host为`xxx.jd.com`的service entry，并且通过virtaul service将流量全部导给了Kubernetes上的nginx容器（未开启服务网格）。从服务网格上的普通容器，可以访问上述service entry： `curl xxx.jd.com`，返回的是普通nginx容器的内容。

而从虚拟机上，经过上述配置后，访问 `xxx.jd.com`，也会返回相同的内容，验证了虚拟机上也具备了相同的流量治理能力。

当然也可以参照官网，单独部署一个helloworld服务来进行验证。

# 总结

istio给出的虚拟机解决方案，目前看是比较完整的：从虚拟机访问网格服务，从网格访问虚拟机服务，都能够支持。


# 疑问

1. 是否只能通过 workload entry 来显式的指明虚拟机上的服务呢？
2. 文档中要求配置的`VM_APP`是用于做什么的？
