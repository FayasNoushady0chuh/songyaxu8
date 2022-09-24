## CookIM - Distributed web chat application built on akka

![CookIM logo](docs/cookim.png)

- [中文文档](README_CN.md)
- [English document](README.md)

Show on PC

![screen snapshot](docs/screen.png) 

Show on mobile

![screen snapshot](docs/screen2.png)

- [demo](https://im.cookeem.com)

### Table of contents
1. [introduction](#introduction)
1. [requirement](#requirement)
1. [install](#installation)
1. [usage](#usage)
1. [framework](#framework)

### Introduction


### Requirement
Java 8+

Scala 2.11+

SBT 0.13+

MongoDB 3+ ( there's problem test in 3.4.0, recommend to use 3.2.9)

### Installation
- [3.1] download source
```sh
git clone https://github.com/cookeem/CookIM.git

cd CookIM
```

- [3.2] open a terminal, and run the command below to start up CookIM backend service:

```sh
sbt "run-main com.cookeem.chat.CookIM -w 8080 -a 2551"
```
-h 8080 is the port of HTTP service

-a 2551 is akka cluster seed node port, by default seed node is localhost:2551

- [3.3] open the browser, and access the url below:

http://localhost:8080

- [3.4] open the other terminal, and run the command below to start up another CookIM backend service:
```sh
sbt "run-main com.cookeem.chat.CookIM -w 8081 -a 2552"
```

- [3.5] because CookIM applicaton use cookie, please open the other kind of browser, and access the url below:

http://localhost:8081
