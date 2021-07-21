
> 针对istio 1.10版本验证。

本文将使用solo开发的[wasme](https://github.com/solo-io/wasm)初始化一个assemblyscript类型的项目，并执行编译，得到wasm二进制文件。然后创建WASM类型的envoy filter，将wasm文件以configmap的形式，自动挂载到istio-proxy容器中，



项目的功能为http response自动添加header：hello=world。






Ref:

- [jotak@discuss.isito.io](https://discuss.istio.io/t/istio-1-7-fails-to-create-a-wasm-filter/8208/10?u=silenceshell)，与1.10适配
- [How to write WASM filters for Envoy and deploy it with Istio](https://banzaicloud.com/blog/envoy-wasm-filter/), banzaicloud的文章，注意文中envoyfilter已经与1.10不匹配了。
- [solo-io/proxy-runtime](https://github.com/solo-io/proxy-runtime), examples/addheader 与1.10不适配
- [Extensibility: WebAssembly](https://istio.io/latest/docs/concepts/wasm/)
- [Webassembly Hub](https://docs.solo.io/web-assembly-hub/latest/tutorial_code/)
