
istio在应用时，会遇到的一个典型质疑是：istio增加了单独的数据平面，从传输的角度来说增加了2跳，势必会带来latency的增加。那么latency的增加到底是多少呢？

istio社区提供了一个工具来进行具体的测试：[istio tools perf benchmark](https://github.com/istio/tools/tree/master/perf/benchmark)

# install 
安装参考 [readme](https://github.com/istio/tools/tree/master/perf/benchmark)即可。

注意：
1. 资源需求比较多，要求至少32 cpu、4个node
2. 依赖python3/pip/pipenv，所以最好是在本地环境进行安装；离线安装会遇到一些镜像源获取不到的问题。
3. 不要在docker环境中安装，运行过程中会使用docker执行一些命令，如果在docker中安装会执行失败

执行如下命令后，将在namespace twopods-istio中安装2个Deployment：

```bash
export NAMESPACE=twopods-istio
export INTERCEPTION_MODE=REDIRECT
export ISTIO_INJECT=true
export LOAD_GEN_TYPE=nighthawk
export DNS_DOMAIN=v104.qualistio.org
./setup_test.sh
```


















# Ref

- [istio tools perf benchmark](https://github.com/istio/tools/tree/master/perf/benchmark)
- [Best Practices: Benchmarking Service Mesh Performance](https://istio.io/latest/blog/2019/performance-best-practices/)

