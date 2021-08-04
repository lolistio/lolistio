
istio在应用时，会遇到的2个典型质疑是：

- istio增加了单独的数据平面，从传输的角度来说增加了2跳，势必会带来latency的增加。那么latency的增加到底是多少呢？
- proxy容器需要实现数据面的报文劫持、转发，以及一些策略的实施，其需要的cpu、内存，是多少呢？

要回答这些问题，就要对istio的数据面进行量化。
istio社区提供了一个工具来进行具体的测试：[istio tools perf benchmark](https://github.com/istio/tools/tree/master/perf/benchmark)

为什么不需要对控制面进行量化呢？主要原因是数据面是O(n)的空间复杂度，而控制面几乎是O(1)的空间复杂度。另外一个原因是目前istio的遥测、限流等策略，均为envoy实现，不再交由mixer组件处理，因此集群规模提升后对控制面来说变化不大。

# tools install 
安装参考 [readme](https://github.com/istio/tools/tree/master/perf/benchmark)即可。

注意：
1. 资源需求比较多，要求至少32 cpu、4个node。建议使用完善的生产集群，确保硬件资源充裕。
2. 测试套依赖python3/pip3/pipenv，所以最好是在有网环境进行安装；离线安装会遇到一些镜像源获取不到的问题。
3. 不要在docker环境中安装，测试套运行过程中会使用docker执行一些命令，如果在docker中安装会执行失败。

执行如下命令后，将在namespace twopods-istio中安装2个Deployment：fortioclient/fortioserver。

## fortioclient

fortioclient中包括4个container和1个init container：

- captured：运行nighthawk客户端，客户端发出的报文会被proxy劫持
- uncaptured：运行fortio客户端，客户端发出的报文不会被proxy劫持；监听9076端口，用于托管测试生成的json文件
- shell：用于执行curl、jq等命令行
- istio-proxy：proxy
- init container：

注意下面Deployment 中设置的 excludeInboundPorts/excludeOutboundPorts的端口范围，8076/8077/8078/8081/端口是不走proxy的，这里代表测试的是原生（非mesh）的性能，用于作为baseline对比。

Deployment的编排文件请见 [fortioclient.yaml](fortioclient.yaml)、[fortioserver.yaml](fortioserver.yaml)。

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fortioclient
  namespace: twopods-istio
..
  template:
    metadata:
      annotations:
        linkerd.io/inject: disabled
        sidecar.istio.io/inject: "true"
        sidecar.istio.io/interceptionMode: REDIRECT
        traffic.sidecar.istio.io/excludeInboundPorts: 8076,8077,8078,8081,9999
        traffic.sidecar.istio.io/excludeOutboundPorts: 80,8076,8077,8078,8081
        traffic.sidecar.istio.io/includeOutboundIPRanges: 10.222.0.0/16
```

## fortioserver




















# Ref

- [istio tools perf benchmark](https://github.com/istio/tools/tree/master/perf/benchmark)
- [Best Practices: Benchmarking Service Mesh Performance](https://istio.io/latest/blog/2019/performance-best-practices/)

