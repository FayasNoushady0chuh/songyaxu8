## CookIM - 一个基于akka的分布式聊天程序

![CookIM logo](docs/cookim.png)

- [中文文档](README_CN.md)
- [English](README.md)

PC

![screen snapshot](docs/screen.png) 

手机

![screen snapshot](docs/screen2.png)

- [演示地址](https://im.cookeem.com)

### 目录
1. [功能](#功能)
1. [安装前准备](#安装前准备)
1. [安装](#安装)
1. [使用](#使用)
1. [架构](#架构)

### 功能


### 安装前准备
Java 8+

Scala 2.11+

SBT 0.13+

MongoDB 3+ (3.4.0测试有问题，建议使用3.2.9)

### 安装
- [3.1] 下载源代码
```sh
git clone https://github.com/cookeem/CookIM.git

cd CookIM
```

- [3.2] 打开一个终端，运行如下命令，启动CookIM服务。

```sh
sbt "run-main com.cookeem.chat.CookIM -h 8080 -n 2551"
```
-h 8080 表示HTTP服务监听8080端口

-n 2551 表示akka集群的seed node监听2551端口，默认seed node为localhost:2551

- [3.3] 打开浏览器，访问以下网址：

http://localhost:8080

- [3.4] 打开另外一个终端，运行如下命令，启动另外一个CookIM服务。
```sh
sbt "run-main com.cookeem.chat.CookIM -h 8081 -n 2552"
```

- [3.5] 打开另外一个不同类型的浏览器，访问以下网址：

http://localhost:8081

该演示启动了两个CookIM服务，访问地址分别为8080端口以及8081端口，用户通过两个浏览器分别访问不同的的CookIM服务，用户在浏览器中通过websocket发送消息到akka集群，akka集群通过分布式的消息订阅与发布，把消息推送到集群中相应的节点，实现分布式通讯。
